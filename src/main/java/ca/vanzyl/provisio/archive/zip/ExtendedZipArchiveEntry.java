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
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;
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
