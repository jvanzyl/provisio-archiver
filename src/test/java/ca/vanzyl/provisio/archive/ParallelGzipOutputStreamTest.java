package ca.vanzyl.provisio.archive;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.junit.Test;

public class ParallelGzipOutputStreamTest {

    @Test
    public void concatenatedMembersRoundTripInSubmissionOrder() throws Exception {
        byte[] content = new byte[4097];
        new Random(12345).nextBytes(content);

        byte[] compressed = compress(content, 4, 64);

        assertArrayEquals(content, decompress(compressed));
    }

    @Test
    public void outputIsDeterministicAcrossWorkerCounts() throws Exception {
        byte[] content = new byte[8193];
        new Random(67890).nextBytes(content);

        assertArrayEquals(compress(content, 1, 128), compress(content, 8, 128));
    }

    @Test
    public void pendingChunkQueueIsBounded() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ParallelGzipOutputStream gzip = new ParallelGzipOutputStream(output, 6, 16, 2);

        gzip.write(new byte[16 * 20]);

        assertEquals(4, gzip.maximumPendingChunks());
        assertTrue(gzip.pendingChunks() <= gzip.maximumPendingChunks());
        gzip.close();
        assertEquals(0, gzip.pendingChunks());
    }

    @Test
    public void submissionAppliesBackpressureAtThePendingChunkLimit() throws Exception {
        CountDownLatch compressorStarted = new CountDownLatch(1);
        CountDownLatch releaseCompressor = new CountDownLatch(1);
        CountDownLatch writeReturned = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ParallelGzipOutputStream gzip = new ParallelGzipOutputStream(output, 6, 4, 1, (source, length, level) -> {
            compressorStarted.countDown();
            try {
                releaseCompressor.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("compressor interrupted", e);
            }
            return ParallelGzipOutputStream.compressMember(source, length, level);
        });
        Thread writer = new Thread(() -> {
            try {
                gzip.write(new byte[12]);
            } catch (Throwable e) {
                failure.set(e);
            } finally {
                writeReturned.countDown();
            }
        });
        writer.start();
        assertTrue(compressorStarted.await(5, TimeUnit.SECONDS));

        assertFalse("The producer escaped gzip backpressure", writeReturned.await(100, TimeUnit.MILLISECONDS));
        assertEquals(gzip.maximumPendingChunks(), gzip.pendingChunks());
        releaseCompressor.countDown();
        assertTrue(writeReturned.await(5, TimeUnit.SECONDS));
        writer.join(5_000);
        assertFalse(writer.isAlive());
        assertNull(failure.get());
        gzip.close();
        assertArrayEquals(new byte[12], decompress(output.toByteArray()));
    }

    @Test
    public void emptyInputProducesAValidEmptyGzipStream() throws Exception {
        byte[] compressed = compress(new byte[0], 2, 32);

        assertTrue(compressed.length >= 18);
        assertArrayEquals(new byte[0], decompress(compressed));
    }

    @Test
    public void compressedMemberRetainsBackingBufferWithoutTrimmingCopy() throws Exception {
        byte[] content = new byte[1024];

        ParallelGzipOutputStream.CompressedMember member =
                ParallelGzipOutputStream.compressMember(content, content.length, 6);

        assertTrue(member.length() < member.buffer().length);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.write(member.buffer(), 0, member.length());
        assertArrayEquals(content, decompress(output.toByteArray()));
    }

    @Test
    public void flushDoesNotMakePartialChunkBoundariesScheduleDependent() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ParallelGzipOutputStream gzip = new ParallelGzipOutputStream(output, 6, 32, 2);

        gzip.write(new byte[8]);
        gzip.flush();
        assertEquals(0, output.size());
        gzip.close();

        assertArrayEquals(new byte[8], decompress(output.toByteArray()));
    }

    @Test
    public void workerFailureRemainsPrimaryWhenOutputCloseAlsoFails() throws Exception {
        IOException workerFailure = new IOException("worker failed");
        IOException closeFailure = new IOException("output close failed");
        FailingCloseOutputStream output = new FailingCloseOutputStream(closeFailure);
        ParallelGzipOutputStream gzip = new ParallelGzipOutputStream(output, 6, 4, 2, (source, length, level) -> {
            if (source[0] == 2) {
                throw workerFailure;
            }
            return ParallelGzipOutputStream.compressMember(source, length, level);
        });
        gzip.write(new byte[] {1, 1, 1, 1, 2, 2, 2, 2});

        try {
            gzip.close();
            fail("Expected compression worker failure");
        } catch (IOException expected) {
            assertSame(workerFailure, expected);
            assertEquals(1, expected.getSuppressed().length);
            assertSame(closeFailure, expected.getSuppressed()[0]);
        }
        assertTrue(output.closed);
        gzip.close();
    }

    @Test
    public void interruptedDrainRestoresInterruptStatusAndClosesOutput() throws Exception {
        CountDownLatch workerStarted = new CountDownLatch(1);
        CountDownLatch releaseWorker = new CountDownLatch(1);
        TrackingOutputStream output = new TrackingOutputStream();
        ParallelGzipOutputStream gzip = new ParallelGzipOutputStream(output, 6, 4, 1, (source, length, level) -> {
            workerStarted.countDown();
            try {
                releaseWorker.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("worker interrupted", e);
            }
            return ParallelGzipOutputStream.compressMember(source, length, level);
        });
        gzip.write(new byte[] {1, 2, 3, 4});
        AtomicReference<IOException> failure = new AtomicReference<>();
        AtomicBoolean interruptRestored = new AtomicBoolean();
        Thread closer = new Thread(() -> {
            try {
                gzip.close();
            } catch (IOException e) {
                failure.set(e);
                interruptRestored.set(Thread.currentThread().isInterrupted());
            }
        });
        closer.start();
        assertTrue(workerStarted.await(5, TimeUnit.SECONDS));
        closer.interrupt();
        closer.join(5_000);
        releaseWorker.countDown();

        assertFalse("Close thread did not terminate", closer.isAlive());
        assertEquals("Interrupted while compressing gzip content", failure.get().getMessage());
        assertTrue(interruptRestored.get());
        assertTrue(output.closed);
    }

    @Test
    public void invalidConfigurationAndWritesAreRejected() throws Exception {
        assertInvalid(() -> new ParallelGzipOutputStream(new ByteArrayOutputStream(), 6, 0, 1));
        assertInvalid(() -> new ParallelGzipOutputStream(new ByteArrayOutputStream(), 6, 1, 0));
        assertInvalid(() -> new ParallelGzipOutputStream(new ByteArrayOutputStream(), -2, 1, 1));
        assertInvalid(() -> new ParallelGzipOutputStream(new ByteArrayOutputStream(), 10, 1, 1));
        assertInvalid(() -> Archiver.builder().gzipCompressionThreads(0));
        assertInvalid(() -> Archiver.builder().gzipCompressionLevel(10));

        ParallelGzipOutputStream gzip = new ParallelGzipOutputStream(new ByteArrayOutputStream(), 6, 8, 1);
        gzip.close();
        try {
            gzip.write(1);
            fail("Expected a closed stream to reject writes");
        } catch (IOException expected) {
            assertEquals("Stream is closed", expected.getMessage());
        }
        try {
            gzip.write(new byte[1], 1, 1);
            fail("Expected invalid array bounds");
        } catch (IndexOutOfBoundsException expected) {
            // Expected.
        }
    }

    private byte[] compress(byte[] content, int threads, int chunkSize) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ParallelGzipOutputStream gzip = new ParallelGzipOutputStream(output, 6, chunkSize, threads)) {
            gzip.write(content);
        }
        return output.toByteArray();
    }

    private byte[] decompress(byte[] content) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (InputStream input = new GzipCompressorInputStream(new ByteArrayInputStream(content), true)) {
            byte[] buffer = new byte[256];
            int count;
            while ((count = input.read(buffer)) != -1) {
                output.write(buffer, 0, count);
            }
        }
        return output.toByteArray();
    }

    private void assertInvalid(ThrowingRunnable runnable) throws Exception {
        try {
            runnable.run();
            fail("Expected invalid configuration to fail");
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
    }

    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private static class TrackingOutputStream extends OutputStream {

        private final ByteArrayOutputStream delegate = new ByteArrayOutputStream();
        boolean closed;

        @Override
        public void write(int value) {
            delegate.write(value);
        }

        @Override
        public void write(byte[] bytes, int offset, int length) {
            delegate.write(bytes, offset, length);
        }

        @Override
        public void close() throws IOException {
            closed = true;
        }
    }

    private static final class FailingCloseOutputStream extends TrackingOutputStream {

        private final IOException failure;

        private FailingCloseOutputStream(IOException failure) {
            this.failure = failure;
        }

        @Override
        public void close() throws IOException {
            super.close();
            throw failure;
        }
    }
}
