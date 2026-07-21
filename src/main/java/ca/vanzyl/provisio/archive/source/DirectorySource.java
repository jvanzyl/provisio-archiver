/*
 * Copyright (c) 2014-2024 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 */
package ca.vanzyl.provisio.archive.source;

import ca.vanzyl.provisio.archive.EntryContents;
import ca.vanzyl.provisio.archive.Source;
import ca.vanzyl.provisio.archive.SourceEntry;
import ca.vanzyl.provisio.archive.perms.FileMode;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.codehaus.plexus.util.DirectoryScanner;

public class DirectorySource implements Source {
    private final File[] sourceDirectories;

    public DirectorySource(File... sourceDirectories) {
        this.sourceDirectories = sourceDirectories;
    }

    @Override
    public void forEachEntry(EntryConsumer consumer) throws IOException {
        List<SourceEntry> files = new ArrayList<>();
        for (File sourceDirectory : sourceDirectories) {
            String normalizedSourceDirectory = sourceDirectory.getName().replace('\\', '/');
            DirectoryScanner scanner = new DirectoryScanner();
            scanner.setBasedir(sourceDirectory);
            scanner.setCaseSensitive(true);
            scanner.scan();
            //
            // We need to include the directories to preserve the archiving of empty directories. We also sort in
            // natural order because filesystem traversal order differs across platforms.
            //
            List<String> entryNames = Stream.of(scanner.getIncludedFiles(), scanner.getIncludedDirectories())
                    .flatMap(Stream::of)
                    .sorted()
                    .collect(Collectors.toList());
            for (String entryName : entryNames) {
                if (!entryName.isEmpty()) {
                    File file = new File(sourceDirectory, entryName);
                    String archiveEntryName = normalizedSourceDirectory + "/" + entryName.replace('\\', '/');
                    files.add(
                            file.isDirectory()
                                    ? SourceEntry.directory(
                                            archiveEntryName, FileMode.getFileMode(file), file.lastModified())
                                    : SourceEntry.file(
                                            archiveEntryName,
                                            EntryContents.of(file.toPath()),
                                            FileMode.getFileMode(file),
                                            file.lastModified()));
                }
            }
        }
        for (SourceEntry file : files) {
            consumer.accept(file);
        }
    }

    @Override
    public void close() throws IOException {}

    @Override
    public boolean isDirectory() {
        return true;
    }
}
