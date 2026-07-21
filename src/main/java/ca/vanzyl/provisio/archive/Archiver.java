/*
 * Copyright (c) 2014-2024 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 */
package ca.vanzyl.provisio.archive;

import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class Archiver {

    static final long DOS_EPOCH_IN_JAVA_TIME = 315561600000L;
    // ZIP timestamps have a resolution of 2 seconds.
    // see http://www.info-zip.org/FAQ.html#limits
    static final long MINIMUM_TIMESTAMP_INCREMENT = 2000L;
    private final ArchiveOptions options;

    private Archiver(ArchiverBuilder builder) {
        options = new ArchiveOptions(builder);
    }

    public void archive(Path archive, Path... sourceDirectories) throws IOException {
        archive(archive, Sources.directories(sourceDirectories));
    }

    public void archive(Path archive, Source... sources) throws IOException {
        SourceSpec[] sourceSpecs = new SourceSpec[sources.length];
        for (int i = 0; i < sources.length; i++) {
            sourceSpecs[i] = SourceSpec.of(sources[i]);
        }
        archive(archive, sourceSpecs);
    }

    public void archive(Path archive, SourceSpec... sources) throws IOException {
        Path output = requireNonNull(archive).toAbsolutePath();
        Files.createDirectories(output.getParent());
        Path temporary = Files.createTempFile(
                output.getParent(), ".provisio-" + output.getFileName().toString() + "-", ".tmp");
        boolean completed = false;
        try {
            writeArchive(archive, temporary, sources);
            moveIntoPlace(temporary, output);
            completed = true;
        } finally {
            if (!completed) {
                Files.deleteIfExists(temporary);
            }
        }
    }

    private void writeArchive(Path formatSource, Path output, SourceSpec... sources) throws IOException {
        ArchiveFormat format = ArchiveFormat.detect(formatSource);
        try (ArchiveSession session = new ArchiveSession(output, format, options)) {
            for (SourceSpec sourceSpec : sources) {
                session.add(sourceSpec);
            }
            session.finish();
        }
    }

    private void moveIntoPlace(Path temporary, Path output) throws IOException {
        try {
            Files.move(temporary, output, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temporary, output, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static ArchiverBuilder builder() {
        return new ArchiverBuilder();
    }

    public static final class ArchiverBuilder {

        List<String> executables = new ArrayList<>();
        ReproducibilityPolicy reproducibilityPolicy = ReproducibilityPolicy.PRESERVE;
        EntryOrder entryOrder = EntryOrder.SOURCE;
        ContentIdentityMode contentIdentityMode = ContentIdentityMode.VERIFIED;
        boolean posixLongFileMode;
        List<String> hardLinkIncludes = new ArrayList<>();
        List<String> hardLinkExcludes = new ArrayList<>();
        int gzipCompressionThreads = GzipCompressionOptions.DEFAULT_THREADS;
        int gzipCompressionLevel = GzipCompressionOptions.DEFAULT_LEVEL;

        /** Selects preserved or normalized archive metadata independently of entry ordering. */
        public ArchiverBuilder reproducibility(ReproducibilityPolicy reproducibilityPolicy) {
            this.reproducibilityPolicy = requireNonNull(reproducibilityPolicy);
            return this;
        }

        /**
         * Selects source-order streaming or name-sorted output independently of metadata normalization.
         *
         * @param entryOrder output entry order
         */
        public ArchiverBuilder entryOrder(EntryOrder entryOrder) {
            this.entryOrder = requireNonNull(entryOrder);
            return this;
        }

        /**
         * Selects verified or metadata-only identity for eligible tar hard links.
         *
         * @param contentIdentityMode content identity policy
         */
        public ArchiverBuilder contentIdentity(ContentIdentityMode contentIdentityMode) {
            this.contentIdentityMode = requireNonNull(contentIdentityMode);
            return this;
        }

        public ArchiverBuilder executable(String... executables) {
            return executable(Collections.unmodifiableList(new ArrayList<>(Arrays.asList(executables))));
        }

        public ArchiverBuilder executable(Iterable<String> executables) {
            executables.forEach(this.executables::add);
            return this;
        }

        public ArchiverBuilder posixLongFileMode(boolean posixLongFileMode) {
            this.posixLongFileMode = posixLongFileMode;
            return this;
        }

        /** Sets the number of bounded gzip compression workers used for tar.gz output. */
        public ArchiverBuilder gzipCompressionThreads(int threads) {
            new GzipCompressionOptions(threads, gzipCompressionLevel);
            gzipCompressionThreads = threads;
            return this;
        }

        /** Sets the DEFLATE compression level from -1 (default) through 9 (best compression). */
        public ArchiverBuilder gzipCompressionLevel(int level) {
            new GzipCompressionOptions(gzipCompressionThreads, level);
            gzipCompressionLevel = level;
            return this;
        }

        public ArchiverBuilder hardLinkIncludes(String... hardLinkIncludes) {
            return hardLinkIncludes(Collections.unmodifiableList(new ArrayList<>(Arrays.asList(hardLinkIncludes))));
        }

        public ArchiverBuilder hardLinkIncludes(Iterable<String> hardLinkIncludes) {
            hardLinkIncludes.forEach(this.hardLinkIncludes::add);
            return this;
        }

        public ArchiverBuilder hardLinkExcludes(String... hardLinkExcludes) {
            return hardLinkExcludes(Collections.unmodifiableList(new ArrayList<>(Arrays.asList(hardLinkExcludes))));
        }

        public ArchiverBuilder hardLinkExcludes(Iterable<String> hardLinkExcludes) {
            hardLinkExcludes.forEach(this.hardLinkExcludes::add);
            return this;
        }

        public Archiver build() {
            return new Archiver(this);
        }
    }
}
