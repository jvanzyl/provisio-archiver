/*
 * Copyright (c) 2014-2024 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 */
package ca.vanzyl.provisio.archive.tar;

import ca.vanzyl.provisio.archive.EntryContent;
import ca.vanzyl.provisio.archive.Source;
import ca.vanzyl.provisio.archive.SourceEntry;
import java.io.File;
import java.io.FileInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarConstants;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.io.input.CloseShieldInputStream;

public class TarGzArchiveSource implements Source {

    private final File archive;

    public TarGzArchiveSource(File archive) {
        this.archive = archive;
    }

    @Override
    public void forEachEntry(EntryConsumer consumer) throws IOException {
        try (TarArchiveInputStream inputStream =
                new TarArchiveInputStream(new GzipCompressorInputStream(new FileInputStream(archive), true))) {
            TarArchiveEntry archiveEntry;
            while ((archiveEntry = inputStream.getNextTarEntry()) != null) {
                acceptEntry(inputStream, archiveEntry, consumer);
            }
        }
    }

    private void acceptEntry(TarArchiveInputStream inputStream, TarArchiveEntry archiveEntry, EntryConsumer consumer)
            throws IOException {
        String name = archiveEntry.getName();
        if (!archiveEntry.isCheckSumOK()) {
            throw new IOException("Invalid tar header checksum for " + name);
        }
        int mode = archiveEntry.getMode();
        long time = archiveEntry.getModTime().getTime();
        if (archiveEntry.isDirectory()) {
            consumer.accept(SourceEntry.directory(name, mode, time));
        } else if (archiveEntry.isSymbolicLink()) {
            consumer.accept(SourceEntry.symbolicLink(name, archiveEntry.getLinkName(), mode, time));
        } else if (archiveEntry.isLink()) {
            consumer.accept(SourceEntry.hardLink(name, archiveEntry.getLinkName(), mode, time));
        } else if (archiveEntry.getLinkFlag() == TarConstants.LF_NORMAL
                || archiveEntry.getLinkFlag() == TarConstants.LF_OLDNORM) {
            SequentialEntryContent content = new SequentialEntryContent(inputStream, archiveEntry.getSize());
            try {
                consumer.accept(SourceEntry.file(name, content, mode, time));
            } finally {
                content.invalidate();
            }
        } else {
            throw new IOException("Unsupported tar entry type for " + name);
        }
    }

    @Override
    public void close() throws IOException {}

    @Override
    public boolean isDirectory() {
        return true;
    }

    private static final class SequentialEntryContent implements EntryContent {

        private final InputStream inputStream;
        private final long size;
        private boolean active = true;
        private boolean opened;

        private SequentialEntryContent(InputStream inputStream, long size) {
            this.inputStream = inputStream;
            this.size = size;
        }

        @Override
        public InputStream open() throws IOException {
            ensureActive();
            if (opened) {
                throw new IOException("Tar entry content can only be opened once");
            }
            opened = true;
            return new FilterInputStream(CloseShieldInputStream.wrap(inputStream)) {
                @Override
                public int read() throws IOException {
                    ensureActive();
                    return super.read();
                }

                @Override
                public int read(byte[] bytes, int offset, int length) throws IOException {
                    ensureActive();
                    return super.read(bytes, offset, length);
                }
            };
        }

        @Override
        public long size() {
            return size;
        }

        private void invalidate() {
            active = false;
        }

        private void ensureActive() throws IOException {
            if (!active) {
                throw new IOException("Tar entry content is no longer active");
            }
        }
    }
}
