package ca.vanzyl.provisio.archive;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public class SourceLifecycleTest extends FileSystemAssert {

    @Test
    public void archiverCanBeReusedWithoutLeakingNormalizedEntries() throws Exception {
        Archiver archiver = Archiver.builder().normalize(true).build();

        File firstArchive = getTargetArchive("reuse-first.tar.gz");
        archiver.archive(firstArchive, new StringListSource(Collections.singletonList("first")));
        new TarGzArchiveValidator(firstArchive).assertEntries("first");

        File secondArchive = getTargetArchive("reuse-second.tar.gz");
        archiver.archive(secondArchive, new StringListSource(Collections.singletonList("second")));
        new TarGzArchiveValidator(secondArchive).assertEntries("second");
    }

    @Test
    public void sourceIsClosedAfterSuccessfulTraversal() throws Exception {
        TrackingSource source = new TrackingSource(Collections.singletonList("entry"), null, null);

        Archiver.builder().build().archive(getTargetArchive("source-close-success.tar.gz"), source);

        assertEquals(1, source.closeCount);
    }

    @Test
    public void sourceIsClosedWhenTraversalFails() throws Exception {
        IOException readFailure = new IOException("source read failed");
        TrackingSource source = new TrackingSource(Collections.emptyList(), readFailure, null);

        try {
            Archiver.builder().build().archive(getTargetArchive("source-read-failure.tar.gz"), source);
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
            Archiver.builder().build().archive(getTargetArchive("source-close-failure.tar.gz"), source);
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
            Archiver.builder().build().archive(getTargetArchive("source-suppressed-close-failure.tar.gz"), source);
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
            Archiver.builder().build().archive(getTargetArchive("source-entry-failure.tar.gz"), source);
            fail("Expected duplicate entry to fail");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("duplicate"));
        }

        assertEquals(1, source.closeCount);
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
                consumer.accept(new StringListSource.StringEntry(entry));
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
