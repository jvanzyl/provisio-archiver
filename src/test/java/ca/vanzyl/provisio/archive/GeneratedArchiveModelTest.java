package ca.vanzyl.provisio.archive;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Stream;
import org.apache.commons.io.IOUtils;
import org.junit.Test;

/** Model-based coverage for combinations that are cumbersome to maintain as hand-written fixtures. */
public class GeneratedArchiveModelTest extends FileSystemAssert {

    private static final long[] SEEDS = {0x5eedL, 0xc0ffeeL, 0x30400L};

    @Test
    public void generatedNestedArchivesMatchTheContentModelAcrossFormatsAndOrders() throws Exception {
        for (long seed : SEEDS) {
            List<ModelEntry> model = generateModel(seed, 32);
            for (String nestedExtension : Arrays.asList("zip", "tar.gz")) {
                File nested = getTargetArchive("generated-nested-" + seed + "." + nestedExtension);
                CallbackScopedModelSource nestedInput = new CallbackScopedModelSource(model, false);
                Archiver.builder().build().archive(nested.toPath(), nestedInput);
                nestedInput.assertConsumedExactlyOnce();

                for (String outputExtension : Arrays.asList("zip", "tar.gz")) {
                    for (EntryOrder order : EntryOrder.values()) {
                        File output = getTargetArchive("generated-model-" + seed + "-" + nestedExtension + "-"
                                + outputExtension + "-" + order.name().toLowerCase() + "." + outputExtension);
                        CallbackScopedModelSource directInput = new CallbackScopedModelSource(model, true);
                        SourceSpec direct = SourceSpec.builder(directInput)
                                .useRoot(false)
                                .destinationPrefix("direct")
                                .build();
                        SourceSpec archived = SourceSpec.builder(openArchive(nested))
                                .destinationPrefix("nested")
                                .build();

                        Archiver.builder()
                                .entryOrder(order)
                                .hardLinkIncludes("**/*")
                                .build()
                                .archive(output.toPath(), direct, archived);

                        directInput.assertConsumedExactlyOnce();
                        Map<String, byte[]> expected = expectedContents(model);
                        ObservedArchive actual = readArchive(output);
                        assertContentsEqual(expected, actual.contents);
                        if (outputExtension.equals("tar.gz")) {
                            assertTrue(
                                    "Expected generated duplicate content to produce hard links", actual.hardLinks > 0);
                        } else {
                            assertEquals(0, actual.hardLinks);
                        }
                    }
                }
            }
        }
    }

    @Test
    public void generatedFlatteningCollisionsFailTransactionally() throws Exception {
        for (long seed : SEEDS) {
            String basename = "shared-" + Long.toHexString(seed) + ".bin";
            List<ModelEntry> model = Arrays.asList(
                    new ModelEntry("root/left/" + basename, bytes("left-" + seed)),
                    new ModelEntry("root/right/" + basename, bytes("right-" + seed)));
            File output = getTargetArchive("generated-flatten-collision-" + seed + ".tar.gz");
            byte[] original = bytes("existing-" + seed);
            Files.write(output.toPath(), original);
            SourceSpec source = SourceSpec.builder(new CallbackScopedModelSource(model, true))
                    .useRoot(false)
                    .flatten(true)
                    .destinationPrefix("flat")
                    .build();

            try {
                Archiver.builder().entryOrder(EntryOrder.NAME).build().archive(output.toPath(), source);
                fail("Expected generated flattened paths to collide for seed " + seed);
            } catch (IllegalArgumentException expected) {
                assertEquals("Duplicate archive entry flat/" + basename, expected.getMessage());
            }

            assertArrayEquals(original, Files.readAllBytes(output.toPath()));
            assertEquals(0, temporaryFilesFor(output.toPath()));
            assertEquals(0, spooledContentFilesFor(output.toPath()));
        }
    }

