package ca.vanzyl.provisio.archive;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import ca.vanzyl.provisio.archive.tar.TarGzArchiveSource;
import ca.vanzyl.provisio.archive.zip.ZipArchiveSource;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.stream.Stream;
import java.util.zip.CRC32;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.archivers.tar.TarConstants;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.junit.Test;

public class ArchiveInputFailureTest extends FileSystemAssert {

    @Test
    public void truncatedZipDirectoryIsRejectedTransactionally() throws Exception {
        File source = getTargetArchive("negative-valid.zip");
        writeStoredZip(source, bytes("valid zip content"));
        byte[] valid = Files.readAllBytes(source.toPath());
        File truncated = getTargetArchive("negative-truncated.zip");
        Files.write(truncated.toPath(), Arrays.copyOf(valid, valid.length - 12));

        assertRejectedTransactionally(new ZipArchiveSource(truncated.toPath()), "negative-from-truncated-zip.tar.gz");
    }

    @Test
    public void corruptedZipEntryDataIsRejectedTransactionally() throws Exception {
        File source = getTargetArchive("negative-corrupt-data.zip");
        writeStoredZip(source, bytes("content protected by the central directory CRC"));
        byte[] corrupt = Files.readAllBytes(source.toPath());
        int dataOffset = 30 + littleEndian16(corrupt, 26) + littleEndian16(corrupt, 28);
        corrupt[dataOffset + 3] ^= 0x40;
        Files.write(source.toPath(), corrupt);

        IOException failure = assertRejectedTransactionally(
                new ZipArchiveSource(source.toPath()), "negative-from-corrupt-zip.tar.gz");
        assertTrue(failure.getMessage().contains("CRC"));
    }

    @Test
    public void corruptedGzipTrailerIsRejectedTransactionally() throws Exception {
        File source = getTargetArchive("negative-valid.tar.gz");
        Archiver.builder().build().archive(source.toPath(), new StringListSource(Arrays.asList("one", "two")));
        byte[] corrupt = Files.readAllBytes(source.toPath());
        corrupt[corrupt.length - 8] ^= 0x01;
        File corrupted = getTargetArchive("negative-corrupt-gzip-trailer.tar.gz");
        Files.write(corrupted.toPath(), corrupt);

        assertRejectedTransactionally(
                new TarGzArchiveSource(corrupted.toPath()), "negative-from-corrupt-gzip-trailer.tar.gz");
    }

    @Test
    public void corruptedTarHeaderIsRejectedTransactionally() throws Exception {
        byte[] tar = rawTar(TarConstants.LF_NORMAL, bytes("tar content"));
        tar[0] ^= 0x01;
        File source = getTargetArchive("negative-corrupt-tar-header.tar.gz");
        Files.write(source.toPath(), gzip(tar));

        assertRejectedTransactionally(
                new TarGzArchiveSource(source.toPath()), "negative-from-corrupt-tar-header.tar.gz");
    }

    @Test
    public void unsupportedTarEntryTypeIsRejectedTransactionally() throws Exception {
        File source = getTargetArchive("negative-unsupported-tar-entry.tar.gz");
        Files.write(source.toPath(), gzip(rawTar(TarConstants.LF_FIFO, new byte[0])));

        IOException failure = assertRejectedTransactionally(
                new TarGzArchiveSource(source.toPath()), "negative-from-unsupported-tar-entry.tar.gz");
        assertEquals("Unsupported tar entry type for entry", failure.getMessage());
    }

    @Test
    public void contentReadFailureRemainsPrimaryAndAllResourcesAreClosed() throws Exception {
        for (EntryOrder order : EntryOrder.values()) {
            IOException readFailure = new IOException("content read failed");
            IOException contentCloseFailure = new IOException("content close failed");
            IOException sourceCloseFailure = new IOException("source close failed");
            FailingContentSource source =
                    new FailingContentSource(readFailure, contentCloseFailure, sourceCloseFailure);
            File output =
                    getTargetArchive("negative-content-failure-" + order.name().toLowerCase() + ".tar.gz");
            byte[] original = bytes("existing output for " + order);
            Files.write(output.toPath(), original);

            try {
                Archiver.builder().entryOrder(order).build().archive(output.toPath(), source);
                fail("Expected content read failure for " + order);
            } catch (IOException expected) {
                assertSame(readFailure, expected);
                assertTrue(containsThrowable(expected, contentCloseFailure));
                assertTrue(containsThrowable(expected, sourceCloseFailure));
            }

            assertEquals(1, source.closeCount);
            assertArrayEquals(original, Files.readAllBytes(output.toPath()));
            assertNoTemporaryFiles(output.toPath());
        }
    }

