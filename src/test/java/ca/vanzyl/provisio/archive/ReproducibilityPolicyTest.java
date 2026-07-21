package ca.vanzyl.provisio.archive;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import java.util.TimeZone;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.junit.Test;

public class ReproducibilityPolicyTest extends FileSystemAssert {

    @Test
    public void normalizedTarGzIsByteIdenticalAcrossTimeZonesAndSourceMetadata() throws Exception {
        File first = getTargetArchive("reproducible-first.tar.gz");
        File second = getTargetArchive("reproducible-second.tar.gz");

        createNormalized(first, "UTC", source(1_700_000_000_000L, 0600, 0700));
        createNormalized(second, "Pacific/Auckland", source(1_800_000_000_000L, 0666, 0777));

        assertArrayEquals(Files.readAllBytes(first.toPath()), Files.readAllBytes(second.toPath()));
        byte[] gzip = Files.readAllBytes(first.toPath());
        assertEquals(0, gzip[4]);
        assertEquals(0, gzip[5]);
        assertEquals(0, gzip[6]);
        assertEquals(0, gzip[7]);
        assertEquals(255, gzip[9] & 0xff);
    }

    @Test
    public void normalizedZipIsByteIdenticalAcrossTimeZonesAndSourceMetadata() throws Exception {
        File first = getTargetArchive("reproducible-first.zip");
        File second = getTargetArchive("reproducible-second.zip");

        createNormalized(first, "UTC", source(1_700_000_000_000L, 0600, 0700));
        createNormalized(second, "America/Los_Angeles", source(1_800_000_000_000L, 0666, 0777));

        assertArrayEquals(Files.readAllBytes(first.toPath()), Files.readAllBytes(second.toPath()));
    }

    @Test
    public void normalizedTarGzIsByteIdenticalAcrossCompressionWorkerCounts() throws Exception {
        byte[] content = new byte[8 * 1024 * 1024 + 1024];
        new Random(24680).nextBytes(content);
        File serial = getTargetArchive("reproducible-serial.tar.gz");
        File parallel = getTargetArchive("reproducible-parallel.tar.gz");

        createNormalized(serial, content, 1);
        createNormalized(parallel, content, 4);

        assertArrayEquals(Files.readAllBytes(serial.toPath()), Files.readAllBytes(parallel.toPath()));
        new TarGzArchiveValidator(parallel).assertSizeOfEntryInArchive("large.bin", content.length);
    }

    @Test
    public void normalizedSourceOrderedDirectoryIsByteIdenticalAcrossCreationOrderAndMetadata() throws Exception {
        Path firstRoot =
                getOutputDirectory("reproducible-source-first").toPath().resolve("root");
        Path secondRoot =
                getOutputDirectory("reproducible-source-second").toPath().resolve("root");
        createDirectorySource(firstRoot, Arrays.asList("z.txt", "a.txt"), 1_700_000_000_000L);
        createDirectorySource(secondRoot, Arrays.asList("a.txt", "z.txt"), 1_800_000_000_000L);
        File first = getTargetArchive("reproducible-source-first.tar.gz");
        File second = getTargetArchive("reproducible-source-second.tar.gz");

        createNormalizedSourceOrdered(first, Sources.directory(firstRoot));
        createNormalizedSourceOrdered(second, Sources.directory(secondRoot));

        assertArrayEquals(Files.readAllBytes(first.toPath()), Files.readAllBytes(second.toPath()));
    }

    @Test
    public void normalizedSourceOrderedZipIsByteIdenticalAcrossInputEntryOrder() throws Exception {
        Path firstInput = getTargetArchive("reproducible-source-first.zip").toPath();
        Path secondInput = getTargetArchive("reproducible-source-second.zip").toPath();
        createZip(firstInput, "z/library.jar", "a/library.jar");
        createZip(secondInput, "a/library.jar", "z/library.jar");
        File first = getTargetArchive("reproducible-zip-source-first.tar.gz");
        File second = getTargetArchive("reproducible-zip-source-second.tar.gz");

        createNormalizedMetadataSourceOrdered(first, Sources.zip(firstInput));
        createNormalizedMetadataSourceOrdered(second, Sources.zip(secondInput));

        assertArrayEquals(Files.readAllBytes(first.toPath()), Files.readAllBytes(second.toPath()));
    }

    @Test
    public void normalizedTarMetadataUsesCanonicalModesAndOwnership() throws Exception {
        File archive = getTargetArchive("reproducible-metadata.tar.gz");

        createNormalized(archive, "UTC", source(1_700_000_000_000L, 0600, 0700));

        Map<String, TarArchiveEntry> entries = tarEntries(archive);
        assertEquals(0755, entries.get("root/").getMode());
        assertEquals(0644, entries.get("root/App.class").getMode());
        assertEquals(0755, entries.get("root/run").getMode());
        for (TarArchiveEntry entry : entries.values()) {
            assertEquals(0, entry.getLongUserId());
            assertEquals(0, entry.getLongGroupId());
            assertEquals("", entry.getUserName());
            assertEquals("", entry.getGroupName());
        }
    }

