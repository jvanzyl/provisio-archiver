/*
 * Copyright (c) 2014-2024 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 */
package ca.vanzyl.provisio.archive.source;

import ca.vanzyl.provisio.archive.ExtendedArchiveEntry;
import ca.vanzyl.provisio.archive.Source;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;
import org.codehaus.plexus.util.DirectoryScanner;

public class DirectorySource implements Source {
    private final File[] sourceDirectories;

    public DirectorySource(File... sourceDirectories) {
        this.sourceDirectories = sourceDirectories;
    }

    @Override
    public Iterable<ExtendedArchiveEntry> entries() {
        return () -> new DirectoryEntryIterator(sourceDirectories);
    }

    static class DirectoryEntryIterator implements Iterator<ExtendedArchiveEntry> {
        final List<FileEntry> files = new ArrayList<>();
        int currentFileIndex;

        DirectoryEntryIterator(File[] sourceDirectories) {
            for (File sourceDirectory : sourceDirectories) {
                String normalizedSourceDirectory = sourceDirectory.getName().replace('\\', '/');
                DirectoryScanner scanner = new DirectoryScanner();
                scanner.setBasedir(sourceDirectory);
                scanner.setCaseSensitive(true);
                scanner.scan();
                //
                // We need to include the directories to preserved the archiving of empty directories. We are also
                // sorting in natural
                // order because it seems that on the Linux with GitHub Actions the files are coming off the disk not
                // naturally sorted.
                // I thought this was always the case, but apparently not. Sorting here makes sure that everything
                // beyond this point
                // will be in sorted order as our reproducibility model depends on this fact.
                //
                Stream.of(scanner.getIncludedFiles(), scanner.getIncludedDirectories())
                        .flatMap(Stream::of)
                        .sorted()
                        .forEach(f -> {
                            if (!f.isEmpty()) {
                                File file = new File(sourceDirectory, f);
                                String archiveEntryName = normalizedSourceDirectory + "/" + f.replace('\\', '/');
                                files.add(new FileEntry(archiveEntryName, file));
                            }
                        });
            }
        }

        @Override
        public boolean hasNext() {
            return currentFileIndex != files.size();
        }

        @Override
        public ExtendedArchiveEntry next() {
            return files.get(currentFileIndex++);
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException("remove method not implemented");
        }
    }

    @Override
    public void close() throws IOException {}

    @Override
    public boolean isDirectory() {
        return true;
    }
}
