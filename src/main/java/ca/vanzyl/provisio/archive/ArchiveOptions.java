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
    private final boolean normalize;
    private final EntryOrder entryOrder;
    private final boolean posixLongFileMode;
    private final List<String> hardLinkIncludes;
    private final List<String> hardLinkExcludes;

    ArchiveOptions(Archiver.ArchiverBuilder builder) {
        executables = immutableCopy(builder.executables);
        normalize = builder.normalize;
        entryOrder = builder.entryOrder;
        posixLongFileMode = builder.posixLongFileMode;
        hardLinkIncludes = immutableCopy(builder.hardLinkIncludes);
        hardLinkExcludes = immutableCopy(builder.hardLinkExcludes);
    }

    List<String> executables() {
        return executables;
    }

    boolean normalize() {
        return normalize;
    }

    EntryOrder entryOrder() {
        return entryOrder;
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

    private static <T> List<T> immutableCopy(List<T> values) {
        return Collections.unmodifiableList(new ArrayList<>(values));
    }
}