    private IOException assertRejectedTransactionally(Source source, String outputName) throws Exception {
        File output = getTargetArchive(outputName);
        byte[] original = bytes("existing destination " + outputName);
        Files.write(output.toPath(), original);
        try {
            Archiver.builder().entryOrder(EntryOrder.NAME).build().archive(output.toPath(), source);
            fail("Expected corrupt input to be rejected: " + outputName);
            throw new AssertionError();
        } catch (IOException expected) {
            assertArrayEquals(original, Files.readAllBytes(output.toPath()));
            assertNoTemporaryFiles(output.toPath());
            return expected;
        }
    }

    private void assertNoTemporaryFiles(Path archive) throws IOException {
        String archivePrefix = ".provisio-" + archive.getFileName() + "-";
        try (Stream<Path> files = Files.list(archive.toAbsolutePath().getParent())) {
            assertEquals(
                    0,
                    files.filter(path -> {
                                String name = path.getFileName().toString();
                                return name.startsWith(archivePrefix) || name.startsWith(".provisio-entry-");
                            })
                            .count());
        }
    }

    private void writeStoredZip(File archive, byte[] content) throws IOException {
        CRC32 crc32 = new CRC32();
        crc32.update(content);
        try (ZipArchiveOutputStream output = new ZipArchiveOutputStream(Files.newOutputStream(archive.toPath()))) {
            ZipArchiveEntry entry = new ZipArchiveEntry("entry");
            entry.setMethod(ZipArchiveEntry.STORED);
            entry.setSize(content.length);
            entry.setCompressedSize(content.length);
            entry.setCrc(crc32.getValue());
            output.putArchiveEntry(entry);
            output.write(content);
            output.closeArchiveEntry();
        }
    }

    private byte[] rawTar(byte type, byte[] content) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (TarArchiveOutputStream output = new TarArchiveOutputStream(bytes)) {
            TarArchiveEntry entry = new TarArchiveEntry("entry", type);
            entry.setSize(content.length);
            output.putArchiveEntry(entry);
            output.write(content);
            output.closeArchiveEntry();
        }
        return bytes.toByteArray();
    }

    private byte[] gzip(byte[] content) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (GzipCompressorOutputStream output = new GzipCompressorOutputStream(bytes)) {
            output.write(content);
        }
        return bytes.toByteArray();
    }

    private int littleEndian16(byte[] bytes, int offset) {
        return (bytes[offset] & 0xff) | ((bytes[offset + 1] & 0xff) << 8);
    }

    private boolean containsThrowable(Throwable root, Throwable expected) {
        if (root == expected) {
            return true;
        }
        if (root.getCause() != null && containsThrowable(root.getCause(), expected)) {
            return true;
        }
        for (Throwable suppressed : root.getSuppressed()) {
            if (containsThrowable(suppressed, expected)) {
                return true;
            }
        }
        return false;
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static final class FailingContentSource implements Source {

        private final IOException readFailure;
        private final IOException contentCloseFailure;
        private final IOException sourceCloseFailure;
        private int closeCount;

        private FailingContentSource(
                IOException readFailure, IOException contentCloseFailure, IOException sourceCloseFailure) {
            this.readFailure = readFailure;
            this.contentCloseFailure = contentCloseFailure;
            this.sourceCloseFailure = sourceCloseFailure;
        }

        @Override
        public void forEachEntry(EntryConsumer consumer) throws IOException {
            consumer.accept(SourceEntry.file("entry", new FailingContent(), 0644, 0));
        }

        @Override
        public boolean isDirectory() {
            return false;
        }

        @Override
        public void close() throws IOException {
            closeCount++;
            throw sourceCloseFailure;
        }

        private final class FailingContent implements EntryContent {

            @Override
            public InputStream open() {
                return new InputStream() {
                    private final ByteArrayInputStream prefix = new ByteArrayInputStream(bytes("partial"));

                    @Override
                    public int read() throws IOException {
                        int value = prefix.read();
                        if (value != -1) {
                            return value;
                        }
                        throw readFailure;
                    }

                    @Override
                    public int read(byte[] bytes, int offset, int length) throws IOException {
                        int count = prefix.read(bytes, offset, length);
                        if (count != -1) {
                            return count;
                        }
                        throw readFailure;
                    }

                    @Override
                    public void close() throws IOException {
                        throw contentCloseFailure;
                    }
                };
            }

            @Override
            public long size() {
                return 32;
            }
        }
    }
}
