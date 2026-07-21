/*
 * Copyright (c) 2014-2024 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 */
package ca.vanzyl.provisio.archive.zip;

import ca.vanzyl.provisio.archive.ExtendedArchiveEntry;
import ca.vanzyl.provisio.archive.SourceEntry;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;

public class ExtendedZipArchiveEntry extends ZipArchiveEntry implements ExtendedArchiveEntry {

    private final SourceEntry entry;

    public ExtendedZipArchiveEntry(String entryName, SourceEntry entry) {
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
        try (InputStream inputStream = getInputStream()) {
            org.apache.commons.io.IOUtils.copyLarge(inputStream, outputStream);
        }
    }

    @Override
    public InputStream getInputStream() throws IOException {
        if (entry.isSymbolicLink()) {
            return new ByteArrayInputStream(entry.getLinkTarget().getBytes(StandardCharsets.UTF_8));
        }
        return entry.getContent().open();
    }

    @Override
    public boolean isSymbolicLink() {
        return entry.isSymbolicLink();
    }

    @Override
    public String getSymbolicLinkPath() {
        return entry.isSymbolicLink() ? entry.getLinkTarget() : null;
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

    @Override
    public void setTime(long timeEpochMillis) {
        if (timeEpochMillis != -1) {
            timeEpochMillis = dosToJavaTime(timeEpochMillis, true);
        }
        super.setTime(timeEpochMillis);
    }

    /**
     * Converts DOS epoch to UNIX epoch timestamp and other way around. DOS epoch is "local time" so it is about
     * removing or adding TZ and DST offset.
     */
    static long dosToJavaTime(long time, boolean writeToArchive) {
        Calendar cal = Calendar.getInstance(TimeZone.getDefault(), Locale.ROOT);
        cal.setTimeInMillis(time);
        return time - ((cal.get(Calendar.ZONE_OFFSET) + (cal.get(Calendar.DST_OFFSET))) * (writeToArchive ? 1 : -1));
    }
}
