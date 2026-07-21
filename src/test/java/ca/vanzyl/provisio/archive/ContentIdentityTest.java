package ca.vanzyl.provisio.archive;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class ContentIdentityTest extends FileSystemAssert {

    @Test
    public void sameFileNameWithDifferentContentIsNotLinked() throws Exception {
        File archive = getTargetArchive("identity-same-name-different-content.tar.gz");
        Source source = source(file("one/library.jar", "alpha"), file("two/library.jar", "bravo"));

        hardLinkingArchiver(EntryOrder.SOURCE).archive(archive.toPath(), source);

        assertEquals(EntryType.FILE, entries(archive).get("one/library.jar").getType());
        assertEquals(EntryType.FILE, entries(archive).get("two/library.jar").getType());
        ArchiveValidator validator = new TarGzArchiveValidator(archive);
        validator.assertContentOfEntryInArchive("one/library.jar", "alpha");
        validator.assertContentOfEntryInArchive("two/library.jar", "bravo");
    }

    @Test
    public void verifiedIdentityRejectsMatchingCrcMetadataForDifferentBytes() throws Exception {
        File archive = getTargetArchive("identity-crc-collision.tar.gz");
        TrackingMetadataContent first = new TrackingMetadataContent("alpha", 1234, false);
        TrackingMetadataContent second = new TrackingMetadataContent("bravo", 1234, false);
        Source source =
                source(SourceEntry.file("one/a.jar", first, 0644, 0), SourceEntry.file("two/b.jar", second, 0644, 0));

        hardLinkingArchiver(EntryOrder.SOURCE).archive(archive.toPath(), source);

        assertEquals(EntryType.FILE, entries(archive).get("one/a.jar").getType());
        assertEquals(EntryType.FILE, entries(archive).get("two/b.jar").getType());
        assertEquals(1, first.openCount);
        assertEquals(1, second.openCount);
    }

    @Test
    public void metadataIdentityAvoidsOpeningSourceOrderedDuplicateContent() throws Exception {
        File archive = getTargetArchive("identity-metadata-source-order.tar.gz");
        TrackingMetadataContent first = new TrackingMetadataContent("same", 77, false);
        TrackingMetadataContent duplicate = new TrackingMetadataContent("xxxx", 77, true);
        Source source = source(
                SourceEntry.file("one/a.jar", first, 0644, 0), SourceEntry.file("two/b.jar", duplicate, 0644, 0));

        metadataHardLinkingArchiver(EntryOrder.SOURCE).archive(archive.toPath(), source);

        assertEquals(1, first.openCount);
        assertEquals(0, duplicate.openCount);
        assertEquals(EntryType.HARD_LINK, entries(archive).get("two/b.jar").getType());
    }

    @Test
    public void metadataIdentitySpoolsOneRepresentativeForNameOrder() throws Exception {
        File archive = getTargetArchive("identity-metadata-name-order.tar.gz");
        TrackingMetadataContent representative = new TrackingMetadataContent("same", 77, false);
        TrackingMetadataContent duplicate = new TrackingMetadataContent("xxxx", 77, true);
        Source source = source(
                SourceEntry.file("z/z.jar", representative, 0644, 0), SourceEntry.file("a/a.jar", duplicate, 0644, 0));

        metadataHardLinkingArchiver(EntryOrder.NAME).archive(archive.toPath(), source);

        assertEquals(1, representative.openCount);
        assertEquals(0, duplicate.openCount);
        Map<String, SourceEntry> entries = entries(archive);
        assertEquals(EntryType.FILE, entries.get("a/a.jar").getType());
        assertEquals(EntryType.HARD_LINK, entries.get("z/z.jar").getType());
        assertEquals("a/a.jar", entries.get("z/z.jar").getLinkTarget());
        new TarGzArchiveValidator(archive).assertContentOfEntryInArchive("a/a.jar", "same");
    }

    @Test
    public void zipCentralDirectoryMetadataCanDriveTarHardLinks() throws Exception {
        File sourceArchive = getTargetArchive("identity-metadata-source.zip");
        Archiver.builder()
                .build()
                .archive(sourceArchive.toPath(), source(file("z/z.jar", "same"), file("a/a.jar", "same")));
        File outputArchive = getTargetArchive("identity-metadata-from-zip.tar.gz");

        metadataHardLinkingArchiver(EntryOrder.NAME)
                .archive(outputArchive.toPath(), Sources.zip(sourceArchive.toPath()));

        Map<String, SourceEntry> entries = entries(outputArchive);
        assertEquals(EntryType.FILE, entries.get("a/a.jar").getType());
        assertEquals(EntryType.HARD_LINK, entries.get("z/z.jar").getType());
        assertEquals("a/a.jar", entries.get("z/z.jar").getLinkTarget());
    }

    @Test
    public void missingMetadataFallsBackToVerifiedIdentity() throws Exception {
        File archive = getTargetArchive("identity-metadata-missing.tar.gz");
        TrackingMetadataContent first = new TrackingMetadataContent("same", -1, false);
        TrackingMetadataContent second = new TrackingMetadataContent("same", -1, false);
        Source source =
                source(SourceEntry.file("one/a.jar", first, 0644, 0), SourceEntry.file("two/b.jar", second, 0644, 0));

        metadataHardLinkingArchiver(EntryOrder.SOURCE).archive(archive.toPath(), source);

        assertEquals(1, first.openCount);
        assertEquals(1, second.openCount);
        assertEquals(EntryType.HARD_LINK, entries(archive).get("two/b.jar").getType());
    }

    @Test
    public void invalidCrcMetadataFallsBackToVerifiedIdentity() throws Exception {
        File archive = getTargetArchive("identity-metadata-invalid.tar.gz");
        long invalidCrc = 0x1_0000_0000L;
        TrackingMetadataContent first = new TrackingMetadataContent("same", invalidCrc, false);
        TrackingMetadataContent second = new TrackingMetadataContent("same", invalidCrc, false);

        metadataHardLinkingArchiver(EntryOrder.SOURCE)
                .archive(
                        archive.toPath(),
                        source(
                                SourceEntry.file("one/a.jar", first, 0644, 0),
                                SourceEntry.file("two/b.jar", second, 0644, 0)));

        assertEquals(1, second.openCount);
        assertEquals(EntryType.HARD_LINK, entries(archive).get("two/b.jar").getType());
    }

    @Test
    public void nullContentIdentityModeIsRejected() {
        try {
            Archiver.builder().contentIdentity(null);
            fail("Expected null content identity mode to fail");
        } catch (NullPointerException expected) {
            // Expected.
        }
    }

    @Test
    public void differentFileNamesWithIdenticalContentAreLinked() throws Exception {
        File archive = getTargetArchive("identity-different-name-same-content.tar.gz");
        Source source = source(file("one/a.jar", "same"), file("two/b.jar", "same"));

        hardLinkingArchiver(EntryOrder.SOURCE).archive(archive.toPath(), source);

        Map<String, SourceEntry> entries = entries(archive);
        assertEquals(EntryType.FILE, entries.get("one/a.jar").getType());
        assertEquals(EntryType.HARD_LINK, entries.get("two/b.jar").getType());
        assertEquals("one/a.jar", entries.get("two/b.jar").getLinkTarget());
    }

    @Test
    public void nameOrderChoosesAnEarlierOutputEntryAsTheTarget() throws Exception {
        File archive = getTargetArchive("identity-name-order-target.tar.gz");
        Source source = source(file("z/z.jar", "same"), file("a/a.jar", "same"));

        hardLinkingArchiver(EntryOrder.NAME).archive(archive.toPath(), source);

        List<SourceEntry> entries = entryList(archive);
        assertEquals(
                Arrays.asList("a/", "a/a.jar", "z/", "z/z.jar"),
                Arrays.asList(
                        entries.get(0).getName(),
                        entries.get(1).getName(),
                        entries.get(2).getName(),
                        entries.get(3).getName()));
        assertEquals(EntryType.FILE, entries.get(1).getType());
        assertEquals(EntryType.HARD_LINK, entries.get(3).getType());
        assertEquals("a/a.jar", entries.get(3).getLinkTarget());
    }

    @Test
    public void exactIdentityConsumesSingleUseCandidatesOnlyOnce() throws Exception {
        File archive = getTargetArchive("identity-single-use.tar.gz");
        SingleUseContent first = new SingleUseContent("same");
        SingleUseContent second = new SingleUseContent("same");
        Source source =
                source(SourceEntry.file("one/a.jar", first, 0644, 0), SourceEntry.file("two/b.jar", second, 0644, 0));

        hardLinkingArchiver(EntryOrder.SOURCE).archive(archive.toPath(), source);

        assertEquals(1, first.openCount);
        assertEquals(1, second.openCount);
        assertEquals(EntryType.HARD_LINK, entries(archive).get("two/b.jar").getType());
    }

    @Test
    public void emptyFilesCanShareAContentIdentity() throws Exception {
        File archive = getTargetArchive("identity-empty-files.tar.gz");
        Source source = source(file("one/a.jar", ""), file("two/b.jar", ""));

        hardLinkingArchiver(EntryOrder.SOURCE).archive(archive.toPath(), source);

        assertEquals(EntryType.FILE, entries(archive).get("one/a.jar").getType());
        assertEquals(EntryType.HARD_LINK, entries(archive).get("two/b.jar").getType());
    }

    @Test
    public void mismatchedContentSizeFailsTransactionally() throws Exception {
        File archive = getTargetArchive("identity-size-mismatch.tar.gz");
        Files.deleteIfExists(archive.toPath());
        EntryContent badSize = new EntryContent() {
            @Override
            public InputStream open() {
                return new ByteArrayInputStream(new byte[] {1, 2, 3});
            }

            @Override
            public long size() {
                return 4;
            }
        };

        try {
            hardLinkingArchiver(EntryOrder.SOURCE)
                    .archive(archive.toPath(), source(SourceEntry.file("bad.jar", badSize, 0644, 0)));
            fail("Expected mismatched content size to fail");
        } catch (IOException expected) {
            // The writer or identity validation must reject the mismatch.
        }
        assertFalse(archive.exists());
    }

    private Archiver hardLinkingArchiver(EntryOrder order) {
        return Archiver.builder().entryOrder(order).hardLinkIncludes("**/*.jar").build();
    }

    private Archiver metadataHardLinkingArchiver(EntryOrder order) {
        return Archiver.builder()
                .entryOrder(order)
                .contentIdentity(ContentIdentityMode.SIZE_AND_CRC32)
                .hardLinkIncludes("**/*.jar")
                .build();
    }

    private static SourceEntry file(String name, String content) {
        return SourceEntry.file(name, EntryContents.of(content.getBytes(StandardCharsets.UTF_8)), 0644, 0);
    }

    private static Source source(SourceEntry... entries) {
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

    private static Map<String, SourceEntry> entries(File archive) throws IOException {
        Map<String, SourceEntry> entries = new LinkedHashMap<>();
        for (SourceEntry entry : entryList(archive)) {
            entries.put(entry.getName(), entry);
        }
        return entries;
    }

    private static List<SourceEntry> entryList(File archive) throws IOException {
        List<SourceEntry> entries = new ArrayList<>();
        try (Source source = Sources.tarGz(archive.toPath())) {
            source.forEachEntry(entries::add);
        }
        return entries;
    }

    private static final class SingleUseContent implements EntryContent {

        private final byte[] bytes;
        private int openCount;

        private SingleUseContent(String content) {
            bytes = content.getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public InputStream open() throws IOException {
            openCount++;
            if (openCount > 1) {
                throw new IOException("single-use content opened more than once");
            }
            return new ByteArrayInputStream(bytes);
        }

        @Override
        public long size() {
            return bytes.length;
        }
    }

    private static final class TrackingMetadataContent implements EntryContent {

        private final byte[] bytes;
        private final long crc32;
        private final boolean failOnOpen;
        private int openCount;

        private TrackingMetadataContent(String content, long crc32, boolean failOnOpen) {
            bytes = content.getBytes(StandardCharsets.UTF_8);
            this.crc32 = crc32;
            this.failOnOpen = failOnOpen;
        }

        @Override
        public InputStream open() throws IOException {
            if (failOnOpen) {
                throw new IOException("duplicate content should not be opened");
            }
            openCount++;
            return new ByteArrayInputStream(bytes);
        }

        @Override
        public long size() {
            return bytes.length;
        }

        @Override
        public long crc32() {
            return crc32;
        }
    }
}
