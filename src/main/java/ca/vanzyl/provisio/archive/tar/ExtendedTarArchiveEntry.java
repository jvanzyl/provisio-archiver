/*
 * Copyright (c) 2014-2024 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 */
package ca.vanzyl.provisio.archive.tar;

import ca.vanzyl.provisio.archive.EntryType;
import ca.vanzyl.provisio.archive.ExtendedArchiveEntry;
import ca.vanzyl.provisio.archive.SourceEntry;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarConstants;

public class ExtendedTarArchiveEntry extends TarArchiveEntry implements ExtendedArchiveEntry {

    private final SourceEntry entry;

    public ExtendedTarArchiveEntry(String entryName, String hardLinkTarget) {
        super(entryName, TarConstants.LF_LINK);
        this.entry = SourceEntry.hardLink(entryName, hardLinkTarget, -1, 0);
        setLinkName(hardLinkTarget);
    }

    public ExtendedTarArchiveEntry(String entryName, SourceEntry entry) {
        super(entryName, linkFlag(entry));
        this.entry = entry;
        if (entry.getLinkTarget() != null) {
            setLinkName(entry.getLinkTarget());
        }
    }

    private static byte linkFlag(SourceEntry entry) {
        if (entry.getType() == EntryType.DIRECTORY) {
            return TarConstants.LF_DIR;
        }
        if (entry.getType() == EntryType.SYMBOLIC_LINK) {
            return TarConstants.LF_SYMLINK;
        }
        if (entry.getType() == EntryType.HARD_LINK) {
            return TarConstants.LF_LINK;
        }
        return TarConstants.LF_NORMAL;
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
    public String getSymbolicLinkPath() {
        return isSymbolicLink() ? getLinkName() : null;
    }

    @Override
    public boolean isHardLink() {
        return isLink();
    }

    @Override
    public String getHardLinkPath() {
        return isHardLink() ? getLinkName() : null;
    }

    @Override
    public boolean isExecutable() {
        return false;
    }

    @Override
    public long getTime() {
        return getModTime().getTime();
    }

    @Override
    public void setTime(long time) {
        setModTime(time);
    }

    @Override
    public void writeEntry(OutputStream outputStream) throws IOException {
        if (!isHardLink() && !isDirectory() && !isSymbolicLink()) {
            try (InputStream inputStream = entry.getContent().open()) {
                org.apache.commons.io.IOUtils.copyLarge(inputStream, outputStream);
            }
        }
    }

    @Override
    public InputStream getInputStream() throws IOException {
        return entry.getContent().open();
    }
}
