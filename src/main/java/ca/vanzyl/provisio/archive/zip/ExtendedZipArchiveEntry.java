/*
 * Copyright (c) 2014-2024 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 */
package ca.vanzyl.provisio.archive.zip;

import ca.vanzyl.provisio.archive.ExtendedArchiveEntry;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;

public class ExtendedZipArchiveEntry extends ZipArchiveEntry implements ExtendedArchiveEntry {

    private final ExtendedArchiveEntry entry;

    public ExtendedZipArchiveEntry(String entryName, ExtendedArchiveEntry entry) {
        super(entryName);
        this.entry = entry;
    }

    @Override
    public void setFileMode(int mode) {
        setUnixMode(mode);
    }

    @Override
    public int getFileMode() {
        return getUnixMode();
    }

    @Override
    public void writeEntry(OutputStream outputStream) throws IOException {
        entry.writeEntry(outputStream);
    }

    @Override
    public InputStream getInputStream() throws IOException {
        return entry.getInputStream();
    }

    @Override
    public boolean isSymbolicLink() {
        return false;
    }

    @Override
    public String getSymbolicLinkPath() {
        return null;
    }

    @Override
    public boolean isHardLink() {
        return false;
    }

    @Override
    public String getHardLinkPath() {
        return null;
    }

    @Override
    public boolean isExecutable() {
        return false;
    }
}
