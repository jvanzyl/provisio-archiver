package ca.vanzyl.provisio.archive;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import org.junit.Test;

public class SourceSpecTest extends FileSystemAssert {

    @Test
    public void eachSourceHasIndependentSelectionAndMapping() throws Exception {
        SourceSpec first = SourceSpec.builder(new DirectoryLikeSource("root/a", "root/skip"))
                .includes("**/a")
                .useRoot(false)
                .destinationPrefix("one/")
                .build();
        SourceSpec second = SourceSpec.builder(new DirectoryLikeSource("root/nested/b", "root/nested/skip"))
                .excludes("**/skip")
                .useRoot(false)
                .flatten(true)
                .destinationPrefix("two/")
                .build();

        File archive = getTargetArchive("source-spec-independent-mapping.tar.gz");
        Archiver.builder().build().archive(archive, first, second);

        ArchiveValidator validator = new TarGzArchiveValidator(archive);
        validator.assertEntries("one/", "one/a", "two/", "two/b");
        validator.assertContentOfEntryInArchive("one/a", "root/a");
        validator.assertContentOfEntryInArchive("two/b", "root/nested/b");
    }

    @Test
    public void sourceSpecIsUnaffectedByLaterBuilderMutation() throws Exception {
        SourceSpec.Builder builder =
                SourceSpec.builder(new DirectoryLikeSource("a", "b")).includes("a");
        SourceSpec spec = builder.build();
        builder.includes("b").destinationPrefix("changed/");

        File archive = getTargetArchive("source-spec-immutable.zip");
        Archiver.builder().build().archive(archive, spec);

        new ZipArchiveValidator(archive).assertEntries("a");
    }

    @Test
    public void mappedPathsStillCollideAcrossDifferentSources() throws Exception {
        SourceSpec first = SourceSpec.builder(new DirectoryLikeSource("one/file"))
                .useRoot(false)
                .destinationPrefix("same/")
                .build();
        SourceSpec second = SourceSpec.builder(new DirectoryLikeSource("two/file"))
                .useRoot(false)
                .destinationPrefix("same/")
                .build();
        File archive = getTargetArchive("source-spec-collision.tar.gz");
        Files.deleteIfExists(archive.toPath());

        try {
            Archiver.builder().build().archive(archive, first, second);
            fail("Expected mappings from different sources to collide");
        } catch (IllegalArgumentException expected) {
            assertEquals("Duplicate archive entry same/file", expected.getMessage());
        }
        assertFalse(archive.exists());
    }

    @Test
    public void excludedEntriesAreNotOpened() throws Exception {
        EntryContent unreadable = new EntryContent() {
            @Override
            public InputStream open() throws IOException {
                throw new IOException("excluded content was opened");
            }

            @Override
            public long size() {
                return 1;
            }
        };
        Source source = new Source() {
            @Override
            public void forEachEntry(EntryConsumer consumer) throws IOException {
                consumer.accept(SourceEntry.file("skip", unreadable, 0644, 0));
            }

            @Override
            public boolean isDirectory() {
                return false;
            }

            @Override
            public void close() throws IOException {}
        };
        SourceSpec spec = SourceSpec.builder(source).excludes("skip").build();
        File archive = getTargetArchive("source-spec-excluded-content.tar.gz");

        Archiver.builder()
                .reproducibility(ReproducibilityPolicy.NORMALIZED)
                .build()
                .archive(archive, spec);

        new TarGzArchiveValidator(archive).assertNumberOfEntriesInArchive(0);
    }

    @Test
    public void nullSourcesAndPatternsAreRejectedWhenConfigured() {
        try {
            SourceSpec.builder(null);
            fail("Expected null source to fail");
        } catch (NullPointerException expected) {
            // Expected.
        }

        try {
            SourceSpec.builder(new DirectoryLikeSource("entry")).includes("entry", null);
            fail("Expected null include to fail");
        } catch (NullPointerException expected) {
            // Expected.
        }

        try {
            SourceSpec.builder(new DirectoryLikeSource("entry")).excludes(Arrays.asList("other", null));
            fail("Expected null exclude to fail");
        } catch (NullPointerException expected) {
            // Expected.
        }
    }

    private static class DirectoryLikeSource implements Source {

        private final String[] names;

        private DirectoryLikeSource(String... names) {
            this.names = names;
        }

        @Override
        public void forEachEntry(EntryConsumer consumer) throws IOException {
            for (String name : names) {
                consumer.accept(
                        SourceEntry.file(name, EntryContents.of(name.getBytes(StandardCharsets.UTF_8)), 0644, 0));
            }
        }

        @Override
        public boolean isDirectory() {
            return true;
        }

        @Override
        public void close() throws IOException {}
    }
}
