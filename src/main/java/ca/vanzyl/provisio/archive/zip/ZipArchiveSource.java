/*
 * Copyright (c) 2014-2024 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 */
package ca.vanzyl.provisio.archive.zip;

import static ca.vanzyl.provisio.archive.zip.ExtendedZipArchiveEntry.dosToJavaTime;

import ca.vanzyl.provisio.archive.ExtendedArchiveEntry;
import ca.vanzyl.provisio.archive.Source;
import ca.vanzyl.provisio.archive.perms.FileMode;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Date;
import java.util.Enumeration;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.io.IOUtils;

public class ZipArchiveSource implements Source {

    private final File archive;

    public ZipArchiveSource(File archive) {
        this.archive = archive;
    }

    @Override
    public void forEachEntry(EntryConsumer consumer) throws IOException {
        try (ZipFile zipFile = ZipFile.builder()
                .setFile(archive)
                .setUseUnicodeExtraFields(false)
                .get()) {
            // UTF-8 is the default charset
            Enumeration<ZipArchiveEntry> entries = zipFile.getEntries();
            while (entries.hasMoreElements()) {
                consumer.accept(new EntrySourceArchiveEntry(zipFile, entries.nextElement()));
            }
        }
    }

    class EntrySourceArchiveEntry implements ExtendedArchiveEntry {

        private final ZipFile zipFile;
        private final ZipArchiveEntry archiveEntry;

        public EntrySourceArchiveEntry(ZipFile zipFile, ZipArchiveEntry archiveEntry) {
            this.zipFile = zipFile;
            this.archiveEntry = archiveEntry;
        }

        @Override
        public String getName() {
            return archiveEntry.getName();
        }

        @Override
        public InputStream getInputStream() throws IOException {
            return zipFile.getInputStream(archiveEntry);
        }

        @Override
        public boolean isSymbolicLink() {
            return archiveEntry.isUnixSymlink();
        }

        @Override
        public String getSymbolicLinkPath() {
            try (InputStream is = getInputStream();
                    ByteArrayOutputStream os = new ByteArrayOutputStream()) {
                IOUtils.copyLarge(is, os);
                return os.toString();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
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
        public long getSize() {
            return archiveEntry.getSize();
        }

        @Override
        public void writeEntry(OutputStream outputStream) throws IOException {
            try (InputStream inputStream = getInputStream()) {
                IOUtils.copyLarge(inputStream, outputStream);
            }
        }

        @Override
        public void setFileMode(int mode) {}

        @Override
        public int getFileMode() {
            return archiveEntry.getUnixMode();
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
            return FileMode.EXECUTABLE_FILE.equals(getFileMode());
        }

        @Override
        public long getTime() {
            long time = archiveEntry.getTime();
            if (time != -1) {
                return dosToJavaTime(time, false);
            }
            return time;
        }
    }

    @Override
    public void close() throws IOException {}

    @Override
    public boolean isDirectory() {
        return true;
    }
}
