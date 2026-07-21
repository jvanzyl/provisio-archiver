/*
 * Copyright (c) 2014-2024 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 */
package ca.vanzyl.provisio.archive;

import static java.util.Objects.requireNonNull;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

/** Compresses fixed-size gzip members concurrently and writes them in submission order. */
final class ParallelGzipOutputStream extends OutputStream {

    interface ChunkCompressor {
        CompressedMember compress(byte[] source, int length, int compressionLevel) throws IOException;
    }

    private static final int GZIP_MAGIC_FIRST = 0x1f;
    private static final int GZIP_MAGIC_SECOND = 0x8b;
    private static final int DEFLATE_METHOD = 8;
    private static final int UNKNOWN_OPERATING_SYSTEM = 255;

    private final OutputStream output;
    private final ExecutorService executor;
    private final Deque<Future<CompressedMember>> pending = new ArrayDeque<>();
    private final int compressionLevel;
    private final int chunkSize;
    private final int maximumPending;
    private final ChunkCompressor compressor;

    private byte[] chunk;
    private int position;
    private boolean submitted;
    private boolean closed;
    private IOException failure;

    ParallelGzipOutputStream(OutputStream output, int compressionLevel, int chunkSize, int threads) {
        this(output, compressionLevel, chunkSize, threads, ParallelGzipOutputStream::compressMember);
    }

    ParallelGzipOutputStream(
            OutputStream output, int compressionLevel, int chunkSize, int threads, ChunkCompressor compressor) {
        this.output = requireNonNull(output);
        this.compressor = requireNonNull(compressor);
        if (threads < 1 || threads > 256) {
            throw new IllegalArgumentException("threads must be between 1 and 256");
        }
        if (chunkSize < 1) {
            throw new IllegalArgumentException("chunk size must be positive");
        }
        if (compressionLevel < Deflater.DEFAULT_COMPRESSION || compressionLevel > Deflater.BEST_COMPRESSION) {
            throw new IllegalArgumentException("compression level must be between -1 and 9");
        }
        this.compressionLevel = compressionLevel;
        this.chunkSize = chunkSize;
        maximumPending = Math.multiplyExact(threads, 2);
        executor = Executors.newFixedThreadPool(threads, new CompressorThreadFactory());
        chunk = new byte[chunkSize];
    }

    @Override
    public void write(int value) throws IOException {
        ensureWritable();
        chunk[position++] = (byte) value;
        if (position == chunkSize) {
            submit();
        }
    }

    @Override
    public void write(byte[] source, int offset, int length) throws IOException {
        requireNonNull(source);
        if (offset < 0 || length < 0 || offset > source.length - length) {
            throw new IndexOutOfBoundsException();
        }
        ensureWritable();
        int remaining = length;
        int sourcePosition = offset;
        while (remaining > 0) {
            int count = Math.min(remaining, chunkSize - position);
            System.arraycopy(source, sourcePosition, chunk, position, count);
            position += count;
            sourcePosition += count;
            remaining -= count;
            if (position == chunkSize) {
                submit();
            }
        }
    }

    @Override
    public void flush() throws IOException {
        ensureWritable();
        try {
            while (!pending.isEmpty() && pending.peekFirst().isDone()) {
                writeNextMember();
            }
            output.flush();
        } catch (IOException e) {
            fail(e);
            throw e;
        }
    }

    @Override
    public void close() throws IOException {
        if (closed) {
            return;
        }
        closed = true;
        IOException closeFailure = null;
        try {
            if (failure == null) {
                if (position > 0 || !submitted) {
                    submitChunk();
                }
                drainTo(0);
            }
        } catch (IOException e) {
            closeFailure = e;
        } finally {
            cancelPending();
            executor.shutdownNow();
            try {
                output.close();
            } catch (IOException e) {
                if (closeFailure == null) {
                    closeFailure = e;
                } else {
                    closeFailure.addSuppressed(e);
                }
            }
        }
        if (closeFailure != null) {
            throw closeFailure;
        }
    }

