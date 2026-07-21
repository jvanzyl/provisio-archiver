/*
 * Copyright (c) 2014-2024 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 */
package ca.vanzyl.provisio.archive.tar;

import ca.vanzyl.provisio.archive.ArchiverHelper;
import ca.vanzyl.provisio.archive.ExtendedArchiveEntry;
import ca.vanzyl.provisio.archive.Source;
import ca.vanzyl.provisio.archive.UnArchiver;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Date;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.io.IOUtils;

public class TarGzArchiveSource implements Source {

    private final File archive;

    public TarGzArchiveSource(File archive) {
        this.archive = archive;
    }

    @Override
    public void forEachEntry(EntryConsumer consumer) throws IOException {
        try (ArchiveInputStream archiveInputStream =
                ArchiverHelper.getArchiveHandler(archive, UnArchiver.builder()).getInputStream()) {
            TarArchiveEntry archiveEntry;
            while ((archiveEntry = (TarArchiveEntry) archiveInputStream.getNextEntry()) != null) {
                consumer.accept(new EntrySourceArchiveEntry(archiveInputStream, archiveEntry));
            }
        }
    }

    class EntrySourceArchiveEntry implements ExtendedArchiveEntry {

        private final ArchiveInputStream archiveInputStream;
        private final TarArchiveEntry archiveEntry;

        public EntrySourceArchiveEntry(ArchiveInputStream archiveInputStream, TarArchiveEntry archiveEntry) {
            this.archiveInputStream = archiveInputStream;
            this.archiveEntry = archiveEntry;
        }

        @Override
        public String getName() {
            return archiveEntry.getName();
        }

        @Override
        public InputStream getInputStream() throws IOException {
            return archiveInputStream;
        }

        @Override
        public boolean isSymbolicLink() {
            return archiveEntry.isSymbolicLink();
        }

        @Override
        public String getSymbolicLinkPath() {
            return archiveEntry.getLinkName();
        }

        @Override
        public boolean isHardLink() {
            return archiveEntry.isLink();
        }

        @Override
        public String getHardLinkPath() {
            return archiveEntry.getLinkName();
        }

        @Override
        public long getSize() {
            return archiveEntry.getSize();
        }

        @Override
        public void writeEntry(OutputStream outputStream) throws IOException {
            // We specifically do not close the entry because if you do then you can't read anymore archive entries from
            // the stream
            IOUtils.copyLarge(getInputStream(), outputStream);
        }

        @Override
        public void setFileMode(int mode) {}

        @Override
        public int getFileMode() {
            return archiveEntry.getMode();
        }

        @Override
        public void setSize(long size) {}

        @Override
        public void setTime(long time) {}

        @Override
        public boolean isDirectory() {
            return archiveEntry.isDirectory();
        }

        @Override
        public Date getLastModifiedDate() {
            return null;
        }

        @Override
        public boolean isExecutable() {
            return false;
        }

        @Override
        public long getTime() {
            return archiveEntry.getModTime().getTime();
        }
    }

    @Override
    public void close() throws IOException {}

    @Override
    public boolean isDirectory() {
        return true;
    }
}
