package ca.vanzyl.provisio.archive;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import ca.vanzyl.provisio.archive.tar.TarGzArchiveSource;
import ca.vanzyl.provisio.archive.zip.ZipArchiveSource;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import java.util.zip.CRC32;
import org.junit.Test;

public class ArchiveScaleTest extends FileSystemAssert {

    private static final int LARGE_ENTRY_SIZE = 16 * 1024 * 1024 + 123;
    private static final int MANY_ENTRY_COUNT = 1_500;

    @Test
    public void sourceOrderStreamsLargeContentWithoutEntrySpools() throws Exception {
        Path output = getTargetArchive("scale-large-source-order.tar.gz").toPath();
        CountingLargeSource source = new CountingLargeSource(output.getParent(), LARGE_ENTRY_SIZE);

        Archiver.builder()
                .entryOrder(EntryOrder.SOURCE)
                .gzipCompressionThreads(2)
                .gzipCompressionLevel(1)
                .build()
                .archive(output, source);

        assertEquals(1, source.openCount);
        assertEquals(1, source.closeCount);
        assertEquals(0, spoolFiles(output.getParent()));
        long[] size = {0};
        long[] crc32 = {0};
        try (Source archive = new TarGzArchiveSource(output)) {
            archive.forEachEntry(entry -> {
                if (entry.getType() == EntryType.FILE) {
                    try (InputStream input = entry.getContent().open()) {
                        CRC32 checksum = new CRC32();
                        byte[] buffer = new byte[8192];
                        int count;
                        while ((count = input.read(buffer)) != -1) {
                            checksum.update(buffer, 0, count);
                            size[0] += count;
                        }
                        crc32[0] = checksum.getValue();
                    }
                }
            });
        }
        assertEquals(LARGE_ENTRY_SIZE, size[0]);
        assertEquals(checksum(new DeterministicInputStream(LARGE_ENTRY_SIZE)), crc32[0]);
    }

    @Test
    public void nameOrderUsesSequentialSourceHandlesAndCleansManySpools() throws Exception {
        Path output = getTargetArchive("scale-many-name-order.zip").toPath();
        ManyEntrySource source = new ManyEntrySource(output.getParent(), MANY_ENTRY_COUNT);

        Archiver.builder().entryOrder(EntryOrder.NAME).build().archive(output, source);

        assertEquals(MANY_ENTRY_COUNT, source.openCount);
        assertEquals(MANY_ENTRY_COUNT, source.closeCount);
        assertEquals(1, source.maximumOpenStreams);
        assertEquals(MANY_ENTRY_COUNT, source.spoolsAtTraversalEnd);
        assertEquals(0, spoolFiles(output.getParent()));

        int[] files = {0};
        try (Source archive = new ZipArchiveSource(output)) {
            archive.forEachEntry(entry -> {
                if (entry.getType() == EntryType.FILE) {
                    files[0]++;
                }
            });
        }
        assertEquals(MANY_ENTRY_COUNT, files[0]);
    }

    private static long checksum(InputStream input) throws IOException {
        try (InputStream stream = input) {
            CRC32 checksum = new CRC32();
            byte[] buffer = new byte[8192];
            int count;
            while ((count = stream.read(buffer)) != -1) {
                checksum.update(buffer, 0, count);
            }
            return checksum.getValue();
        }
    }

    private static long spoolFiles(Path directory) throws IOException {
        try (Stream<Path> files = Files.list(directory)) {
            return files.filter(path -> path.getFileName().toString().startsWith(".provisio-entry-"))
                    .count();
        }
    }

    private static final class CountingLargeSource implements Source {

        private final Path spoolDirectory;
        private final int size;
        private int openCount;
        private int closeCount;

        private CountingLargeSource(Path spoolDirectory, int size) {
            this.spoolDirectory = spoolDirectory;
            this.size = size;
        }

        @Override
        public void forEachEntry(EntryConsumer consumer) throws IOException {
            assertEquals(0, spoolFiles(spoolDirectory));
            consumer.accept(SourceEntry.file(
                    "large.bin",
                    new EntryContent() {
                        @Override
                        public InputStream open() {
                            openCount++;
                            return new FilterInputStream(new DeterministicInputStream(size)) {
                                @Override
                                public void close() throws IOException {
                                    closeCount++;
                                    super.close();
                                }
                            };
                        }

                        @Override
                        public long size() {
                            return size;
                        }
                    },
                    0644,
                    0));
            assertEquals(0, spoolFiles(spoolDirectory));
        }

        @Override
        public boolean isDirectory() {
            return false;
        }

        @Override
        public void close() {}
    }

    private static final class ManyEntrySource implements Source {

        private final Path spoolDirectory;
        private final int entryCount;
        private int openStreams;
        private int maximumOpenStreams;
        private int openCount;
        private int closeCount;
        private long spoolsAtTraversalEnd;

        private ManyEntrySource(Path spoolDirectory, int entryCount) {
            this.spoolDirectory = spoolDirectory;
            this.entryCount = entryCount;
        }

        @Override
        public void forEachEntry(EntryConsumer consumer) throws IOException {
            for (int i = 0; i < entryCount; i++) {
                final int entry = i;
                consumer.accept(SourceEntry.file(
                        String.format("group-%02d/entry-%04d.bin", i % 16, i),
                        new EntryContent() {
                            @Override
                            public InputStream open() {
                                openCount++;
                                openStreams++;
                                maximumOpenStreams = Math.max(maximumOpenStreams, openStreams);
                                byte[] content = new byte[256];
                                for (int index = 0; index < content.length; index++) {
                                    content[index] = (byte) (entry * 31 + index);
                                }
                                return new FilterInputStream(new java.io.ByteArrayInputStream(content)) {
                                    private boolean closed;

                                    @Override
                                    public void close() throws IOException {
                                        if (!closed) {
                                            closed = true;
                                            closeCount++;
                                            openStreams--;
                                        }
                                        super.close();
                                    }
                                };
                            }

                            @Override
                            public long size() {
                                return 256;
                            }
                        },
                        0644,
                        0));
                assertEquals("A source stream escaped its callback", 0, openStreams);
            }
            spoolsAtTraversalEnd = spoolFiles(spoolDirectory);
        }

        @Override
        public boolean isDirectory() {
            return false;
        }

        @Override
        public void close() {
            assertTrue("Source closed with live content streams", openStreams == 0);
        }
    }

    private static final class DeterministicInputStream extends InputStream {

        private int remaining;
        private int state = 0x13579bdf;

        private DeterministicInputStream(int size) {
            remaining = size;
        }

        @Override
        public int read() {
            if (remaining == 0) {
                return -1;
            }
            remaining--;
            state = state * 1103515245 + 12345;
            return (state >>> 16) & 0xff;
        }

        @Override
        public int read(byte[] bytes, int offset, int length) {
            if (remaining == 0) {
                return -1;
            }
            int count = Math.min(length, remaining);
            for (int i = 0; i < count; i++) {
                bytes[offset + i] = (byte) read();
            }
            return count;
        }
    }
}