    @Test
    public void generatedExplicitLinksRemainCorrectAfterRootAndPrefixMapping() throws Exception {
        for (long seed : SEEDS) {
            String release = "release-" + Long.toHexString(seed);
            String root = "root/" + release;
            String payload = root + "/files/payload.bin";
            File output = getTargetArchive("generated-links-" + seed + ".tar.gz");
            Source source = new LinkSource(
                    SourceEntry.file(payload, EntryContents.of(bytes("payload-" + seed)), 0640, seed),
                    SourceEntry.hardLink(root + "/files/copy.bin", payload, 0640, seed),
                    SourceEntry.symbolicLink(root + "/bin/tool", "../files/payload.bin", 0777, seed));
            SourceSpec sourceSpec = SourceSpec.builder(source)
                    .useRoot(false)
                    .destinationPrefix("distribution")
                    .build();

            Archiver.builder().build().archive(output.toPath(), sourceSpec);

            Map<String, SourceEntry> entries = readEntries(output);
            String mappedRoot = "distribution/" + release;
            assertEquals(
                    EntryType.FILE,
                    entries.get(mappedRoot + "/files/payload.bin").getType());
            assertEquals(
                    EntryType.HARD_LINK,
                    entries.get(mappedRoot + "/files/copy.bin").getType());
            assertEquals(
                    mappedRoot + "/files/payload.bin",
                    entries.get(mappedRoot + "/files/copy.bin").getLinkTarget());
            assertEquals(
                    EntryType.SYMBOLIC_LINK,
                    entries.get(mappedRoot + "/bin/tool").getType());
            assertEquals(
                    "../files/payload.bin",
                    entries.get(mappedRoot + "/bin/tool").getLinkTarget());
        }
    }

