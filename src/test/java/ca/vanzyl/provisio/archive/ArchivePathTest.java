package ca.vanzyl.provisio.archive;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import ca.vanzyl.provisio.archive.tar.TarGzArchiveSource;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.archivers.tar.TarConstants;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.junit.Test;

public class ArchivePathTest extends FileSystemAssert {

    @Test
    public void entryPathsAreCanonicalizedIndependentlyOfTheHostPlatform() throws Exception {
        ArchivePath path = ArchivePath.parse("one//two\\three/", "test path");

        assertEquals("one/two/three", path.value());
        assertEquals("one/two/three/", path.entryName(EntryType.DIRECTORY));
        assertEquals("two/three", path.withoutFirstSegment().value());
        assertEquals("three", path.fileName().value());
        assertEquals(
                "prefix/one/two/three", path.prepend("prefix/", "test prefix").value());
    }

    @Test
    public void unsafeEntryPathsAreRejected() throws Exception {
        for (String path : Arrays.asList(
                "",
                "/absolute",
                "//server/share",
                "\\\\server\\share",
                "C:/absolute",
                "c:\\absolute",
                "nested/C:/absolute",
                "one/./two",
                "one/../two",
                "one\\..\\two",
                "nul\0path")) {
            try {
                ArchivePath.parse(path, "test path");
                fail("Expected path to be rejected: " + path);
            } catch (IOException expected) {
                assertTrue(expected.getMessage().startsWith("Invalid test path:"));
            }
        }
    }

    @Test
    public void symbolicLinksMayTraverseParentsOnlyWithinTheArchive() throws Exception {
        ArchivePath link = ArchivePath.parse("root/bin/tool", "test link");

        assertEquals("../libexec/tool", ArchivePath.validateSymbolicLinkTarget(link, "../libexec/tool"));
        assertEquals("../../tool", ArchivePath.validateSymbolicLinkTarget(link, "../../tool"));

        for (String target : Arrays.asList(
                "../../../escape", "/absolute", "\\\\server\\share", "C:/absolute", "./target", "nul\0path")) {
            try {
                ArchivePath.validateSymbolicLinkTarget(link, target);
                fail("Expected symbolic link target to be rejected: " + target);
            } catch (IOException expected) {
                assertTrue(expected.getMessage().startsWith("Invalid symbolic link target"));
            }
        }
    }

    @Test
    public void archiverRejectsUnsafeSourcePathsTransactionally() throws Exception {
        int index = 0;
        for (String path :
                Arrays.asList("../escape", "/absolute", "\\\\server\\share", "C:/absolute", "one/./two", "nul\0path")) {
            File archive = getTargetArchive("unsafe-source-" + index++ + ".tar.gz");
            Files.deleteIfExists(archive.toPath());
            try {
                Archiver.builder().build().archive(archive, new NamedSource(path));
                fail("Expected unsafe source path to fail: " + path);
            } catch (IOException expected) {
                assertTrue(expected.getMessage().startsWith("Invalid source entry path:"));
            }
            assertFalse(archive.exists());
        }
    }

    @Test
    public void archiverRejectsUnsafePrefixesAndCanonicalMappingCollisions() throws Exception {
        File prefixedArchive = getTargetArchive("unsafe-prefix.tar.gz");
        Files.deleteIfExists(prefixedArchive.toPath());
        try {
            SourceSpec source = SourceSpec.builder(new NamedSource("entry"))
                    .destinationPrefix("../escape")
                    .build();
            Archiver.builder().build().archive(prefixedArchive, source);
            fail("Expected unsafe prefix to fail");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().startsWith("Invalid source destination prefix:"));
        }
        assertFalse(prefixedArchive.exists());

