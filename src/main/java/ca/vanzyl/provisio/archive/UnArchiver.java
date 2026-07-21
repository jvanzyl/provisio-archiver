/*
 * Copyright (c) 2014-2024 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 */
package ca.vanzyl.provisio.archive;

import static java.util.Objects.requireNonNull;

import ca.vanzyl.provisio.archive.perms.FileModes;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.codehaus.plexus.util.io.CachingOutputStream;

public final class UnArchiver {

    private final Selector selector;
    private final boolean useRoot;
    private final boolean flatten;
    private final boolean dereferenceHardlinks;
    private final UnArchiverBuilder builder;

    private UnArchiver(UnArchiverBuilder builder) {
        this.builder = builder;
        this.useRoot = builder.useRoot;
        this.flatten = builder.flatten;
        this.dereferenceHardlinks = builder.dereferenceHardlinks;
        this.selector = new Selector(builder.includes, builder.excludes);
    }

    public void unarchive(Path archive, Path outputDirectory) throws IOException {
        requireNonNull(archive);
        requireNonNull(outputDirectory);
        unarchive(archive, outputDirectory, new NoopEntryProcessor());
    }

    public void unarchive(Path archive, Path outputDirectory, UnarchivingEntryProcessor entryProcessor)
            throws IOException {
        requireNonNull(archive);
        requireNonNull(outputDirectory);
        requireNonNull(entryProcessor);

        Path inputArchive = archive.toAbsolutePath();
        Path destinationDirectory = outputDirectory.normalize().toAbsolutePath();
        //
        // These are the contributions that unpacking this archive is providing
        //
        Files.createDirectories(destinationDirectory);
        try (Source source = ArchiveFormat.detect(inputArchive).openSource(inputArchive)) {
            Set<String> outputPaths = new HashSet<>();
            source.forEachEntry(archiveEntry ->
                    unarchiveEntry(inputArchive, destinationDirectory, entryProcessor, archiveEntry, outputPaths));
        }
    }

    private void unarchiveEntry(
            Path archive,
            Path outputDirectory,
            UnarchivingEntryProcessor entryProcessor,
            SourceEntry archiveEntry,
            Set<String> outputPaths)
            throws IOException {
        ArchivePath entryPath = adjustPath(true, archiveEntry.getName(), archiveEntry.getType(), entryProcessor);
        String entryName = entryPath.entryName(archiveEntry.getType());

        if (!selector.include(entryName)) {
            return;
        }
        if (!outputPaths.add(entryPath.value())) {
            throw new IOException("Duplicate archive output path " + entryPath.value() + " in " + archive);
        }
        Path outputFile = outputDirectory.resolve(entryPath.value()).normalize().toAbsolutePath();
        if (!outputFile.startsWith(outputDirectory)) {
            throw new IOException("Archive escape attempt detected in " + archive);
        }

        if (archiveEntry.isDirectory()) {
            Files.createDirectories(outputFile);
            entryProcessor.processed(entryName, outputFile);
            return;
        }

        //
        // If we take an archive and flatten it into the output directory the first entry will
        // match the output directory which exists so it will cause an error trying to make it
        //
        if (outputFile.equals(outputDirectory)) {
            entryProcessor.processed(entryName, outputFile);
            return;
        }
        if (!Files.isDirectory(outputFile.getParent())) {
            Files.createDirectories(outputFile.getParent());
        }

        if (archiveEntry.isHardLink()) {
            ArchivePath hardLinkPath = adjustPath(false, archiveEntry.getLinkTarget(), EntryType.FILE, entryProcessor);
            Path hardLinkSource =
                    outputDirectory.resolve(hardLinkPath.value()).normalize().toAbsolutePath();
            if (!hardLinkSource.startsWith(outputDirectory)) {
                throw new IOException("Archive hardlink escape attempt detected in " + archive);
            }
            if (dereferenceHardlinks) {
                Files.copy(hardLinkSource, outputFile, StandardCopyOption.REPLACE_EXISTING);
            } else {
                // Remove any existing file or link as Files.createLink has no option to overwrite
                Files.deleteIfExists(outputFile);
                Files.createLink(outputFile, hardLinkSource);
            }
            setFilePermission(archiveEntry, outputFile);
            entryProcessor.processed(entryName, outputFile);
        } else if (archiveEntry.isSymbolicLink()) {
            String target = ArchivePath.validateSymbolicLinkTarget(entryPath, archiveEntry.getLinkTarget());

            Files.createDirectories(outputFile.getParent());
            Files.deleteIfExists(outputFile);
            Files.createSymbolicLink(outputFile, Paths.get(target));
            entryProcessor.processed(entryName, outputFile);
        } else {
            try (InputStream inputStream = archiveEntry.getContent().open();
                    CachingOutputStream outputStream = new CachingOutputStream(outputFile)) {
                entryProcessor.processStream(archiveEntry.getName(), inputStream, outputStream);
                outputStream.close();
                if (outputStream.isModified()) {
                    setFilePermission(archiveEntry, outputFile);
                }
            } finally {
                entryProcessor.processed(entryName, outputFile);
            }
        }
    }

