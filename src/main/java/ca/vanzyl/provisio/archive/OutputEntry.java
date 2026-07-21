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
    private final int userId;
    private final int groupId;
    private final String userName;
    private final String groupName;

    private OutputEntry(
            String name,
            EntryType type,
            EntryContent content,
            int fileMode,
            long time,
            String linkTarget,
            int userId,
            int groupId,
            String userName,
            String groupName) {
        this.name = requireNonNull(name);
        this.type = requireNonNull(type);
        this.content = requireNonNull(content);
        this.fileMode = fileMode;
        this.time = time;
        this.linkTarget = linkTarget;
        this.userId = userId;
        this.groupId = groupId;
        this.userName = userName;
        this.groupName = groupName;
    }

    static OutputEntry from(
            String name,
            SourceEntry source,
            int fileMode,
            long time,
            String linkTarget,
            int userId,
            int groupId,
            String userName,
            String groupName) {
        return new OutputEntry(
                name,
                source.getType(),
                source.getContent(),
                fileMode,
                time,
                linkTarget,
                userId,
                groupId,
                userName,
                groupName);
    }

    static OutputEntry hardLink(String name, String target, OutputEntry original) {
        return new OutputEntry(
                name,
                EntryType.HARD_LINK,
                EntryContents.empty(),
                original.fileMode,
                original.time,
                requireNonNull(target),
                original.userId,
                original.groupId,
                original.userName,
                original.groupName);
    }

    OutputEntry withContent(EntryContent content) {
        if (type != EntryType.FILE) {
            throw new IllegalStateException("Only file entries have replaceable content");
        }
        return new OutputEntry(name, type, content, fileMode, time, linkTarget, userId, groupId, userName, groupName);
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

    int getUserId() {
        return userId;
    }

    int getGroupId() {
        return groupId;
    }

    String getUserName() {
        return userName;
    }

    String getGroupName() {
        return groupName;
    }
}
