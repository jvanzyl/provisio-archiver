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
import ca.vanzyl.provisio.archive.SourceEntry;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.ArchiveOutputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;

public class TarGzArchiveHandler extends ArchiveHandlerSupport {

    private final File archive;
    private final boolean posixLongFileMode;
    private final Map<String, SourceEntry> processedFilesNames;
    private final Selector hardLinkSelector;

    public TarGzArchiveHandler(
            File archive, boolean posixLongFileMode, List<String> hardLinkIncludes, List<String> hardLinkExcludes) {
        this.archive = archive;
        this.posixLongFileMode = posixLongFileMode;
        this.processedFilesNames = new TreeMap<>();
        if (!hardLinkIncludes.isEmpty() || !hardLinkExcludes.isEmpty()) {
            this.hardLinkSelector = new Selector(hardLinkIncludes, hardLinkExcludes);
        } else {
            this.hardLinkSelector = new Selector(null, Collections.singletonList("**/**"));
        }
    }

    @Override
    public ExtendedArchiveEntry newEntry(String entryName, SourceEntry entry) {
        if (hardLinkSelector.include(entryName)) {
            SourceEntry sourceToHardLink = processedFilesNames.get(fileNameOf(entry));
            if (sourceToHardLink != null) {
                return new ExtendedTarArchiveEntry(entryName, sourceToHardLink.getName());
            }
            processedFilesNames.put(fileNameOf(entry), entry);
        }
        ExtendedTarArchiveEntry tarArchiveEntry = new ExtendedTarArchiveEntry(entryName, entry);

        // We don't want directories to have sizes reported by the operating system
        // which differ between Linux (4096) and MacOS (96). This makes builds non-reproducible.
        if (tarArchiveEntry.isFile()) {
            tarArchiveEntry.setSize(entry.getSize());
        }
        return tarArchiveEntry;
    }

    @Override
    public ArchiveInputStream getInputStream() throws IOException {
        return new TarArchiveInputStream(new GzipCompressorInputStream(new FileInputStream(archive)));
    }

    @Override
    public ArchiveOutputStream getOutputStream() throws IOException {
        TarArchiveOutputStream stream =
                new TarArchiveOutputStream(new GzipCompressorOutputStream(new FileOutputStream(archive)));
        if (posixLongFileMode) {
            stream.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
        }
        return stream;
    }

    @Override
    public Source getArchiveSource() {
        return new TarGzArchiveSource(archive);
    }

    private String fileNameOf(SourceEntry entry) {
        return entry.getName().substring(entry.getName().lastIndexOf('/') + 1);
    }
}
