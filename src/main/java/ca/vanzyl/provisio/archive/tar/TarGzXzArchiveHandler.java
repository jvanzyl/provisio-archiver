/*
 * Copyright (c) 2014-2024 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 */
package ca.vanzyl.provisio.archive.tar;

import ca.vanzyl.provisio.archive.ArchiveHandlerSupport;
import ca.vanzyl.provisio.archive.ExtendedArchiveEntry;
import ca.vanzyl.provisio.archive.Selector;
import ca.vanzyl.provisio.archive.Source;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.ArchiveOutputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.archivers.tar.TarConstants;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream;
import org.apache.commons.compress.compressors.xz.XZCompressorOutputStream;

public class TarGzXzArchiveHandler extends ArchiveHandlerSupport {

    private final File archive;
    private final boolean posixLongFileMode;
    private final Map<String, ExtendedArchiveEntry> processedFilesNames;
    private final Selector hardLinkSelector;

    public TarGzXzArchiveHandler(
            File archive, boolean posixLongFileMode, List<String> hardLinkIncludes, List<String> hardLinkExcludes) {
        this.archive = archive;
        this.posixLongFileMode = posixLongFileMode;
        this.processedFilesNames = new TreeMap<>();
        if (!hardLinkIncludes.isEmpty() || !hardLinkExcludes.isEmpty()) {
            this.hardLinkSelector = new Selector(hardLinkIncludes, hardLinkExcludes);
        } else {
            this.hardLinkSelector = new Selector(null, List.of("**/**"));
        }
    }

    @Override
    public ExtendedArchiveEntry newEntry(String entryName, ExtendedArchiveEntry entry) {
        if (hardLinkSelector.include(entryName)) {
            ExtendedArchiveEntry sourceToHardLink = processedFilesNames.get(fileNameOf(entry));
            if (sourceToHardLink != null) {
                ExtendedTarArchiveEntry tarArchiveEntry = new ExtendedTarArchiveEntry(entryName, TarConstants.LF_LINK);
                tarArchiveEntry.setLinkName(sourceToHardLink.getName());
                return tarArchiveEntry;
            }
            processedFilesNames.put(fileNameOf(entry), entry);
        }
        ExtendedTarArchiveEntry tarArchiveEntry = new ExtendedTarArchiveEntry(entryName, entry);
        tarArchiveEntry.setSize(entry.getSize());
        return tarArchiveEntry;
    }

    @Override
    public ArchiveInputStream getInputStream() throws IOException {
        if (archive.getName().endsWith(".xz")) {
            return new TarArchiveInputStream(new XZCompressorInputStream(new FileInputStream(archive)));
        } else {
            return new TarArchiveInputStream(new GzipCompressorInputStream(new FileInputStream(archive)));
        }
    }

    @Override
    public ArchiveOutputStream getOutputStream() throws IOException {
        TarArchiveOutputStream stream;
        if (archive.getName().endsWith(".xz")) {
            stream = new TarArchiveOutputStream(new XZCompressorOutputStream(new FileOutputStream(archive)));
        } else {
            stream = new TarArchiveOutputStream(new GzipCompressorOutputStream(new FileOutputStream(archive)));
        }
        if (posixLongFileMode) {
            stream.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
        }
        return stream;
    }

    @Override
    public Source getArchiveSource() {
        return new TarGzXzArchiveSource(archive);
    }

    private String fileNameOf(ExtendedArchiveEntry entry) {
        return entry.getName().substring(entry.getName().lastIndexOf('/') + 1);
    }
}
