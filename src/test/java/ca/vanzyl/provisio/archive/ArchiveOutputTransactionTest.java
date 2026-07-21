package ca.vanzyl.provisio.archive;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.stream.Stream;
import org.junit.Test;

public class ArchiveOutputTransactionTest extends FileSystemAssert {

    @Test
    public void failedArchivePreservesExistingOutput() throws Exception {
        File archive = getTargetArchive("transaction-preserves-existing.tar.gz");
        byte[] original = "existing archive".getBytes(StandardCharsets.UTF_8);
        Files.write(archive.toPath(), original);

        try {
            Archiver.builder().build().archive(archive, new FailingSource());
            fail("Expected archive creation to fail");
        } catch (IOException expected) {
            assertEquals("source failed", expected.getMessage());
        }

        assertArrayEquals(original, Files.readAllBytes(archive.toPath()));
        assertEquals(0, temporaryFilesFor(archive.toPath()));
    }

    @Test
    public void failedArchiveDoesNotLeaveRequestedOutputOrTemporaryFile() throws Exception {
        File archive = getTargetArchive("transaction-removes-partial.tar.gz");
        Files.deleteIfExists(archive.toPath());

        try {
            Archiver.builder().build().archive(archive, new FailingSource());
            fail("Expected archive creation to fail");
        } catch (IOException expected) {
            assertEquals("source failed", expected.getMessage());
        }

        assertFalse(archive.exists());
        assertEquals(0, temporaryFilesFor(archive.toPath()));
    }

    @Test
    public void successfulArchiveReplacesExistingOutput() throws Exception {
        File archive = getTargetArchive("transaction-replaces-existing.tar.gz");
        Files.write(archive.toPath(), "existing archive".getBytes(StandardCharsets.UTF_8));

        Archiver.builder().build().archive(archive, new StringListSource(Collections.singletonList("replacement")));

        new TarGzArchiveValidator(archive).assertEntries("replacement");
        assertEquals(0, temporaryFilesFor(archive.toPath()));
    }

    @Test
    public void archiveCreatesMissingParentDirectories() throws Exception {
        File outputDirectory = getOutputDirectory("transaction-parent");
        File archive = new File(outputDirectory, "nested/archive.tar.gz");

        Archiver.builder().build().archive(archive, new StringListSource(Collections.singletonList("entry")));

        new TarGzArchiveValidator(archive).assertEntries("entry");
    }

    private long temporaryFilesFor(Path archive) throws IOException {
        String prefix = ".provisio-" + archive.getFileName() + "-";
        try (Stream<Path> files = Files.list(archive.toAbsolutePath().getParent())) {
            return files.filter(path -> path.getFileName().toString().startsWith(prefix))
                    .count();
        }
    }

    private static class FailingSource implements Source {

        @Override
        public void forEachEntry(EntryConsumer consumer) throws IOException {
            consumer.accept(new StringListSource.StringEntry("partial"));
            throw new IOException("source failed");
        }

        @Override
        public boolean isDirectory() {
            return false;
        }

        @Override
        public void close() throws IOException {}
    }
}
