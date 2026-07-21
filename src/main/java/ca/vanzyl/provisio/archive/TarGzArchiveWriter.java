/*
 * Copyright (c) 2014-2024 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 */
package ca.vanzyl.provisio.archive;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.archivers.tar.TarConstants;
import org.apache.commons.io.IOUtils;

final class TarGzArchiveWriter implements ArchiveWriter {

    private static final int COMPRESSION_CHUNK_SIZE = 1024 * 1024;

    private final TarArchiveOutputStream outputStream;

    TarGzArchiveWriter(Path archive, boolean posixLongFileMode, GzipCompressionOptions gzipCompression)
            throws IOException {
        outputStream = new TarArchiveOutputStream(new ParallelGzipOutputStream(
                Files.newOutputStream(archive),
                gzipCompression.level(),
                COMPRESSION_CHUNK_SIZE,
                gzipCompression.threads()));
        if (posixLongFileMode) {
            outputStream.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
        }
    }

    @Override
    public void write(OutputEntry entry) throws IOException {
        TarArchiveEntry archiveEntry = new TarArchiveEntry(entry.getName(), linkFlag(entry.getType()));
        if (entry.getLinkTarget() != null) {
            archiveEntry.setLinkName(entry.getLinkTarget());
        }
        if (entry.getFileMode() != -1) {
            archiveEntry.setMode(entry.getFileMode());
        }
        if (entry.getTime() != -1) {
            archiveEntry.setModTime(entry.getTime());
        }
        if (entry.getUserId() != -1) {
            archiveEntry.setUserId(entry.getUserId());
        }
        if (entry.getGroupId() != -1) {
            archiveEntry.setGroupId(entry.getGroupId());
        }
        if (entry.getUserName() != null) {
            archiveEntry.setUserName(entry.getUserName());
        }
        if (entry.getGroupName() != null) {
            archiveEntry.setGroupName(entry.getGroupName());
        }
        if (entry.getType() == EntryType.FILE) {
            archiveEntry.setSize(entry.getContent().size());
        }

        outputStream.putArchiveEntry(archiveEntry);
        if (entry.getType() == EntryType.FILE) {
            try (InputStream inputStream = entry.getContent().open()) {
                IOUtils.copyLarge(inputStream, outputStream);
            }
        }
        outputStream.closeArchiveEntry();
    }

    private byte linkFlag(EntryType type) {
        switch (type) {
            case DIRECTORY:
                return TarConstants.LF_DIR;
            case SYMBOLIC_LINK:
                return TarConstants.LF_SYMLINK;
            case HARD_LINK:
                return TarConstants.LF_LINK;
            case FILE:
                return TarConstants.LF_NORMAL;
            default:
                throw new IllegalArgumentException("Unsupported tar entry type " + type);
        }
    }

    @Override
    public void close() throws IOException {
        outputStream.close();
    }
}
