/*
 * Copyright (c) 2014-2024 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 */
package ca.vanzyl.provisio.archive;

import ca.vanzyl.provisio.archive.source.DirectorySource;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.TreeMap;
import org.apache.commons.io.IOUtils;
import org.codehaus.plexus.util.SelectorUtils;

public class Archiver {

    public static final long DOS_EPOCH_IN_JAVA_TIME = 315561600000L;
    // ZIP timestamps have a resolution of 2 seconds.
    // see http://www.info-zip.org/FAQ.html#limits
    public static final long MINIMUM_TIMESTAMP_INCREMENT = 2000L;
    private final List<String> executables;
    private final boolean useRoot;
    private final boolean flatten;
    private final boolean normalize;
    private final String prefix;
    private final Selector selector;
    private final ArchiverBuilder builder;

    private Archiver(ArchiverBuilder builder) {
        this.builder = builder;
        this.executables = builder.executables;
        this.useRoot = builder.useRoot;
        this.flatten = builder.flatten;
        this.normalize = builder.normalize;
        this.prefix = builder.prefix;
        this.selector = new Selector(builder.includes, builder.excludes);
    }

    public void archive(File archive, List<String> sourceDirectories) throws IOException {
        File[] fileSourceDirectories = new File[sourceDirectories.size()];
        for (int i = 0; i < sourceDirectories.size(); i++) {
            fileSourceDirectories[i] = new File(sourceDirectories.get(i));
        }
        archive(archive, fileSourceDirectories);
    }

    public void archive(File archive, File... sourceDirectories) throws IOException {
        archive(archive, new DirectorySource(sourceDirectories));
    }

    public void archive(File archive, Source... sources) throws IOException {
        Path output = archive.toPath().toAbsolutePath();
        Files.createDirectories(output.getParent());
        Path temporary = Files.createTempFile(
                output.getParent(), ".provisio-" + output.getFileName().toString() + "-", ".tmp");
        boolean completed = false;
        try {
            writeArchive(archive, temporary.toFile(), sources);
            moveIntoPlace(temporary, output);
            completed = true;
        } finally {
            if (!completed) {
                Files.deleteIfExists(temporary);
            }
        }
    }

    private void writeArchive(File formatSource, File output, Source... sources) throws IOException {
        ArchiveFormat format = ArchiveFormat.detect(formatSource.toPath());
        OutputEntryFactory outputEntryFactory = new OutputEntryFactory(format, builder);
        Map<String, OutputEntry> entries = new TreeMap<>();

        try (ContentSpool contentSpool = new ContentSpool(output.toPath().getParent());
                ArchiveWriter writer = format.openWriter(output.toPath(), builder)) {
            //
            // collected archive entry paths mapped to true for explicitly provided entries
            // and to false for implicitly created directory entries duplicate explicitly
            // provided entries result in IllegalArgumentException
            //
            Map<String, Boolean> paths = new HashMap<>();
            for (Source source : sources) {
                try (Source closeableSource = source) {
                    closeableSource.forEachEntry(entry -> addSourceEntry(
                            closeableSource, entry, outputEntryFactory, paths, entries, writer, contentSpool));
                }
            }

            if (!entries.isEmpty()) {
                for (Map.Entry<String, OutputEntry> entry : entries.entrySet()) {
                    writer.write(entry.getValue());
                }
            }
        }
    }