    int pendingChunks() {
        return pending.size();
    }

    int maximumPendingChunks() {
        return maximumPending;
    }

    private void ensureWritable() throws IOException {
        if (closed) {
            throw new IOException("Stream is closed");
        }
        if (failure != null) {
            throw new IOException("Gzip compression has failed", failure);
        }
    }

    private void submit() throws IOException {
        submitChunk();
        try {
            drainTo(maximumPending);
        } catch (IOException e) {
            fail(e);
            throw e;
        }
    }

    private void submitChunk() {
        byte[] source = chunk;
        int length = position;
        pending.addLast(executor.submit(() -> compressor.compress(source, length, compressionLevel)));
        submitted = true;
        chunk = new byte[chunkSize];
        position = 0;
    }

    private void drainTo(int limit) throws IOException {
        while (pending.size() > limit) {
            writeNextMember();
        }
    }

    private void writeNextMember() throws IOException {
        Future<CompressedMember> member = pending.removeFirst();
        try {
            member.get().writeTo(output);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while compressing gzip content", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IOException) {
                throw (IOException) cause;
            }
            throw new IOException("Failed to compress gzip content", cause);
        }
    }

    private void fail(IOException exception) {
        failure = exception;
        cancelPending();
    }

    private void cancelPending() {
        for (Future<CompressedMember> future : pending) {
            future.cancel(true);
        }
        pending.clear();
    }

    static CompressedMember compressMember(byte[] source, int length, int compressionLevel) throws IOException {
        MemberOutputStream member = new MemberOutputStream(compressedMemberCapacity(length));
        member.write(GZIP_MAGIC_FIRST);
        member.write(GZIP_MAGIC_SECOND);
        member.write(DEFLATE_METHOD);
        member.write(0); // flags
        writeLittleEndian(member, 0); // modification time
        member.write(0); // extra flags
        member.write(UNKNOWN_OPERATING_SYSTEM);

        Deflater deflater = new Deflater(compressionLevel, true);
        try {
            try (DeflaterOutputStream deflaterStream = new DeflaterOutputStream(member, deflater, 8192)) {
                deflaterStream.write(source, 0, length);
            }
        } finally {
            deflater.end();
        }

        CRC32 crc32 = new CRC32();
        crc32.update(source, 0, length);
        writeLittleEndian(member, crc32.getValue());
        writeLittleEndian(member, length);
        return member.compressedMember();
    }

    private static int compressedMemberCapacity(int length) {
        long capacity = (long) length + (length >>> 12) + (length >>> 14) + (length >>> 25) + 64;
        return (int) Math.min(capacity, Integer.MAX_VALUE - 8L);
    }

    private static void writeLittleEndian(ByteArrayOutputStream output, long value) {
        output.write((int) (value & 0xff));
        output.write((int) ((value >>> 8) & 0xff));
        output.write((int) ((value >>> 16) & 0xff));
        output.write((int) ((value >>> 24) & 0xff));
    }

    static final class CompressedMember {

        private final byte[] buffer;
        private final int length;

        private CompressedMember(byte[] buffer, int length) {
            this.buffer = buffer;
            this.length = length;
        }

        byte[] buffer() {
            return buffer;
        }

        int length() {
            return length;
        }

        private void writeTo(OutputStream output) throws IOException {
            output.write(buffer, 0, length);
        }
    }

    private static final class MemberOutputStream extends ByteArrayOutputStream {

        private MemberOutputStream(int capacity) {
            super(capacity);
        }

        private CompressedMember compressedMember() {
            return new CompressedMember(buf, count);
        }
    }

    private static final class CompressorThreadFactory implements ThreadFactory {

        private static final AtomicInteger POOL_IDS = new AtomicInteger();
        private final AtomicInteger threadIds = new AtomicInteger();
        private final int poolId = POOL_IDS.incrementAndGet();

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "provisio-gzip-" + poolId + "-" + threadIds.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    }
}
