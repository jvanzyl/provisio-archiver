/*
 * Copyright (c) 2014-2024 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 */
package ca.vanzyl.provisio.archive.tar;

import ca.vanzyl.provisio.archive.ExtendedArchiveEntry;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;

public class ExtendedTarArchiveEntry extends TarArchiveEntry implements ExtendedArchiveEntry {

    private ExtendedArchiveEntry entry;
    private boolean hardLink;
    private boolean symbolicLink;

    public ExtendedTarArchiveEntry(String entryName, byte linkFlag) {
        super(entryName, linkFlag);
        this.hardLink = true;
    }

    public ExtendedTarArchiveEntry(String entryName, ExtendedArchiveEntry entry) {
        super(entryName);
        this.entry = entry;
    }

    @Override
    public void setFileMode(int mode) {
        setMode(mode);
    }

    @Override
    public int getFileMode() {
        return getMode();
    }

    @Override
    public boolean isSymbolicLink() {
        return symbolicLink;
    }

    @Override
    public String getSymbolicLinkPath() {
        return entry.getSymbolicLinkPath();
    }

    @Override
    public boolean isHardLink() {
        return hardLink;
    }

    @Override
    public String getHardLinkPath() {
        return entry.getHardLinkPath();
    }

    @Override
    public boolean isExecutable() {
        return false;
    }

    @Override
    public long getTime() {
        return 0;
    }

    @Override
    public void setTime(long time) {
        setModTime(time);
    }

    @Override
    public void writeEntry(OutputStream outputStream) throws IOException {
        if (!hardLink) {
            entry.writeEntry(outputStream);
        }
    }

    @Override
    public InputStream getInputStream() throws IOException {
        return entry.getInputStream();
    }
}
