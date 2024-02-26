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
import java.util.Collections;

public class FileSource implements Source {

    private final String archiveEntryName;
    private final File file;

    public FileSource(File file) {
        this.archiveEntryName = file.getName();
        this.file = file;
    }

    public FileSource(String archiveEntryName, File file) {
        this.archiveEntryName = archiveEntryName;
        this.file = file;
    }

    @Override
    public Iterable<ExtendedArchiveEntry> entries() {
        return Collections.singleton(new FileEntry(archiveEntryName, file));
    }

    @Override
    public void close() throws IOException {}

    @Override
    public boolean isDirectory() {
        return false;
    }
}
