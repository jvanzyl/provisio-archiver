/*
 * Copyright (c) 2014-2024 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 */
package ca.vanzyl.provisio.archive.zip;

import ca.vanzyl.provisio.archive.EntryContent;
import ca.vanzyl.provisio.archive.Source;
import ca.vanzyl.provisio.archive.SourceEntry;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Calendar;
import java.util.Enumeration;
import java.util.Locale;
import java.util.TimeZone;
import java.util.zip.CRC32;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.io.IOUtils;

public class ZipArchiveSource implements Source {

    private final Path archive;

    public ZipArchiveSource(Path archive) {
        this.archive = archive;
    }

    @Override
    public void forEachEntry(EntryConsumer consumer) throws IOException {
        try (ZipFile zipFile = ZipFile.builder()
                .setPath(archive)
                .setUseUnicodeExtraFields(false)
                .get()) {
            Enumeration<ZipArchiveEntry> entries = zipFile.getEntries();
            while (entries.hasMoreElements()) {
                acceptEntry(zipFile, entries.nextElement(), consumer);
            }
        }
    }

    private void acceptEntry(ZipFile zipFile, ZipArchiveEntry archiveEntry, EntryConsumer consumer) throws IOException {
        String name = archiveEntry.getName();
        int mode = archiveEntry.getUnixMode();
        long time = archiveEntry.getTime();
        if (time != -1) {
            time = dosToJavaTime(time, false);
        }

        if (archiveEntry.isDirectory()) {
            consumer.accept(SourceEntry.directory(name, mode, time));
            return;
        }

        ZipEntryContent content = new ZipEntryContent(zipFile, archiveEntry);
        try {
            if (archiveEntry.isUnixSymlink()) {
                String target;
                try (InputStream inputStream = content.open()) {
                    target = new String(IOUtils.toByteArray(inputStream), StandardCharsets.UTF_8);
                }
                consumer.accept(SourceEntry.symbolicLink(name, target, mode, time));
            } else {
                consumer.accept(SourceEntry.file(name, content, mode, time));
            }
        } finally {
            content.invalidate();
        }
    }

    private static long dosToJavaTime(long time, boolean writeToArchive) {
        Calendar calendar = Calendar.getInstance(TimeZone.getDefault(), Locale.ROOT);
        calendar.setTimeInMillis(time);
        return time
                - ((calendar.get(Calendar.ZONE_OFFSET) + calendar.get(Calendar.DST_OFFSET))
                        * (writeToArchive ? 1 : -1));
    }

    @Override
    public void close() throws IOException {}

    @Override
    public boolean isDirectory() {
        return true;
    }

    private static final class ZipEntryContent implements EntryContent {

        private final ZipFile zipFile;
        private final ZipArchiveEntry archiveEntry;
        private boolean active = true;

        private ZipEntryContent(ZipFile zipFile, ZipArchiveEntry archiveEntry) {
            this.zipFile = zipFile;
            this.archiveEntry = archiveEntry;
        }

        @Override
        public InputStream open() throws IOException {
            ensureActive();
            return new FilterInputStream(zipFile.getInputStream(archiveEntry)) {
                private final CRC32 crc32 = new CRC32();
                private long size;
                private boolean validated;

                @Override
                public int read() throws IOException {
                    ensureActive();
                    int value = super.read();
                    if (value == -1) {
                        validateContent();
                    } else {
                        crc32.update(value);
                        size++;
                    }
                    return value;
                }

                @Override
                public int read(byte[] bytes, int offset, int length) throws IOException {
                    ensureActive();
                    int count = super.read(bytes, offset, length);
                    if (count == -1) {
                        validateContent();
                    } else {
                        crc32.update(bytes, offset, count);
                        size += count;
                    }
                    return count;
                }

                private void validateContent() throws IOException {
                    if (validated) {
                        return;
                    }
                    validated = true;
                    if (size != archiveEntry.getSize()) {
                        throw new IOException("ZIP entry size mismatch for " + archiveEntry.getName() + ": expected "
                                + archiveEntry.getSize() + " but read " + size);
                    }
                    if (archiveEntry.getCrc() != -1 && crc32.getValue() != archiveEntry.getCrc()) {
                        throw new IOException("ZIP entry CRC mismatch for " + archiveEntry.getName() + ": expected "
                                + archiveEntry.getCrc() + " but calculated " + crc32.getValue());
                    }
                }
            };
        }

        @Override
        public long size() {
            return archiveEntry.getSize();
        }

        @Override
        public long crc32() {
            return archiveEntry.getCrc();
        }

        @Override
        public boolean isRepeatable() {
            return true;
        }

        private void invalidate() {
            active = false;
        }

        private void ensureActive() throws IOException {
            if (!active) {
                throw new IOException("ZIP entry content is no longer active");
            }
        }
    }
}
