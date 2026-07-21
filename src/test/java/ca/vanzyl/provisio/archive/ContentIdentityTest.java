package ca.vanzyl.provisio.archive;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

import ca.vanzyl.provisio.archive.tar.TarGzArchiveSource;
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

        hardLinkingArchiver(EntryOrder.SOURCE).archive(archive, source);

        assertEquals(EntryType.FILE, entries(archive).get("one/library.jar").getType());
        assertEquals(EntryType.FILE, entries(archive).get("two/library.jar").getType());
        ArchiveValidator validator = new TarGzArchiveValidator(archive);
        validator.assertContentOfEntryInArchive("one/library.jar", "alpha");
        validator.assertContentOfEntryInArchive("two/library.jar", "bravo");
    }

    @Test
    public void differentFileNamesWithIdenticalContentAreLinked() throws Exception {
        File archive = getTargetArchive("identity-different-name-same-content.tar.gz");
        Source source = source(file("one/a.jar", "same"), file("two/b.jar", "same"));

        hardLinkingArchiver(EntryOrder.SOURCE).archive(archive, source);

        Map<String, SourceEntry> entries = entries(archive);
        assertEquals(EntryType.FILE, entries.get("one/a.jar").getType());
        assertEquals(EntryType.HARD_LINK, entries.get("two/b.jar").getType());
        assertEquals("one/a.jar", entries.get("two/b.jar").getLinkTarget());
    }

    @Test
    public void nameOrderChoosesAnEarlierOutputEntryAsTheTarget() throws Exception {
        File archive = getTargetArchive("identity-name-order-target.tar.gz");
        Source source = source(file("z/z.jar", "same"), file("a/a.jar", "same"));

        hardLinkingArchiver(EntryOrder.NAME).archive(archive, source);

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

        hardLinkingArchiver(EntryOrder.SOURCE).archive(archive, source);

        assertEquals(1, first.openCount);
        assertEquals(1, second.openCount);
        assertEquals(EntryType.HARD_LINK, entries(archive).get("two/b.jar").getType());
    }

    @Test
    public void emptyFilesCanShareAContentIdentity() throws Exception {
        File archive = getTargetArchive("identity-empty-files.tar.gz");
        Source source = source(file("one/a.jar", ""), file("two/b.jar", ""));

        hardLinkingArchiver(EntryOrder.SOURCE).archive(archive, source);

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
                    .archive(archive, source(SourceEntry.file("bad.jar", badSize, 0644, 0)));
            fail("Expected mismatched content size to fail");
        } catch (IOException expected) {
            // The writer or identity validation must reject the mismatch.
        }
        assertFalse(archive.exists());
    }

    private Archiver hardLinkingArchiver(EntryOrder order) {
        return Archiver.builder().entryOrder(order).hardLinkIncludes("**/*.jar").build();
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
        try (Source source = new TarGzArchiveSource(archive)) {
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
}
