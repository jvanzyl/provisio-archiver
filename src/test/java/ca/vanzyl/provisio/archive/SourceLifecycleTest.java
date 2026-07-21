package ca.vanzyl.provisio.archive;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.Test;

public class SourceLifecycleTest extends FileSystemAssert {

    @Test
    public void archiverCanBeReusedWithoutLeakingNormalizedEntries() throws Exception {
        Archiver archiver = Archiver.builder()
                .reproducibility(ReproducibilityPolicy.NORMALIZED)
                .build();

        File firstArchive = getTargetArchive("reuse-first.tar.gz");
        archiver.archive(firstArchive.toPath(), new StringListSource(Collections.singletonList("first")));
        new TarGzArchiveValidator(firstArchive).assertEntries("first");

        File secondArchive = getTargetArchive("reuse-second.tar.gz");
        archiver.archive(secondArchive.toPath(), new StringListSource(Collections.singletonList("second")));
        new TarGzArchiveValidator(secondArchive).assertEntries("second");
    }

    @Test
    public void builtArchiverDoesNotObserveLaterBuilderMutation() throws Exception {
        Archiver.ArchiverBuilder builder = Archiver.builder().entryOrder(EntryOrder.SOURCE);
        Archiver archiver = builder.build();
        builder.entryOrder(EntryOrder.NAME)
                .reproducibility(ReproducibilityPolicy.NORMALIZED)
                .executable("**");

        File archive = getTargetArchive("builder-snapshot.tar.gz");
        archiver.archive(archive.toPath(), new StringListSource(Arrays.asList("second", "first")));

        assertEquals(Arrays.asList("second", "first"), entryNames(archive));
    }

    @Test
    public void hardLinkCandidatesDoNotLeakBetweenArchiveOperations() throws Exception {
        Archiver archiver = Archiver.builder().hardLinkIncludes("**/*.jar").build();

        archiver.archive(
                getTargetArchive("hard-link-state-first.tar.gz").toPath(),
                new StringListSource(Collections.singletonList("first/library.jar")));
        File secondArchive = getTargetArchive("hard-link-state-second.tar.gz");
        archiver.archive(secondArchive.toPath(), new StringListSource(Collections.singletonList("second/library.jar")));

        List<EntryType> types = new ArrayList<>();
        Sources.tarGz(secondArchive.toPath()).forEachEntry(entry -> types.add(entry.getType()));
        assertEquals(Arrays.asList(EntryType.DIRECTORY, EntryType.FILE), types);
    }

    @Test
    public void configuredArchiverCanRunConcurrentIndependentOperations() throws Exception {
        Archiver archiver = Archiver.builder()
                .reproducibility(ReproducibilityPolicy.NORMALIZED)
                .entryOrder(EntryOrder.NAME)
                .build();
        File firstArchive = getTargetArchive("concurrent-first.tar.gz");
        File secondArchive = getTargetArchive("concurrent-second.tar.gz");
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = executor.submit(() -> {
                start.await();
                archiver.archive(firstArchive.toPath(), new StringListSource(Arrays.asList("b", "a")));
                return null;
            });
            Future<?> second = executor.submit(() -> {
                start.await();
                archiver.archive(secondArchive.toPath(), new StringListSource(Arrays.asList("d", "c")));
                return null;
            });
            start.countDown();
            first.get();
            second.get();
        } finally {
            executor.shutdownNow();
        }

