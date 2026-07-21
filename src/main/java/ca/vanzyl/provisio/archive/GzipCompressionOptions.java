/*
 * Copyright (c) 2014-2024 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 */
package ca.vanzyl.provisio.archive;

import java.util.zip.Deflater;

/** Immutable bounded gzip compression settings. */
final class GzipCompressionOptions {

    static final int DEFAULT_THREADS =
            Math.max(1, Math.min(8, Runtime.getRuntime().availableProcessors()));
    static final int DEFAULT_LEVEL = Deflater.BEST_COMPRESSION;

    private final int threads;
    private final int level;

    GzipCompressionOptions(int threads, int level) {
        if (threads < 1 || threads > 256) {
            throw new IllegalArgumentException("gzip compression threads must be between 1 and 256");
        }
        if (level < Deflater.DEFAULT_COMPRESSION || level > Deflater.BEST_COMPRESSION) {
            throw new IllegalArgumentException("gzip compression level must be between -1 and 9");
        }
        this.threads = threads;
        this.level = level;
    }

    int threads() {
        return threads;
    }

    int level() {
        return level;
    }
}
