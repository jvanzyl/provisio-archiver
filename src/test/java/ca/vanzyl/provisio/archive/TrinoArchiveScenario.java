package ca.vanzyl.provisio.archive;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;

/** Shared workload for the structural Trino-shaped test and the opt-in macrobenchmark. */
final class TrinoArchiveScenario {

    Result run(Path directory, int entryCount, int uniquePayloadCount) throws IOException {
        if (entryCount < 1 || uniquePayloadCount < 1 || uniquePayloadCount > entryCount) {
            throw new IllegalArgumentException("unique payload count must be between one and the entry count");
        }
        Files.createDirectories(directory);
        Path sourceArchive = directory.resolve("trino-input.zip");
        Path outputArchive = directory.resolve("trino-output.tar.gz");
        List<byte[]> payloads = payloads(uniquePayloadCount);
        Archiver.builder()
                .entryOrder(EntryOrder.SOURCE)
                .build()
                .archive(sourceArchive, new JarLikeSource(entryCount, payloads));

        TrackingSource source = new TrackingSource(Sources.zip(sourceArchive));
        long started = System.nanoTime();
        Archiver.builder()
                .entryOrder(EntryOrder.SOURCE)
                .contentIdentity(ContentIdentityMode.SIZE_AND_CRC32)
                .hardLinkIncludes("**/*.jar")
                .gzipCompressionThreads(4)
                .gzipCompressionLevel(1)
                .build()
                .archive(outputArchive, source);
        long elapsedNanos = System.nanoTime() - started;

        int[] regularFiles = {0};
        int[] hardLinks = {0};
        try (Source output = Sources.tarGz(outputArchive)) {
            output.forEachEntry(entry -> {
                if (entry.getType() == EntryType.FILE) {
                    regularFiles[0]++;
                } else if (entry.getType() == EntryType.HARD_LINK) {
                    hardLinks[0]++;
                }
            });
        }

        long directories;
        long unexpectedFiles;
        try (Stream<Path> children = Files.list(directory)) {
            List<Path> paths = new ArrayList<>();
            children.forEach(paths::add);
            directories = paths.stream().filter(Files::isDirectory).count();
            unexpectedFiles = paths.stream()
                    .filter(path -> !path.equals(sourceArchive) && !path.equals(outputArchive))
                    .count();
        }
        return new Result(
                entryCount,
                uniquePayloadCount,
                source.contentOpenCount,
                regularFiles[0],
                hardLinks[0],
                elapsedNanos,
                Files.size(sourceArchive),
                Files.size(outputArchive),
                directories,
                unexpectedFiles);
    }

    private List<byte[]> payloads(int count) {
        List<byte[]> payloads = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            byte[] payload = new byte[512 + i];
            new Random(0x30400L + i).nextBytes(payload);
            payloads.add(payload);
        }
        return payloads;
    }

    static final class Result {

        final int entries;
        final int uniquePayloads;
        final int contentOpens;
        final int regularFiles;
        final int hardLinks;
        final long elapsedNanos;
        final long sourceBytes;
        final long outputBytes;
        final long expandedDirectories;
        final long unexpectedFiles;

        private Result(
                int entries,
                int uniquePayloads,
                int contentOpens,
                int regularFiles,
                int hardLinks,
                long elapsedNanos,
                long sourceBytes,
                long outputBytes,
                long expandedDirectories,
                long unexpectedFiles) {
            this.entries = entries;
            this.uniquePayloads = uniquePayloads;
            this.contentOpens = contentOpens;
            this.regularFiles = regularFiles;
            this.hardLinks = hardLinks;
            this.elapsedNanos = elapsedNanos;
            this.sourceBytes = sourceBytes;
            this.outputBytes = outputBytes;
            this.expandedDirectories = expandedDirectories;
            this.unexpectedFiles = unexpectedFiles;
        }
    }

    private static final class JarLikeSource implements Source {

        private final int entryCount;
        private final List<byte[]> payloads;

        private JarLikeSource(int entryCount, List<byte[]> payloads) {
            this.entryCount = entryCount;
            this.payloads = payloads;
        }

        @Override
        public void forEachEntry(EntryConsumer consumer) throws IOException {
            for (int i = 0; i < entryCount; i++) {
                byte[] payload = payloads.get(i % payloads.size());
                String name = String.format("repository/group-%03d/artifact-%05d/artifact-%05d-1.0.jar", i % 128, i, i);
                consumer.accept(SourceEntry.file(name, EntryContents.of(payload), 0644, 1_700_000_000_000L));
            }
        }

        @Override
        public boolean isDirectory() {
            return false;
        }

        @Override
        public void close() {}
    }

    private static final class TrackingSource implements Source {

        private final Source delegate;
        private int contentOpenCount;

        private TrackingSource(Source delegate) {
            this.delegate = delegate;
        }

        @Override
        public void forEachEntry(EntryConsumer consumer) throws IOException {
            delegate.forEachEntry(entry -> {
                if (entry.getType() != EntryType.FILE) {
                    consumer.accept(entry);
                    return;
                }
                EntryContent content = entry.getContent();
                consumer.accept(entry.withContent(new EntryContent() {
                    @Override
                    public InputStream open() throws IOException {
                        contentOpenCount++;
                        return content.open();
                    }

                    @Override
                    public long size() {
                        return content.size();
                    }

                    @Override
                    public long crc32() {
                        return content.crc32();
                    }

                    @Override
                    public boolean isRepeatable() {
                        return content.isRepeatable();
                    }
                }));
            });
        }

        @Override
        public boolean isDirectory() {
            return delegate.isDirectory();
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }
    }
}
