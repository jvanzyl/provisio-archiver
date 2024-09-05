/*
 * Copyright (c) 2014-2024 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 */
package ca.vanzyl.provisio.archive;

import static java.util.Objects.requireNonNull;

import ca.vanzyl.provisio.archive.perms.FileMode;
import ca.vanzyl.provisio.archive.perms.PosixModes;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.codehaus.plexus.util.io.CachingOutputStream;

public class UnArchiver {

    private final Selector selector;
    private final boolean useRoot;
    private final boolean flatten;
    private final boolean dereferenceHardlinks;
    private final UnArchiverBuilder builder;

    public UnArchiver(UnArchiverBuilder builder) {
        this.builder = builder;
        this.useRoot = builder.useRoot;
        this.flatten = builder.flatten;
        this.dereferenceHardlinks = builder.dereferenceHardlinks;
        this.selector = new Selector(builder.includes, builder.excludes);
    }

    public void unarchive(File archive, File outputDirectory) throws IOException {
        requireNonNull(archive);
        requireNonNull(outputDirectory);
        unarchive(archive.toPath(), outputDirectory.toPath(), new NoopEntryProcessor());
    }

    @Deprecated
    public void unarchive(File archive, File outputDirectory, UnarchivingEntryProcessor entryProcessor)
            throws IOException {
        requireNonNull(archive);
        requireNonNull(outputDirectory);
        requireNonNull(entryProcessor);
        // adapt legacy to new one
        unarchive(archive.toPath(), outputDirectory.toPath(), new UnarchivingEnhancedEntryProcessor() {
            @Override
            public String targetName(String name) {
                return entryProcessor.processName(name);
            }

            @Override
            public String sourceName(String name) {
                return entryProcessor.processName(name);
            }

            @Override
            public void processStream(String entryName, InputStream inputStream, OutputStream outputStream)
                    throws IOException {
                entryProcessor.processStream(entryName, inputStream, outputStream);
            }
        });
    }

    public void unarchive(Path archive, Path outputDirectory, UnarchivingEnhancedEntryProcessor entryProcessor)
            throws IOException {
        requireNonNull(archive);
        requireNonNull(outputDirectory);
        requireNonNull(entryProcessor);

        archive = archive.toAbsolutePath();
        outputDirectory = outputDirectory.toAbsolutePath();
        //
        // These are the contributions that unpacking this archive is providing
        //
        Files.createDirectories(outputDirectory);
        Source source =
                ArchiverHelper.getArchiveHandler(archive.toFile(), builder).getArchiveSource();
        for (ExtendedArchiveEntry archiveEntry : source.entries()) {
            String entryName = adjustPath(true, archiveEntry.getName(), entryProcessor);

            if (!selector.include(entryName)) {
                continue;
            }
            Path outputFile = outputDirectory.resolve(entryName).toAbsolutePath();
            if (!outputFile.startsWith(outputDirectory)) {
                throw new IOException("Archive escape attempt detected in " + archive);
            }

            if (archiveEntry.isDirectory()) {
                Files.createDirectories(outputFile);
                entryProcessor.processed(entryName, outputFile);
                continue;
            }

            //
            // If we take an archive and flatten it into the output directory the first entry will
            // match the output directory which exists so it will cause an error trying to make it
            //
            if (outputFile.equals(outputDirectory)) {
                entryProcessor.processed(entryName, outputFile);
                continue;
            }
            if (!Files.isDirectory(outputFile.getParent())) {
                Files.createDirectories(outputFile.getParent());
            }

            if (archiveEntry.isHardLink()) {
                Path hardLinkSource = outputDirectory
                        .resolve(adjustPath(false, archiveEntry.getHardLinkPath(), entryProcessor))
                        .toAbsolutePath();
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
                // We expect symlinks to be relative as they are not generally useful in a tarball otherwise
                Path link = outputDirectory.resolve(entryName);
                Path target = outputDirectory.relativize(outputDirectory.resolve(archiveEntry.getSymbolicLinkPath()));
                Files.createDirectories(link.getParent());
                Files.createSymbolicLink(link, target);
                entryProcessor.processed(entryName, link.toAbsolutePath());
            } else {
                try (CachingOutputStream outputStream = new CachingOutputStream(outputFile)) {
                    entryProcessor.processStream(archiveEntry.getName(), archiveEntry.getInputStream(), outputStream);
                    outputStream.close();
                    if (outputStream.isModified()) {
                        setFilePermission(archiveEntry, outputFile);
                    }
                } finally {
                    entryProcessor.processed(entryName, outputFile);
                }
            }
        }
        source.close();
    }

    private String adjustPath(boolean target, String entryName, UnarchivingEnhancedEntryProcessor entryProcessor) {
        if (!useRoot) {
            entryName = entryName.substring(entryName.indexOf('/') + 1);
        }
        // Process the entry name before any output is created on disk
        if (target) {
            entryName = entryProcessor.targetName(entryName);
        } else {
            entryName = entryProcessor.sourceName(entryName);
        }
        // So with an entry we may want to take a set of entry in a set of directories and flatten them
        // into one directory, or we may want to preserve the directory structure.
        if (flatten) {
            entryName = entryName.substring(entryName.lastIndexOf("/") + 1);
        }
        return entryName;
    }

    private void setFilePermission(ExtendedArchiveEntry archiveEntry, Path outputFile) throws IOException {
        int mode = archiveEntry.getFileMode();
        //
        // Currently, zip entries produced by plexus-archiver return 0 for the unix mode, so I'm doing something wrong,
        // or
        // it's not being stored directly. So in the case of unpacking an zip archive we don't want to produce files
        // that are unreadable or unusable, so we'll give files 0644 and directories 0755
        //
        if (mode > 0) {
            setFilePermissions(outputFile, FileMode.toPermissionsSet(mode));
        } else {
            if (archiveEntry.isDirectory()) {
                setFilePermissions(outputFile, PosixModes.intModeToPosix(0755));
            } else {
                setFilePermissions(outputFile, PosixModes.intModeToPosix(0644));
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
     * {@link UnarchivingEnhancedEntryProcessor} that leaves the entry name and content as-is.
     */
    static class NoopEntryProcessor implements UnarchivingEnhancedEntryProcessor {}

    public static class UnArchiverBuilder {

        List<String> includes = new ArrayList<>();
        List<String> excludes = new ArrayList<>();
        boolean useRoot = true;
        boolean flatten = false;
        boolean posixLongFileMode;
        boolean dereferenceHardlinks = false;

        public UnArchiverBuilder includes(String... includes) {
            List<String> i = new ArrayList<>();
            for (String include : includes) {
                if (include != null) {
                    i.add(include);
                }
            }
            return includes(List.copyOf(i));
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
            return excludes(List.copyOf(i));
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

        public UnArchiverBuilder posixLongFileMode(boolean posixLongFileMode) {
            this.posixLongFileMode = posixLongFileMode;
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
