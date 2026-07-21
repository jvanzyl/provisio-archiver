package ca.vanzyl.provisio.archive;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import ca.vanzyl.provisio.archive.tar.TarGzArchiveSource;
import ca.vanzyl.provisio.archive.zip.ZipArchiveSource;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.junit.Test;

public class ArchiveWriterTest extends FileSystemAssert {

    @Test
    public void archiveFormatDetectionRetainsAllSupportedAliases() {
        assertEquals(ArchiveFormat.TAR_GZ, ArchiveFormat.detect(Paths.get("archive.tar.gz")));
        assertEquals(ArchiveFormat.TAR_GZ, ArchiveFormat.detect(Paths.get("archive.tgz")));
        assertEquals(ArchiveFormat.ZIP, ArchiveFormat.detect(Paths.get("archive.zip")));
        assertEquals(ArchiveFormat.ZIP, ArchiveFormat.detect(Paths.get("archive.jar")));
        assertEquals(ArchiveFormat.ZIP, ArchiveFormat.detect(Paths.get("archive.war")));
        assertEquals(ArchiveFormat.ZIP, ArchiveFormat.detect(Paths.get("archive.hpi")));
        assertEquals(ArchiveFormat.ZIP, ArchiveFormat.detect(Paths.get("archive.jpi")));
    }

    @Test
    public void tarWriterPreservesAnAlreadySelectedHardLink() throws Exception {
        File archive = getTargetArchive("writer-hard-link.tar.gz");
        Archiver.builder().build().archive(archive, new LinkSource());

        int[] hardLinks = {0};
        new TarGzArchiveSource(archive).forEachEntry(entry -> {
            if (entry.isHardLink()) {
                hardLinks[0]++;
                assertEquals("target.txt", entry.getLinkTarget());
            }
        });
        assertEquals(1, hardLinks[0]);

        File output = getOutputDirectory("writer-hard-link");
        UnArchiver.builder().build().unarchive(archive, output);
        assertTrue(Files.isSameFile(
                output.toPath().resolve("target.txt"), output.toPath().resolve("link.txt")));
    }

    @Test
    public void zipWriterRejectsAHardLinkWithoutLeavingAnArchive() throws Exception {
        File archive = getTargetArchive("writer-hard-link.zip");
        Files.deleteIfExists(archive.toPath());

        try {
            Archiver.builder().build().archive(archive, new LinkSource());
            fail("Expected ZIP hard-link output to fail");
        } catch (IOException expected) {
            assertEquals("ZIP does not support hard link entry link.txt", expected.getMessage());
        }

        assertFalse(archive.exists());
    }

    @Test
    public void tarWriterPreservesSymbolicLinkMetadataFromAnArchiveSource() throws Exception {
        File archive = getTargetArchive("writer-symbolic-link.tar.gz");
        Archiver.builder().build().archive(archive, new TarGzArchiveSource(getSourceArchive("jenv.tar.gz")));

        assertSymbolicLink(new TarGzArchiveSource(archive));
    }

    @Test
    public void zipWriterPreservesSymbolicLinkMetadataFromAnArchiveSource() throws Exception {
        File archive = getTargetArchive("writer-symbolic-link.zip");
        Archiver.builder().build().archive(archive, new TarGzArchiveSource(getSourceArchive("jenv.tar.gz")));

        assertSymbolicLink(new ZipArchiveSource(archive));
    }

    @Test
    public void unsupportedOutputFormatIsRejectedTransactionally() throws Exception {
        File archive = getTargetArchive("writer-unsupported.unknown");
        Files.deleteIfExists(archive.toPath());

        try {
            Archiver.builder()
                    .build()
                    .archive(archive, new StringListSource(java.util.Collections.singletonList("entry")));
            fail("Expected unsupported output format to fail");
        } catch (IllegalArgumentException expected) {
            assertEquals("Cannot detect archive format for writer-unsupported.unknown", expected.getMessage());
        }

        assertFalse(archive.exists());
    }

    private void assertSymbolicLink(Source source) throws Exception {
        int[] symbolicLinks = {0};
        try (Source closeableSource = source) {
            closeableSource.forEachEntry(entry -> {
                if (entry.isSymbolicLink()) {
                    symbolicLinks[0]++;
                    assertEquals("../libexec/jenv", entry.getLinkTarget());
                }
            });
        }
        assertEquals(1, symbolicLinks[0]);
    }

    private static class LinkSource implements Source {

        @Override
        public void forEachEntry(EntryConsumer consumer) throws IOException {
            consumer.accept(SourceEntry.file(
                    "target.txt", EntryContents.of("target".getBytes(StandardCharsets.UTF_8)), 0644, 0));
            consumer.accept(SourceEntry.hardLink("link.txt", "target.txt", 0644, 0));
        }

        @Override
        public boolean isDirectory() {
            return false;
        }

        @Override
        public void close() throws IOException {}
    }
}
