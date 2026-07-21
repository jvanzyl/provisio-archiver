/*
 * Copyright (c) 2014-2024 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 */
package ca.vanzyl.provisio.archive;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;
import org.apache.commons.compress.archivers.zip.UnixStat;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.io.IOUtils;

final class ZipArchiveWriter implements ArchiveWriter {

    private final ZipArchiveOutputStream outputStream;

    ZipArchiveWriter(Path archive) throws IOException {
        outputStream = new ZipArchiveOutputStream(Files.newOutputStream(archive));
    }

    @Override
    public void write(OutputEntry entry) throws IOException {
        if (entry.getType() == EntryType.HARD_LINK) {
            throw new IOException("ZIP does not support hard link entry " + entry.getName());
        }

        ZipArchiveEntry archiveEntry = new ZipArchiveEntry(entry.getName());
        int mode = unixMode(entry);
        if (mode != -1) {
            archiveEntry.setUnixMode(mode);
        }
        if (entry.getTime() != -1) {
            archiveEntry.setTime(dosToJavaTime(entry.getTime(), true));
        }

        outputStream.putArchiveEntry(archiveEntry);
        if (entry.getType() == EntryType.FILE || entry.getType() == EntryType.SYMBOLIC_LINK) {
            try (InputStream inputStream = content(entry)) {
                IOUtils.copyLarge(inputStream, outputStream);
            }
        }
        outputStream.closeArchiveEntry();
    }

    private int unixMode(OutputEntry entry) {
        int mode = entry.getFileMode();
        if (entry.getType() == EntryType.SYMBOLIC_LINK) {
            int permissions = mode == -1 ? 0777 : mode & UnixStat.PERM_MASK;
            return UnixStat.LINK_FLAG | permissions;
        }
        return mode;
    }

    private InputStream content(OutputEntry entry) throws IOException {
        if (entry.getType() == EntryType.SYMBOLIC_LINK) {
            return new ByteArrayInputStream(entry.getLinkTarget().getBytes(StandardCharsets.UTF_8));
        }
        return entry.getContent().open();
    }

    private static long dosToJavaTime(long time, boolean writeToArchive) {
        Calendar calendar = Calendar.getInstance(TimeZone.getDefault(), Locale.ROOT);
        calendar.setTimeInMillis(time);
        return time
                - ((calendar.get(Calendar.ZONE_OFFSET) + calendar.get(Calendar.DST_OFFSET))
                        * (writeToArchive ? 1 : -1));
    }

    @Override
    public void close() throws IOException {
        outputStream.close();
    }
}
