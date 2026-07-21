/*
 * Copyright (c) 2014-2024 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 */
package ca.vanzyl.provisio.archive;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.TreeMap;
import org.apache.commons.io.IOUtils;
import org.codehaus.plexus.util.SelectorUtils;

/** Mutable state and temporary resources for exactly one archive operation. */
final class ArchiveSession implements Closeable {

    private final ArchiveFormat format;
    private final ArchiveOptions options;
    private final ArchiveWriter writer;
    private final ContentSpool contentSpool;
    private final Selector hardLinkSelector;
    private final Map<String, OutputEntry> hardLinkTargets = new HashMap<>();
    private final Map<String, Boolean> paths = new HashMap<>();
    private final Map<String, OutputEntry> entries = new TreeMap<>();
    private boolean finished;
    private boolean closed;

    ArchiveSession(Path output, ArchiveFormat format, ArchiveOptions options) throws IOException {
        this.format = format;
        this.options = options;
        contentSpool = new ContentSpool(output.getParent());
        writer = format.openWriter(output, options.posixLongFileMode());
        if (!options.hardLinkIncludes().isEmpty() || !options.hardLinkExcludes().isEmpty()) {
            hardLinkSelector = new Selector(options.hardLinkIncludes(), options.hardLinkExcludes());
        } else {
            hardLinkSelector = new Selector(null, Collections.singletonList("**/**"));
        }
    }

    void add(SourceSpec sourceSpec) throws IOException {
        requireActive();
        try (Source source = sourceSpec.source()) {
            source.forEachEntry(entry -> addSourceEntry(sourceSpec, entry));
        }
    }

    void finish() throws IOException {
        requireActive();
        for (OutputEntry entry : entries.values()) {
            writer.write(entry);
        }
        finished = true;
    }

    private void addSourceEntry(SourceSpec sourceSpec, SourceEntry entry) throws IOException {
        ArchivePath sourcePath = ArchivePath.parse(entry.getName(), "source entry path");
        if (!sourceSpec.includes(sourcePath.entryName(entry.getType()))) {
            return;
        }
        if (sourceSpec.source().isDirectory() && sourceSpec.flatten() && entry.isDirectory()) {
            return;
        }
        ArchivePath outputPath = mapPath(sourceSpec, sourcePath);
        String entryName = outputPath.entryName(entry.getType());
        String linkTarget = mapLinkTarget(sourceSpec, outputPath, entry);
        boolean executable = isExecutable(entry.getName());

        for (String directoryName : getParentDirectoryNames(entryName)) {
            String directoryPath = directoryName.substring(0, directoryName.length() - 1);
            if (!paths.containsKey(directoryPath)) {
                paths.put(directoryPath, Boolean.FALSE);
                OutputEntry directoryEntry =
                        createOutputEntry(directoryName, SourceEntry.directory(directoryName, -1, 0), false, null);
                addEntry(directoryName, directoryEntry);
            }
        }

        String path = outputPath.value();
        if (!paths.containsKey(path)) {
            paths.put(path, Boolean.TRUE);
            SourceEntry stableEntry = options.normalize() ? contentSpool.stabilize(entry) : entry;
            OutputEntry archiveEntry = createOutputEntry(entryName, stableEntry, executable, linkTarget);
            addEntry(entryName, archiveEntry);
        } else if (Boolean.TRUE.equals(paths.get(path)) || !entry.isDirectory()) {
            throw new IllegalArgumentException("Duplicate archive entry " + entryName);
        } else {
            paths.put(path, Boolean.TRUE);
        }
    }

    private boolean isExecutable(String name) {
        for (String executable : options.executables()) {
            if (SelectorUtils.match(executable, name)) {
                return true;
            }
        }
        return false;
    }

    private ArchivePath mapPath(SourceSpec sourceSpec, ArchivePath sourcePath) throws IOException {
        ArchivePath mapped = sourcePath;
        if (sourceSpec.source().isDirectory()) {
            if (!sourceSpec.useRoot()) {
                mapped = mapped.withoutFirstSegment();
            }
            if (sourceSpec.flatten()) {
                mapped = mapped.fileName();
            }
        }
        return mapped.prepend(sourceSpec.destinationPrefix(), "source destination prefix");
    }

    private String mapLinkTarget(SourceSpec sourceSpec, ArchivePath outputPath, SourceEntry entry) throws IOException {
        if (entry.isSymbolicLink()) {
            return ArchivePath.validateSymbolicLinkTarget(outputPath, entry.getLinkTarget());
        }
        if (entry.isHardLink()) {
            ArchivePath sourceTarget = ArchivePath.parse(entry.getLinkTarget(), "hard link target");
            return mapPath(sourceSpec, sourceTarget).value();
        }
        return null;
    }

    private Iterable<String> getParentDirectoryNames(String entryName) {
        List<String> directoryNames = new ArrayList<>();
        StringTokenizer tokenizer = new StringTokenizer(entryName, "/");
        if (tokenizer.hasMoreTokens()) {
            StringBuilder directoryName = new StringBuilder(tokenizer.nextToken());
            while (tokenizer.hasMoreTokens()) {
                directoryName.append('/');
                directoryNames.add(directoryName.toString());
                directoryName.append(tokenizer.nextToken());
            }
        }
        return directoryNames;
    }

    private OutputEntry createOutputEntry(String name, SourceEntry source, boolean executable, String linkTarget) {
        int mode = source.getFileMode();
        if (mode != -1 && executable) {
            mode = ca.vanzyl.provisio.archive.perms.FileMode.makeExecutable(mode);
        } else if (mode == -1 && executable) {
            mode = ca.vanzyl.provisio.archive.perms.FileMode.EXECUTABLE_FILE.getBits();
        }

        long time = options.normalize() ? normalizedTimestamp(name) : -1;
        if (format == ArchiveFormat.TAR_GZ && source.getType() == EntryType.FILE && hardLinkSelector.include(name)) {
            String sourceFileName = source.getName().substring(source.getName().lastIndexOf('/') + 1);
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

    private long normalizedTimestamp(String name) {
        if (name.endsWith(".class")) {
            return Archiver.DOS_EPOCH_IN_JAVA_TIME + Archiver.MINIMUM_TIMESTAMP_INCREMENT;
        }
        return Archiver.DOS_EPOCH_IN_JAVA_TIME;
    }

    private void addEntry(String entryName, OutputEntry entry) throws IOException {
        if (options.normalize()) {
            entries.put(entryName, entry);
        } else {
            writer.write(entry);
        }
    }

    private void requireActive() {
        if (closed) {
            throw new IllegalStateException("Archive session is closed");
        }
        if (finished) {
            throw new IllegalStateException("Archive session is finished");
        }
    }

    @Override
    public void close() throws IOException {
        if (closed) {
            return;
        }
        closed = true;
        IOException failure = null;
        try {
            writer.close();
        } catch (IOException e) {
            failure = e;
        }
        try {
            contentSpool.close();
        } catch (IOException e) {
            if (failure == null) {
                failure = e;
            } else {
                failure.addSuppressed(e);
            }
        }
        if (failure != null) {
            throw failure;
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
}