        File collisionArchive = getTargetArchive("canonical-collision.tar.gz");
        Files.deleteIfExists(collisionArchive.toPath());
        try {
            Archiver.builder().build().archive(collisionArchive, new NamedSource("one//two", "one\\two"));
            fail("Expected canonical path collision to fail");
        } catch (IllegalArgumentException expected) {
            assertEquals("Duplicate archive entry one/two", expected.getMessage());
        }
        assertFalse(collisionArchive.exists());
    }

    @Test
    public void archiverRejectsFileDirectoryPathCollisions() throws Exception {
        File archive = getTargetArchive("file-directory-collision.tar.gz");
        Files.deleteIfExists(archive.toPath());
        Source source = new Source() {
            @Override
            public void forEachEntry(EntryConsumer consumer) throws IOException {
                consumer.accept(SourceEntry.file(
                        "same/child", EntryContents.of("child".getBytes(StandardCharsets.UTF_8)), 0644, 0));
                consumer.accept(
                        SourceEntry.file("same", EntryContents.of("file".getBytes(StandardCharsets.UTF_8)), 0644, 0));
            }

            @Override
            public boolean isDirectory() {
                return false;
            }

            @Override
            public void close() throws IOException {}
        };

        try {
            Archiver.builder().build().archive(archive, source);
            fail("Expected file and directory paths to collide");
        } catch (IllegalArgumentException expected) {
            assertEquals("Duplicate archive entry same", expected.getMessage());
        }
        assertFalse(archive.exists());
    }

    @Test
    public void hardLinkTargetsFollowRootRemovalAndPrefixMapping() throws Exception {
        File archive = getTargetArchive("mapped-hard-link.tar.gz");
        Source source = new Source() {
            @Override
            public void forEachEntry(EntryConsumer consumer) throws IOException {
                consumer.accept(SourceEntry.file(
                        "root/target", EntryContents.of("target".getBytes(StandardCharsets.UTF_8)), 0644, 0));
                consumer.accept(SourceEntry.hardLink("root/link", "root/target", 0644, 0));
            }

            @Override
            public boolean isDirectory() {
                return true;
            }

            @Override
            public void close() throws IOException {}
        };

        SourceSpec sourceSpec = SourceSpec.builder(source)
                .useRoot(false)
                .destinationPrefix("prefix/")
                .build();
        Archiver.builder().build().archive(archive, sourceSpec);

        int[] links = {0};
        new TarGzArchiveSource(archive).forEachEntry(entry -> {
            if (entry.isHardLink()) {
                links[0]++;
                assertEquals("prefix/target", entry.getLinkTarget());
            }
        });
        assertEquals(1, links[0]);
    }

    @Test
    public void unarchiverRejectsMaliciousEntryAndProcessorPathsBeforeWriting() throws Exception {
        File malicious = getTargetArchive("malicious-entry.zip");
        writeZip(malicious.toPath(), "../outside.txt", "outside");
        File output = getOutputDirectory("malicious-entry-output");
        Path outside = output.toPath().resolve("../outside.txt").normalize();
        Files.deleteIfExists(outside);

        try {
            UnArchiver.builder().build().unarchive(malicious, output);
            fail("Expected malicious archive entry to fail");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().startsWith("Invalid archive entry path:"));
        }
        assertFalse(Files.exists(outside));

        File ordinary = getTargetArchive("malicious-processor-source.zip");
        Archiver.builder().build().archive(ordinary, new StringListSource(Collections.singletonList("entry")));
        try {
            UnArchiver.builder()
                    .build()
                    .unarchive(ordinary.toPath(), output.toPath(), new UnarchivingEnhancedEntryProcessor() {
                        @Override
                        public String targetName(String name) {
                            return "../processor-outside.txt";
                        }
                    });
            fail("Expected malicious processed path to fail");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().startsWith("Invalid processed archive entry path:"));
        }
        assertFalse(
                Files.exists(output.toPath().resolve("../processor-outside.txt").normalize()));
    }

    @Test
    public void unarchiverRejectsCanonicalAndFlattenedOutputCollisions() throws Exception {
        File canonical = getTargetArchive("unarchive-canonical-collision.zip");
        writeZipEntries(canonical.toPath(), "one//two", "one\\two");
        try {
            UnArchiver.builder().build().unarchive(canonical, getOutputDirectory("unarchive-canonical-collision"));
            fail("Expected canonical output collision to fail");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().startsWith("Duplicate archive output path one/two"));
        }

        File flattened = getTargetArchive("unarchive-flatten-collision.zip");
        writeZipEntries(flattened.toPath(), "one/file", "two/file");
        try {
            UnArchiver.builder()
                    .flatten(true)
                    .build()
                    .unarchive(flattened, getOutputDirectory("unarchive-flatten-collision"));
            fail("Expected flattened output collision to fail");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().startsWith("Duplicate archive output path file"));
        }
    }

    @Test
    public void unarchiverRejectsEscapingSymbolicAndHardLinks() throws Exception {
        File symbolicArchive = getTargetArchive("malicious-symbolic-link.tar.gz");
        writeTarLink(symbolicArchive.toPath(), "link", "../outside", TarConstants.LF_SYMLINK);
        try {
            UnArchiver.builder().build().unarchive(symbolicArchive, getOutputDirectory("malicious-symbolic-output"));
            fail("Expected escaping symbolic link to fail");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("target escapes the archive root"));
        }

        File hardLinkArchive = getTargetArchive("malicious-hard-link.tar.gz");
        writeTarLink(hardLinkArchive.toPath(), "link", "../outside", TarConstants.LF_LINK);
        try {
            UnArchiver.builder().build().unarchive(hardLinkArchive, getOutputDirectory("malicious-hard-output"));
            fail("Expected escaping hard link to fail");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().startsWith("Invalid hard link target:"));
        }
    }

    private void writeZip(Path archive, String name, String content) throws IOException {
        try (ZipArchiveOutputStream outputStream = new ZipArchiveOutputStream(Files.newOutputStream(archive))) {
            byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
            ZipArchiveEntry entry = new ZipArchiveEntry(name);
            outputStream.putArchiveEntry(entry);
            outputStream.write(bytes);
            outputStream.closeArchiveEntry();
        }
    }

    private void writeZipEntries(Path archive, String... names) throws IOException {
        try (ZipArchiveOutputStream outputStream = new ZipArchiveOutputStream(Files.newOutputStream(archive))) {
            for (String name : names) {
                ZipArchiveEntry entry = new ZipArchiveEntry(name);
                outputStream.putArchiveEntry(entry);
                outputStream.write(name.getBytes(StandardCharsets.UTF_8));
                outputStream.closeArchiveEntry();
            }
        }
    }

    private void writeTarLink(Path archive, String name, String target, byte type) throws IOException {
        try (TarArchiveOutputStream outputStream =
                new TarArchiveOutputStream(new GzipCompressorOutputStream(Files.newOutputStream(archive)))) {
            TarArchiveEntry entry = new TarArchiveEntry(name, type);
            entry.setLinkName(target);
            outputStream.putArchiveEntry(entry);
            outputStream.closeArchiveEntry();
        }
    }

    private static class NamedSource implements Source {

        private final String[] names;

        private NamedSource(String... names) {
            this.names = names;
        }

        @Override
        public void forEachEntry(EntryConsumer consumer) throws IOException {
            for (String name : names) {
                consumer.accept(
                        SourceEntry.file(name, EntryContents.of("content".getBytes(StandardCharsets.UTF_8)), 0644, 0));
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