    @Test
    public void preservePolicyCarriesSourceTimestampAndMode() throws Exception {
        File archive = getTargetArchive("preserved-metadata.tar.gz");
        long timestamp = 1_700_000_000_000L;
        Source source = singleEntrySource(
                SourceEntry.file("entry", EntryContents.of("entry".getBytes(StandardCharsets.UTF_8)), 0600, timestamp));

        Archiver.builder()
                .reproducibility(ReproducibilityPolicy.PRESERVE)
                .build()
                .archive(archive.toPath(), source);

        TarArchiveEntry entry = tarEntries(archive).get("entry");
        assertEquals(0600, entry.getMode());
        assertEquals(timestamp, entry.getModTime().getTime());
    }

    @Test
    public void nullReproducibilityPolicyIsRejected() {
        try {
            Archiver.builder().reproducibility(null);
            fail("Expected null reproducibility policy to fail");
        } catch (NullPointerException expected) {
            // Expected.
        }
    }

    private void createNormalized(File archive, String timeZone, Source source) throws Exception {
        TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone(timeZone));
            Archiver.builder()
                    .reproducibility(ReproducibilityPolicy.NORMALIZED)
                    .entryOrder(EntryOrder.NAME)
                    .build()
                    .archive(archive.toPath(), source);
        } finally {
            TimeZone.setDefault(original);
        }
    }

    private void createNormalized(File archive, byte[] content, int compressionThreads) throws Exception {
        Archiver.builder()
                .reproducibility(ReproducibilityPolicy.NORMALIZED)
                .entryOrder(EntryOrder.NAME)
                .gzipCompressionThreads(compressionThreads)
                .build()
                .archive(
                        archive.toPath(),
                        singleEntrySource(SourceEntry.file("large.bin", EntryContents.of(content), 0644, 0)));
    }

    private void createNormalizedSourceOrdered(File archive, Source source) throws Exception {
        Archiver.builder()
                .reproducibility(ReproducibilityPolicy.NORMALIZED)
                .entryOrder(EntryOrder.SOURCE)
                .build()
                .archive(archive.toPath(), source);
    }

    private void createNormalizedMetadataSourceOrdered(File archive, Source source) throws Exception {
        Archiver.builder()
                .reproducibility(ReproducibilityPolicy.NORMALIZED)
                .entryOrder(EntryOrder.SOURCE)
                .contentIdentity(ContentIdentityMode.SIZE_AND_CRC32)
                .hardLinkIncludes("**/*.jar")
                .build()
                .archive(archive.toPath(), source);
    }

    private void createDirectorySource(Path root, Iterable<String> creationOrder, long timestamp) throws Exception {
        Files.createDirectories(root);
        for (String name : creationOrder) {
            Path file = root.resolve(name);
            Files.write(file, name.getBytes(StandardCharsets.UTF_8));
            Files.setLastModifiedTime(file, FileTime.fromMillis(timestamp));
        }
        Files.setLastModifiedTime(root, FileTime.fromMillis(timestamp));
    }

    private void createZip(Path archive, String first, String second) throws Exception {
        Source source = source(zipEntry(first), zipEntry(second));
        Archiver.builder().entryOrder(EntryOrder.SOURCE).build().archive(archive, source);
    }

    private SourceEntry zipEntry(String name) {
        boolean firstName = name.startsWith("a/");
        return SourceEntry.file(
                name,
                EntryContents.of("same".getBytes(StandardCharsets.UTF_8)),
                firstName ? 0600 : 0700,
                firstName ? 1 : 2);
    }

    private Source source(long timestamp, int regularMode, int executableMode) {
        return new Source() {
            @Override
            public void forEachEntry(EntryConsumer consumer) throws IOException {
                consumer.accept(SourceEntry.file(
                        "root/run",
                        EntryContents.of("run".getBytes(StandardCharsets.UTF_8)),
                        executableMode,
                        timestamp));
                consumer.accept(SourceEntry.directory("root", executableMode, timestamp));
                consumer.accept(SourceEntry.file(
                        "root/App.class",
                        EntryContents.of("class".getBytes(StandardCharsets.UTF_8)),
                        regularMode,
                        timestamp));
            }

            @Override
            public boolean isDirectory() {
                return false;
            }

            @Override
            public void close() throws IOException {}
        };
    }

    private Source singleEntrySource(SourceEntry entry) {
        return source(entry);
    }

    private Source source(SourceEntry... entries) {
        return new Source() {
            @Override
            public void forEachEntry(EntryConsumer consumer) throws IOException {
                for (SourceEntry entry : entries) {
                    consumer.accept(entry);
                }
            }

            @Override
            public boolean isDirectory() {
                return false;
            }

            @Override
            public void close() throws IOException {}
        };
    }

    private Map<String, TarArchiveEntry> tarEntries(File archive) throws IOException {
        Map<String, TarArchiveEntry> entries = new LinkedHashMap<>();
        try (TarArchiveInputStream input =
                new TarArchiveInputStream(new GzipCompressorInputStream(Files.newInputStream(archive.toPath())))) {
            TarArchiveEntry entry;
            while ((entry = input.getNextTarEntry()) != null) {
                entries.put(entry.getName(), entry);
            }
        }
        return entries;
    }
}
