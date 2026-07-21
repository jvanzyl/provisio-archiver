package ca.vanzyl.provisio.archive;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.Test;

public class EntryOrderTest extends FileSystemAssert {

    @Test
    public void sourceOrderWritesContentBeforeAdvancingWithoutSpooling() throws Exception {
        File archive = getTargetArchive("source-order-streaming.tar.gz");
        Source source =
                new SpoolObservingSource(archive.toPath().toAbsolutePath().getParent(), "second", "first");

        Archiver.builder()
                .reproducibility(ReproducibilityPolicy.NORMALIZED)
                .entryOrder(EntryOrder.SOURCE)
                .build()
                .archive(archive.toPath(), source);

        assertEquals(Arrays.asList("second", "first"), entryNames(archive));
        ArchiveValidator validator = new TarGzArchiveValidator(archive);
        validator.assertTimeOfEntryInArchive("second", Archiver.DOS_EPOCH_IN_JAVA_TIME);
        validator.assertTimeOfEntryInArchive("first", Archiver.DOS_EPOCH_IN_JAVA_TIME);
    }

    @Test
    public void nameOrderSpoolsScopedContentAndSortsWithoutNormalization() throws Exception {
        File archive = getTargetArchive("name-order-spooling.tar.gz");
        Source source = new CallbackScopedSource("second", "first");

        Archiver.builder().entryOrder(EntryOrder.NAME).build().archive(archive.toPath(), source);

        assertEquals(Arrays.asList("first", "second"), entryNames(archive));
        new TarGzArchiveValidator(archive).assertContentOfEntryInArchive("first", "first");
        assertEquals(0, spoolFiles(archive.toPath().toAbsolutePath().getParent()));
    }

    @Test
    public void sourceOrderIsTheDefault() throws Exception {
        File archive = getTargetArchive("default-source-order.tar.gz");

        Archiver.builder().build().archive(archive.toPath(), new StringListSource(Arrays.asList("z", "a")));

        assertEquals(Arrays.asList("z", "a"), entryNames(archive));
    }

    @Test
    public void nullEntryOrderIsRejected() {
        try {
            Archiver.builder().entryOrder(null);
            fail("Expected null entry order to fail");
        } catch (NullPointerException expected) {
            // Expected.
        }
    }

    private List<String> entryNames(File archive) throws IOException {
        List<String> names = new ArrayList<>();
        Sources.tarGz(archive.toPath()).forEachEntry(entry -> names.add(entry.getName()));
        return names;
    }

    private static long spoolFiles(Path directory) throws IOException {
        try (Stream<Path> files = Files.list(directory)) {
            return files.filter(path -> path.getFileName().toString().startsWith(".provisio-entry-"))
                    .count();
        }
    }

    private static final class SpoolObservingSource implements Source {

        private final Path outputDirectory;
        private final String[] names;

        private SpoolObservingSource(Path outputDirectory, String... names) {
            this.outputDirectory = outputDirectory;
            this.names = names;
        }

        @Override
        public void forEachEntry(EntryConsumer consumer) throws IOException {
            for (String name : names) {
                boolean[] opened = {false};
                EntryContent content = new EntryContent() {
                    @Override
                    public InputStream open() {
                        opened[0] = true;
                        return new ByteArrayInputStream(name.getBytes(StandardCharsets.UTF_8));
                    }

                    @Override
                    public long size() {
                        return name.length();
                    }
                };
                consumer.accept(SourceEntry.file(name, content, 0644, 1));
                assertTrue("Content must be consumed before source traversal advances", opened[0]);
                assertEquals("SOURCE order must not retain a spool file", 0, spoolFiles(outputDirectory));
            }
        }

        @Override
        public boolean isDirectory() {
            return false;
        }

        @Override
        public void close() throws IOException {}
    }

    private static final class CallbackScopedSource implements Source {

        private final String[] names;

        private CallbackScopedSource(String... names) {
            this.names = names;
        }

        @Override
        public void forEachEntry(EntryConsumer consumer) throws IOException {
            for (String name : names) {
                boolean[] active = {true};
                EntryContent content = new EntryContent() {
                    @Override
                    public InputStream open() throws IOException {
                        if (!active[0]) {
                            throw new IOException("content used after callback");
                        }
                        return new ByteArrayInputStream(name.getBytes(StandardCharsets.UTF_8));
                    }

                    @Override
                    public long size() {
                        return name.length();
                    }
                };
                consumer.accept(SourceEntry.file(name, content, 0644, 1));
                active[0] = false;
            }
        }

        @Override
        public boolean isDirectory() {
            return false;
        }

        @Override
        public void close() throws IOException {}
    }
}
