/*
 * Copyright (c) 2014-2024 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 */
package ca.vanzyl.provisio.archive.zip;

import ca.vanzyl.provisio.archive.ArchiveHandlerSupport;
import ca.vanzyl.provisio.archive.ExtendedArchiveEntry;
import ca.vanzyl.provisio.archive.Source;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.ArchiveOutputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;

public class ZipArchiveHandler extends ArchiveHandlerSupport {

    private final File archive;

    public ZipArchiveHandler(File archive) {
        this.archive = archive;
    }

    @Override
    public ExtendedArchiveEntry newEntry(String entryName, ExtendedArchiveEntry entry) {
        return new ExtendedZipArchiveEntry(entryName, entry);
    }

    @Override
    public ArchiveInputStream getInputStream() throws IOException {
        return new ZipArchiveInputStream(new FileInputStream(archive), "UTF8", true, true);
    }

    @Override
    public Source getArchiveSource() {
        return new ZipArchiveSource(archive);
    }

    @Override
    public ArchiveOutputStream getOutputStream() throws IOException {
        return new ZipArchiveOutputStream(new FileOutputStream(archive));
    }
}
