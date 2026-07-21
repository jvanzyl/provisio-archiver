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
import ca.vanzyl.provisio.archive.perms.FileModes;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class FileSource implements Source {

    private final String archiveEntryName;
    private final Path file;

    public FileSource(Path file) {
        this.archiveEntryName = file.getFileName().toString();
        this.file = file;
    }

    public FileSource(String archiveEntryName, Path file) {
        this.archiveEntryName = archiveEntryName;
        this.file = file;
    }

    @Override
    public void forEachEntry(EntryConsumer consumer) throws IOException {
        if (Files.isDirectory(file)) {
            consumer.accept(SourceEntry.directory(
                    archiveEntryName,
                    FileModes.read(file),
                    Files.getLastModifiedTime(file).toMillis()));
        } else {
            consumer.accept(SourceEntry.file(
                    archiveEntryName,
                    EntryContents.of(file),
                    FileModes.read(file),
                    Files.getLastModifiedTime(file).toMillis()));
        }
    }

    @Override
    public void close() throws IOException {}

    @Override
    public boolean isDirectory() {
        return false;
    }
}