    private ArchivePath adjustPath(
            boolean target, String entryName, EntryType entryType, UnarchivingEntryProcessor entryProcessor)
            throws IOException {
        ArchivePath path = ArchivePath.parse(entryName, target ? "archive entry path" : "hard link target");
        if (!useRoot) {
            path = path.withoutFirstSegment();
        }
        // Process the entry name before any output is created on disk
        String processed;
        if (target) {
            processed = entryProcessor.targetName(path.entryName(entryType));
        } else {
            processed = entryProcessor.sourceName(path.value());
        }
        path = ArchivePath.parse(processed, target ? "processed archive entry path" : "processed hard link target");
        // So with an entry we may want to take a set of entry in a set of directories and flatten them
        // into one directory, or we may want to preserve the directory structure.
        if (flatten) {
            path = path.fileName();
        }
        return path;
    }

    private void setFilePermission(SourceEntry archiveEntry, Path outputFile) throws IOException {
        int mode = archiveEntry.getFileMode();
        //
        // Currently, zip entries produced by plexus-archiver return 0 for the unix mode, so I'm doing something wrong,
        // or
        // it's not being stored directly. So in the case of unpacking an zip archive we don't want to produce files
        // that are unreadable or unusable, so we'll give files 0644 and directories 0755
        //
        if (mode > 0) {
            setFilePermissions(outputFile, FileModes.toPermissions(mode));
        } else {
            if (archiveEntry.isDirectory()) {
                setFilePermissions(outputFile, FileModes.toPermissions(0755));
            } else {
                setFilePermissions(outputFile, FileModes.toPermissions(0644));
            }
        }
    }

    private void setFilePermissions(Path file, Set<PosixFilePermission> perms) throws IOException {
        try {
            Files.setPosixFilePermissions(file, perms);
        } catch (UnsupportedOperationException e) {
            // ignore, must be windows
        }
    }

    //
    // Archiver archiver = Archiver.builder()
    // .includes("**/*.java")
    // .includes(Iterable<String>)
    // .excludes("**/*.properties")
    // .excludes(Iterable<String>)
    // .flatten(true)
    // .useRoot(false)
    // .build();

    public static UnArchiverBuilder builder() {
        return new UnArchiverBuilder();
    }

    /**
     * {@link UnarchivingEntryProcessor} that leaves the entry name and content as-is.
     */
    static class NoopEntryProcessor implements UnarchivingEntryProcessor {}

    public static final class UnArchiverBuilder {

        List<String> includes = new ArrayList<>();
        List<String> excludes = new ArrayList<>();
        boolean useRoot = true;
        boolean flatten = false;
        boolean dereferenceHardlinks = false;

        public UnArchiverBuilder includes(String... includes) {
            List<String> i = new ArrayList<>();
            for (String include : includes) {
                if (include != null) {
                    i.add(include);
                }
            }
            return includes(Collections.unmodifiableList(i));
        }

        public UnArchiverBuilder includes(Iterable<String> includes) {
            includes.forEach(this.includes::add);
            return this;
        }

        public UnArchiverBuilder excludes(String... excludes) {
            List<String> i = new ArrayList<>();
            for (String exclude : excludes) {
                if (exclude != null) {
                    i.add(exclude);
                }
            }
            return excludes(Collections.unmodifiableList(i));
        }

        public UnArchiverBuilder excludes(Iterable<String> excludes) {
            excludes.forEach(this.excludes::add);
            return this;
        }

        public UnArchiverBuilder useRoot(boolean useRoot) {
            this.useRoot = useRoot;
            return this;
        }

        public UnArchiverBuilder flatten(boolean flatten) {
            this.flatten = flatten;
            return this;
        }

        public UnArchiverBuilder dereferenceHardlinks(boolean dereferenceHardlinks) {
            this.dereferenceHardlinks = dereferenceHardlinks;
            return this;
        }

        public UnArchiver build() {
            return new UnArchiver(this);
        }
    }
}
