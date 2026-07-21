/*
 * Copyright (c) 2014-2024 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 */
package ca.vanzyl.provisio.archive.source;

import ca.vanzyl.provisio.archive.Source;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.codehaus.plexus.util.DirectoryScanner;

public class DirectorySource implements Source {
    private final File[] sourceDirectories;

    public DirectorySource(File... sourceDirectories) {
        this.sourceDirectories = sourceDirectories;
    }

    @Override
    public void forEachEntry(EntryConsumer consumer) throws IOException {
        List<FileEntry> files = new ArrayList<>();
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
        for (FileEntry file : files) {
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