    private List<ModelEntry> generateModel(long seed, int count) {
        Random random = new Random(seed);
        byte[][] payloads = new byte[7][];
        payloads[0] = new byte[0];
        for (int i = 1; i < payloads.length; i++) {
            payloads[i] = new byte[64 + random.nextInt(2048)];
            random.nextBytes(payloads[i]);
        }

        List<ModelEntry> entries = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String extension = i % 3 == 0 ? ".jar" : (i % 3 == 1 ? ".class" : ".txt");
            String name = String.format("root/module-%02d/layer-%02d/entry-%03d%s", i % 5, i % 4, i, extension);
            entries.add(new ModelEntry(name, payloads[random.nextInt(payloads.length)]));
        }
        return entries;
    }

    private Map<String, byte[]> expectedContents(List<ModelEntry> model) {
        Map<String, byte[]> expected = new LinkedHashMap<>();
        for (ModelEntry entry : model) {
            expected.put("direct/" + entry.name.substring("root/".length()), entry.content);
            expected.put("nested/" + entry.name, entry.content);
        }
        return expected;
    }

    private void assertContentsEqual(Map<String, byte[]> expected, Map<String, byte[]> actual) {
        assertEquals(expected.keySet(), actual.keySet());
        for (Map.Entry<String, byte[]> entry : expected.entrySet()) {
            assertArrayEquals("Content mismatch for " + entry.getKey(), entry.getValue(), actual.get(entry.getKey()));
        }
    }

    private Source openArchive(File archive) {
        return Sources.archive(archive.toPath());
    }

    private ObservedArchive readArchive(File archive) throws IOException {
        Map<String, ObservedEntry> entries = new LinkedHashMap<>();
        int[] hardLinks = {0};
        try (Source source = openArchive(archive)) {
            source.forEachEntry(entry -> {
                if (entry.getType() == EntryType.FILE) {
                    try (InputStream input = entry.getContent().open()) {
                        entries.put(entry.getName(), new ObservedEntry(IOUtils.toByteArray(input), null));
                    }
                } else if (entry.getType() == EntryType.HARD_LINK) {
                    entries.put(entry.getName(), new ObservedEntry(null, entry.getLinkTarget()));
                    hardLinks[0]++;
                }
            });
        }

        Map<String, byte[]> contents = new LinkedHashMap<>();
        for (Map.Entry<String, ObservedEntry> entry : entries.entrySet()) {
            ObservedEntry observed = entry.getValue();
            if (observed.content != null) {
                contents.put(entry.getKey(), observed.content);
            } else {
                ObservedEntry target = entries.get(observed.linkTarget);
                assertTrue("Missing hard-link target " + observed.linkTarget, target != null && target.content != null);
                contents.put(entry.getKey(), target.content);
            }
        }
        return new ObservedArchive(contents, hardLinks[0]);
    }

    private Map<String, SourceEntry> readEntries(File archive) throws IOException {
        Map<String, SourceEntry> entries = new LinkedHashMap<>();
        try (Source source = Sources.tarGz(archive.toPath())) {
            source.forEachEntry(entry -> entries.put(entry.getName(), entry));
        }
        return entries;
    }

    private long temporaryFilesFor(Path archive) throws IOException {
        String prefix = ".provisio-" + archive.getFileName() + "-";
        try (Stream<Path> files = Files.list(archive.toAbsolutePath().getParent())) {
            return files.filter(path -> path.getFileName().toString().startsWith(prefix))
                    .count();
        }
    }

    private long spooledContentFilesFor(Path archive) throws IOException {
        try (Stream<Path> files = Files.list(archive.toAbsolutePath().getParent())) {
            return files.filter(path -> path.getFileName().toString().startsWith(".provisio-entry-"))
                    .count();
        }
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static final class ModelEntry {

        private final String name;
        private final byte[] content;

        private ModelEntry(String name, byte[] content) {
            this.name = name;
            this.content = content;
        }
    }

    private static final class ObservedEntry {

        private final byte[] content;
        private final String linkTarget;

        private ObservedEntry(byte[] content, String linkTarget) {
            this.content = content;
            this.linkTarget = linkTarget;
        }
    }

    private static final class ObservedArchive {

        private final Map<String, byte[]> contents;
        private final int hardLinks;

        private ObservedArchive(Map<String, byte[]> contents, int hardLinks) {
            this.contents = contents;
            this.hardLinks = hardLinks;
        }
    }

    private static final class CallbackScopedModelSource implements Source {

        private final List<ModelEntry> entries;
        private final boolean directory;
        private boolean callbackActive;
        private boolean closed;
        private int openCount;

        private CallbackScopedModelSource(List<ModelEntry> entries, boolean directory) {
            this.entries = entries;
            this.directory = directory;
        }

        @Override
        public void forEachEntry(EntryConsumer consumer) throws IOException {
            if (closed) {
                throw new IOException("source is closed");
            }
            for (ModelEntry entry : entries) {
                callbackActive = true;
                try {
                    consumer.accept(SourceEntry.file(
                            entry.name, new CallbackScopedContent(entry.content), 0644, 1_700_000_000_000L));
                } finally {
                    callbackActive = false;
                }
            }
        }

        @Override
        public boolean isDirectory() {
            return directory;
        }

        @Override
        public void close() {
            closed = true;
        }

        private void assertConsumedExactlyOnce() {
            assertTrue("Expected source to be closed", closed);
            assertFalse("Source callback remained active", callbackActive);
            assertEquals("Each generated entry must be opened once", entries.size(), openCount);
        }

        private final class CallbackScopedContent implements EntryContent {

            private final byte[] content;
            private boolean opened;

            private CallbackScopedContent(byte[] content) {
                this.content = content;
            }

            @Override
            public InputStream open() throws IOException {
                requireActive();
                if (opened) {
                    throw new IOException("generated content opened more than once");
                }
                opened = true;
                openCount++;
                return new FilterInputStream(new ByteArrayInputStream(content)) {
                    @Override
                    public int read() throws IOException {
                        requireActive();
                        return super.read();
                    }

                    @Override
                    public int read(byte[] bytes, int offset, int length) throws IOException {
                        requireActive();
                        return super.read(bytes, offset, length);
                    }
                };
            }

            @Override
            public long size() {
                return content.length;
            }

            private void requireActive() throws IOException {
                if (!callbackActive) {
                    throw new IOException("generated content escaped its source callback");
                }
            }
        }
    }

    private static final class LinkSource implements Source {

        private final List<SourceEntry> entries;

        private LinkSource(SourceEntry... entries) {
            this.entries = Arrays.asList(entries);
        }

        @Override
        public void forEachEntry(EntryConsumer consumer) throws IOException {
            for (SourceEntry entry : entries) {
                consumer.accept(entry);
            }
        }

        @Override
        public boolean isDirectory() {
            return true;
        }

        @Override
        public void close() {}
    }
}