        assertEquals(Arrays.asList("a", "b"), entryNames(firstArchive));
        assertEquals(Arrays.asList("c", "d"), entryNames(secondArchive));
    }

    @Test
    public void sourceIsClosedAfterSuccessfulTraversal() throws Exception {
        TrackingSource source = new TrackingSource(Collections.singletonList("entry"), null, null);

        Archiver.builder()
                .build()
                .archive(getTargetArchive("source-close-success.tar.gz").toPath(), source);

        assertEquals(1, source.closeCount);
    }

    @Test
    public void sourceIsClosedWhenTraversalFails() throws Exception {
        IOException readFailure = new IOException("source read failed");
        TrackingSource source = new TrackingSource(Collections.emptyList(), readFailure, null);

        try {
            Archiver.builder()
                    .build()
                    .archive(getTargetArchive("source-read-failure.tar.gz").toPath(), source);
            fail("Expected source traversal to fail");
        } catch (IOException e) {
            assertSame(readFailure, e);
        }

        assertEquals(1, source.closeCount);
    }

    @Test
    public void closeFailureIsReportedAfterSuccessfulTraversal() throws Exception {
        IOException closeFailure = new IOException("source close failed");
        TrackingSource source = new TrackingSource(Collections.singletonList("entry"), null, closeFailure);

        try {
            Archiver.builder()
                    .build()
                    .archive(getTargetArchive("source-close-failure.tar.gz").toPath(), source);
            fail("Expected source close to fail");
        } catch (IOException e) {
            assertSame(closeFailure, e);
        }

        assertEquals(1, source.closeCount);
    }

    @Test
    public void closeFailureIsSuppressedWhenEntryProcessingAlreadyFailed() throws Exception {
        IOException closeFailure = new IOException("source close failed");
        TrackingSource source = new TrackingSource(Arrays.asList("duplicate", "duplicate"), null, closeFailure);

        try {
            Archiver.builder()
                    .build()
                    .archive(
                            getTargetArchive("source-suppressed-close-failure.tar.gz")
                                    .toPath(),
                            source);
            fail("Expected duplicate entry to fail");
        } catch (IllegalArgumentException e) {
            assertEquals("Duplicate archive entry duplicate", e.getMessage());
            assertEquals(1, e.getSuppressed().length);
            assertSame(closeFailure, e.getSuppressed()[0]);
        }

        assertEquals(1, source.closeCount);
    }

    @Test
    public void sourceIsClosedWhenEntryProcessingFails() throws Exception {
        TrackingSource source = new TrackingSource(Arrays.asList("duplicate", "duplicate"), null, null);

        try {
            Archiver.builder()
                    .build()
                    .archive(getTargetArchive("source-entry-failure.tar.gz").toPath(), source);
            fail("Expected duplicate entry to fail");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("duplicate"));
        }

        assertEquals(1, source.closeCount);
    }

    @Test
    public void independentlyConfiguredSourcesAreEachClosedWhenALaterSourceFails() throws Exception {
        TrackingSource first = new TrackingSource(Collections.singletonList("first"), null, null);
        IOException readFailure = new IOException("second source failed");
        TrackingSource second = new TrackingSource(Collections.emptyList(), readFailure, null);

        try {
            Archiver.builder()
                    .build()
                    .archive(
                            getTargetArchive("source-spec-close-failure.tar.gz").toPath(),
                            SourceSpec.builder(first)
                                    .destinationPrefix("first/")
                                    .build(),
                            SourceSpec.builder(second)
                                    .destinationPrefix("second/")
                                    .build());
            fail("Expected second source traversal to fail");
        } catch (IOException expected) {
            assertSame(readFailure, expected);
        }

        assertEquals(1, first.closeCount);
        assertEquals(1, second.closeCount);
    }

    private List<String> entryNames(File archive) throws IOException {
        List<String> names = new ArrayList<>();
        Sources.tarGz(archive.toPath()).forEachEntry(entry -> names.add(entry.getName()));
        return names;
    }

    private static class TrackingSource implements Source {

        private final List<String> entries;
        private final IOException traversalFailure;
        private final IOException closeFailure;
        private int closeCount;

        private TrackingSource(List<String> entries, IOException traversalFailure, IOException closeFailure) {
            this.entries = entries;
            this.traversalFailure = traversalFailure;
            this.closeFailure = closeFailure;
        }

        @Override
        public void forEachEntry(EntryConsumer consumer) throws IOException {
            if (traversalFailure != null) {
                throw traversalFailure;
            }
            for (String entry : entries) {
                consumer.accept(StringListSource.entry(entry));
            }
        }

        @Override
        public boolean isDirectory() {
            return false;
        }

        @Override
        public void close() throws IOException {
            closeCount++;
            if (closeFailure != null) {
                throw closeFailure;
            }
        }
    }
}