    private void moveIntoPlace(Path temporary, Path output) throws IOException {
        try {
            Files.move(temporary, output, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temporary, output, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void addSourceEntry(
            Source source,
            SourceEntry entry,
            OutputEntryFactory outputEntryFactory,
            Map<String, Boolean> paths,
            Map<String, OutputEntry> entries,
            ArchiveWriter writer,
            ContentSpool contentSpool)
            throws IOException {
        ArchivePath sourcePath = ArchivePath.parse(entry.getName(), "source entry path");
        if (!selector.include(sourcePath.entryName(entry.getType()))) {
            return;
        }
        if (source.isDirectory() && flatten && entry.isDirectory()) {
            return;
        }
        ArchivePath outputPath = mapPath(source, sourcePath);
        String entryName = outputPath.entryName(entry.getType());
        String linkTarget = mapLinkTarget(source, outputPath, entry);
        boolean isExecutable = false;
        for (String executable : executables) {
            if (SelectorUtils.match(executable, entry.getName())) {
                isExecutable = true;
                break;
            }
        }
        // Create any missing intermediate directory entries
        for (String directoryName : getParentDirectoryNames(entryName)) {
            String directoryPath = directoryName.substring(0, directoryName.length() - 1);
            if (!paths.containsKey(directoryPath)) {
                paths.put(directoryPath, Boolean.FALSE);
                OutputEntry directoryEntry = outputEntryFactory.create(
                        directoryName, SourceEntry.directory(directoryName, -1, 0), false, null);
                addEntry(directoryName, directoryEntry, entries, writer);
            }
        }
        String path = outputPath.value();
        if (!paths.containsKey(path)) {
            paths.put(path, Boolean.TRUE);
            SourceEntry stableEntry = normalize ? contentSpool.stabilize(entry) : entry;
            OutputEntry archiveEntry = outputEntryFactory.create(entryName, stableEntry, isExecutable, linkTarget);
            addEntry(entryName, archiveEntry, entries, writer);
        } else if (Boolean.TRUE.equals(paths.get(path)) || !entry.isDirectory()) {
            throw new IllegalArgumentException("Duplicate archive entry " + entryName);
        } else {
            paths.put(path, Boolean.TRUE);
        }
    }

    private ArchivePath mapPath(Source source, ArchivePath sourcePath) throws IOException {
        ArchivePath mapped = sourcePath;
        if (source.isDirectory()) {
            if (!useRoot) {
                mapped = mapped.withoutFirstSegment();
            }
            if (flatten) {
                mapped = mapped.fileName();
            }
        }
        return mapped.prepend(prefix, "archive prefix");
    }

    private String mapLinkTarget(Source source, ArchivePath outputPath, SourceEntry entry) throws IOException {
        if (entry.isSymbolicLink()) {
            return ArchivePath.validateSymbolicLinkTarget(outputPath, entry.getLinkTarget());
        }
        if (entry.isHardLink()) {
            ArchivePath sourceTarget = ArchivePath.parse(entry.getLinkTarget(), "hard link target");
            return mapPath(source, sourceTarget).value();
        }
        return null;
    }

    private Iterable<String> getParentDirectoryNames(String entryName) {
        List<String> directoryNames = new ArrayList<>();
        StringTokenizer st = new StringTokenizer(entryName, "/");
        if (st.hasMoreTokens()) {
            StringBuilder directoryName = new StringBuilder(st.nextToken());
            while (st.hasMoreTokens()) {
                directoryName.append('/');
                directoryNames.add(directoryName.toString());
                directoryName.append(st.nextToken());
            }
        }
        return directoryNames;
    }

    /**
     * Returns the normalized timestamp for a jar entry based on its name. This is necessary since javac will, when loading a class X, prefer a source file to a class file, if both files have the same
     * timestamp. Therefore, we need to adjust the timestamp for class files to slightly after the normalized time.
     *
     * @param name The name of the file for which we should return the normalized timestamp.
     * @return the time for a new Jar file entry in milliseconds since the epoch.
     */
    private long normalizedTimestamp(String name) {
        if (name.endsWith(".class")) {
            return DOS_EPOCH_IN_JAVA_TIME + MINIMUM_TIMESTAMP_INCREMENT;
        } else {
            return DOS_EPOCH_IN_JAVA_TIME;
        }
    }

    private void addEntry(String entryName, OutputEntry entry, Map<String, OutputEntry> entries, ArchiveWriter writer)
            throws IOException {
        if (normalize) {
            entries.put(entryName, entry);
        } else {
            writer.write(entry);
        }
    }

    private final class OutputEntryFactory {

        private final ArchiveFormat format;
        private final Selector hardLinkSelector;
        private final Map<String, OutputEntry> hardLinkTargets = new HashMap<>();

        private OutputEntryFactory(ArchiveFormat format, ArchiverBuilder builder) {
            this.format = format;
            if (!builder.hardLinkIncludes.isEmpty() || !builder.hardLinkExcludes.isEmpty()) {
                hardLinkSelector = new Selector(builder.hardLinkIncludes, builder.hardLinkExcludes);
            } else {
                hardLinkSelector = new Selector(null, Collections.singletonList("**/**"));
            }
        }

        private OutputEntry create(String name, SourceEntry source, boolean executable, String linkTarget) {
            int mode = source.getFileMode();
            if (mode != -1 && executable) {
                mode = ca.vanzyl.provisio.archive.perms.FileMode.makeExecutable(mode);
            } else if (mode == -1 && executable) {
                mode = ca.vanzyl.provisio.archive.perms.FileMode.EXECUTABLE_FILE.getBits();
            }

            long time = normalize ? normalizedTimestamp(name) : -1;
            if (format == ArchiveFormat.TAR_GZ
                    && source.getType() == EntryType.FILE
                    && hardLinkSelector.include(name)) {
                String sourceFileName =
                        source.getName().substring(source.getName().lastIndexOf('/') + 1);
                OutputEntry target = hardLinkTargets.get(sourceFileName);
                if (target != null) {
                    return OutputEntry.hardLink(name, target.getName(), mode, time);
                }
                OutputEntry entry = OutputEntry.from(name, source, mode, time, linkTarget);
                hardLinkTargets.put(sourceFileName, entry);
                return entry;
            }
            return OutputEntry.from(name, source, mode, time, linkTarget);
        }
    }

    private static final class ContentSpool implements Closeable {

        private final Path temporaryDirectory;
        private final List<Path> contentFiles = new ArrayList<>();

        private ContentSpool(Path temporaryDirectory) {
            this.temporaryDirectory = temporaryDirectory;
        }

        private SourceEntry stabilize(SourceEntry entry) throws IOException {
            if (entry.getType() != EntryType.FILE) {
                return entry;
            }

            Path content = Files.createTempFile(temporaryDirectory, ".provisio-entry-", ".tmp");
            boolean completed = false;
            try {
                try (InputStream inputStream = entry.getContent().open();
                        OutputStream outputStream = Files.newOutputStream(content)) {
                    IOUtils.copyLarge(inputStream, outputStream);
                }
                SourceEntry stableEntry = entry.withContent(EntryContents.of(content));
                contentFiles.add(content);
                completed = true;
                return stableEntry;
            } finally {
                if (!completed) {
                    Files.deleteIfExists(content);
                }
            }
        }

        @Override
        public void close() throws IOException {
            IOException failure = null;
            for (Path content : contentFiles) {
                try {
                    Files.deleteIfExists(content);
                } catch (IOException e) {
                    if (failure == null) {
                        failure = e;
                    } else {
                        failure.addSuppressed(e);
                    }
                }
            }
            if (failure != null) {
                throw failure;
            }
        }
    }

    public static ArchiverBuilder builder() {
        return new ArchiverBuilder();
    }

    public static class ArchiverBuilder {

        List<String> includes = new ArrayList<>();
        List<String> excludes = new ArrayList<>();
        List<String> executables = new ArrayList<>();
        boolean useRoot = true;
        boolean flatten = false;
        boolean normalize = false;
        String prefix;
        boolean posixLongFileMode;
        List<String> hardLinkIncludes = new ArrayList<>();
        List<String> hardLinkExcludes = new ArrayList<>();

        public ArchiverBuilder includes(String... includes) {
            return includes(Collections.unmodifiableList(new ArrayList<>(Arrays.asList(includes))));
        }

        public ArchiverBuilder includes(Iterable<String> includes) {
            includes.forEach(this.includes::add);
            return this;
        }

        public ArchiverBuilder excludes(String... excludes) {
            return excludes(Collections.unmodifiableList(new ArrayList<>(Arrays.asList(excludes))));
        }

        public ArchiverBuilder excludes(Iterable<String> excludes) {
            excludes.forEach(this.excludes::add);
            return this;
        }

        public ArchiverBuilder useRoot(boolean useRoot) {
            this.useRoot = useRoot;
            return this;
        }

        /**
         * Enables or disables the Jar entry normalization.
         *
         * @param normalize If true the timestamps of Jar entries will be set to the DOS epoch.
         */
        public ArchiverBuilder normalize(boolean normalize) {
            this.normalize = normalize;
            return this;
        }

        public ArchiverBuilder executable(String... executables) {
            return executable(Collections.unmodifiableList(new ArrayList<>(Arrays.asList(executables))));
        }

        public ArchiverBuilder executable(Iterable<String> executables) {
            executables.forEach(this.executables::add);
            return this;
        }

        public ArchiverBuilder flatten(boolean flatten) {
            this.flatten = flatten;
            return this;
        }

        public ArchiverBuilder withPrefix(String prefix) {
            this.prefix = prefix;
            return this;
        }

        public ArchiverBuilder posixLongFileMode(boolean posixLongFileMode) {
            this.posixLongFileMode = posixLongFileMode;
            return this;
        }

        public ArchiverBuilder hardLinkIncludes(String... hardLinkIncludes) {
            return hardLinkIncludes(Collections.unmodifiableList(new ArrayList<>(Arrays.asList(hardLinkIncludes))));
        }

        public ArchiverBuilder hardLinkIncludes(Iterable<String> hardLinkIncludes) {
            hardLinkIncludes.forEach(this.hardLinkIncludes::add);
            return this;
        }

        public ArchiverBuilder hardLinkExcludes(String... hardLinkExcludes) {
            return hardLinkExcludes(Collections.unmodifiableList(new ArrayList<>(Arrays.asList(hardLinkExcludes))));
        }

        public ArchiverBuilder hardLinkExcludes(Iterable<String> hardLinkExcludes) {
            hardLinkExcludes.forEach(this.hardLinkExcludes::add);
            return this;
        }

        public Archiver build() {
            return new Archiver(this);
        }
    }
}
