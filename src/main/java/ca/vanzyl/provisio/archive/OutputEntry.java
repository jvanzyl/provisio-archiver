/*
 * Copyright (c) 2014-2024 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 */
package ca.vanzyl.provisio.archive;

import static java.util.Objects.requireNonNull;

/** Immutable entry metadata already decided by archive assembly. */
final class OutputEntry {

    private final String name;
    private final EntryType type;
    private final EntryContent content;
    private final int fileMode;
    private final long time;
    private final String linkTarget;

    private OutputEntry(String name, EntryType type, EntryContent content, int fileMode, long time, String linkTarget) {
        this.name = requireNonNull(name);
        this.type = requireNonNull(type);
        this.content = requireNonNull(content);
        this.fileMode = fileMode;
        this.time = time;
        this.linkTarget = linkTarget;
    }

    static OutputEntry from(String name, SourceEntry source, int fileMode, long time, String linkTarget) {
        return new OutputEntry(name, source.getType(), source.getContent(), fileMode, time, linkTarget);
    }

    static OutputEntry hardLink(String name, String target, int fileMode, long time) {
        return new OutputEntry(
                name, EntryType.HARD_LINK, EntryContents.empty(), fileMode, time, requireNonNull(target));
    }

    String getName() {
        return name;
    }

    EntryType getType() {
        return type;
    }

    EntryContent getContent() {
        return content;
    }

    int getFileMode() {
        return fileMode;
    }

    long getTime() {
        return time;
    }

    String getLinkTarget() {
        return linkTarget;
    }
}
