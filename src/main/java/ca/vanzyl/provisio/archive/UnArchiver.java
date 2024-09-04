/*
 * Copyright (c) 2014-2024 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 */
package ca.vanzyl.provisio.archive;

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
        unarchive(archive, outputDirectory, new NoopEntryProcessor());
    }

    public void unarchive(File archive, File outputDirectory, UnarchivingEntryProcessor entryProcessor)
            throws IOException {
        archive = archive.getAbsoluteFile();
        outputDirectory = outputDirectory.getAbsoluteFile();
        final Path outputDirectoryPath = outputDirectory.toPath();
        //
        // These are the contributions that unpacking this archive is providing
        //
        // mkdirs() would check if the directory exists
        outputDirectory.mkdirs();
        Source source = ArchiverHelper.getArchiveHandler(archive, builder).getArchiveSource();
        for (ExtendedArchiveEntry archiveEntry : source.entries()) {
            String entryName = adjustPath(archiveEntry.getName(), entryProcessor);

            if (!selector.include(entryName)) {
                continue;
            }
            File outputFile = new File(outputDirectory, entryName).getAbsoluteFile();
            if (!outputFile.toPath().startsWith(outputDirectoryPath)) {
                throw new IOException("Archive escape attempt detected in " + archive);
            }

            if (archiveEntry.isDirectory()) {
                createDir(outputFile);
                continue;
            }

            //
            // If we take an archive and flatten it into the output directory the first entry will
            // match the output directory which exists so it will cause an error trying to make it
            //
            if (outputFile.equals(outputDirectory)) {
                continue;
            }
            if (!outputFile.getParentFile().exists()) {
                createDir(outputFile.getParentFile());
            }

            if (archiveEntry.isHardLink()) {
                File hardLinkSource =
                        new File(outputDirectory, adjustPath(archiveEntry.getHardLinkPath(), entryProcessor));
                if (dereferenceHardlinks) {
                    Files.copy(hardLinkSource.toPath(), outputFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                } else {
                    // Remove any existing file or link as Files.createLink has no option to overwrite
                    Files.deleteIfExists(outputFile.toPath());
                    Files.createLink(outputFile.toPath(), hardLinkSource.toPath());
                }
                setFilePermission(archiveEntry, outputFile);
            } else if (archiveEntry.isSymbolicLink()) {
                // We expect symlinks to be relative as they are not generally useful in a tarball otherwise
                Path outputPath = outputDirectory.toPath();
                Path link = outputDirectory.toPath().resolve(entryName);
                Path target =
                        outputDirectory.toPath().relativize(outputPath.resolve(archiveEntry.getSymbolicLinkPath()));
                Files.createDirectories(link.getParent());
                Files.createSymbolicLink(link, target);
            } else {
                try (CachingOutputStream outputStream = new CachingOutputStream(outputFile)) {
                    entryProcessor.processStream(archiveEntry.getName(), archiveEntry.getInputStream(), outputStream);
                    outputStream.close();
                    if (outputStream.isModified()) {
                        setFilePermission(archiveEntry, outputFile);
                    }
                }
            }
        }
        source.close();
    }

    private String adjustPath(String entryName, UnarchivingEntryProcessor entryProcessor) {
        if (!useRoot) {
            entryName = entryName.substring(entryName.indexOf('/') + 1);
        }
        // Process the entry name before any output is created on disk
        entryName = entryProcessor.processName(entryName);
        // So with an entry we may want to take a set of entry in a set of directories and flatten them
        // into one directory, or we may want to preserve the directory structure.
        if (flatten) {
            entryName = entryName.substring(entryName.lastIndexOf("/") + 1);
        }
        return entryName;
    }

    private void setFilePermission(ExtendedArchiveEntry archiveEntry, File outputFile) throws IOException {
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

    private void setFilePermissions(File file, Set<PosixFilePermission> perms) throws IOException {
        try {
            Files.setPosixFilePermissions(file.toPath(), perms);
        } catch (UnsupportedOperationException e) {
            // ignore, must be windows
        }
    }

    private void createDir(File dir) {
        if (!dir.exists()) {
            dir.mkdirs();
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
     * {@EntryProcesor} that leaves the entry name and content as-is.
     */
    static class NoopEntryProcessor implements UnarchivingEntryProcessor {

        @Override
        public String processName(String entryName) {
            return entryName;
        }

        @Override
        public void processStream(String entryName, InputStream inputStream, OutputStream outputStream)
                throws IOException {
            inputStream.transferTo(outputStream);
        }
    }

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
