/*
 * Copyright (c) 2014-2024 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 */
package ca.vanzyl.provisio.archive;

import static java.util.Objects.requireNonNull;

/** Immutable source metadata and callback-scoped content. */
public final class SourceEntry {

    private final String name;
    private final EntryType type;
    private final EntryContent content;
    private final int fileMode;
    private final long time;
    private final String linkTarget;

    private SourceEntry(String name, EntryType type, EntryContent content, int fileMode, long time, String linkTarget) {
        this.name = requireNonNull(name);
        this.type = requireNonNull(type);
        this.content = requireNonNull(content);
        this.fileMode = fileMode;
        this.time = time;
        this.linkTarget = linkTarget;
    }

    public static SourceEntry file(String name, EntryContent content, int fileMode, long time) {
        return new SourceEntry(name, EntryType.FILE, content, fileMode, time, null);
    }

    public static SourceEntry directory(String name, int fileMode, long time) {
        return new SourceEntry(name, EntryType.DIRECTORY, EntryContents.empty(), fileMode, time, null);
    }

    public static SourceEntry symbolicLink(String name, String target, int fileMode, long time) {
        return new SourceEntry(
                name, EntryType.SYMBOLIC_LINK, EntryContents.empty(), fileMode, time, requireNonNull(target));
    }

    public static SourceEntry hardLink(String name, String target, int fileMode, long time) {
        return new SourceEntry(
                name, EntryType.HARD_LINK, EntryContents.empty(), fileMode, time, requireNonNull(target));
    }

    SourceEntry withContent(EntryContent replacement) {
        if (type != EntryType.FILE) {
            throw new IllegalStateException("Only file entries have replaceable content");
        }
        return file(name, replacement, fileMode, time);
    }

    public String getName() {
        return name;
    }

    public EntryType getType() {
        return type;
    }

    public EntryContent getContent() {
        return content;
    }

    public int getFileMode() {
        return fileMode;
    }

    public long getTime() {
        return time;
    }

    public String getLinkTarget() {
        return linkTarget;
    }

    public long getSize() {
        return content.size();
    }

    public boolean isDirectory() {
        return type == EntryType.DIRECTORY;
    }

    public boolean isSymbolicLink() {
        return type == EntryType.SYMBOLIC_LINK;
    }

    public boolean isHardLink() {
        return type == EntryType.HARD_LINK;
    }
}
