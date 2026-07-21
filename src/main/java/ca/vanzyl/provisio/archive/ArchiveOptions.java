/*
 * Copyright (c) 2014-2024 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 */
package ca.vanzyl.provisio.archive;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable configuration shared by independent archive operations. */
final class ArchiveOptions {

    private final List<String> executables;
    private final ReproducibilityPolicy reproducibilityPolicy;
    private final EntryOrder entryOrder;
    private final ContentIdentityMode contentIdentityMode;
    private final boolean posixLongFileMode;
    private final List<String> hardLinkIncludes;
    private final List<String> hardLinkExcludes;
    private final GzipCompressionOptions gzipCompression;

    ArchiveOptions(Archiver.ArchiverBuilder builder) {
        executables = immutableCopy(builder.executables);
        reproducibilityPolicy = builder.reproducibilityPolicy;
        entryOrder = builder.entryOrder;
        contentIdentityMode = builder.contentIdentityMode;
        posixLongFileMode = builder.posixLongFileMode;
        hardLinkIncludes = immutableCopy(builder.hardLinkIncludes);
        hardLinkExcludes = immutableCopy(builder.hardLinkExcludes);
        gzipCompression = new GzipCompressionOptions(builder.gzipCompressionThreads, builder.gzipCompressionLevel);
    }

    List<String> executables() {
        return executables;
    }

    ReproducibilityPolicy reproducibilityPolicy() {
        return reproducibilityPolicy;
    }

    EntryOrder entryOrder() {
        return entryOrder;
    }

    ContentIdentityMode contentIdentityMode() {
        return contentIdentityMode;
    }

    boolean posixLongFileMode() {
        return posixLongFileMode;
    }

    List<String> hardLinkIncludes() {
        return hardLinkIncludes;
    }

    List<String> hardLinkExcludes() {
        return hardLinkExcludes;
    }

    GzipCompressionOptions gzipCompression() {
        return gzipCompression;
    }

    private static <T> List<T> immutableCopy(List<T> values) {
        return Collections.unmodifiableList(new ArrayList<>(values));
    }
}
