/*
 * Copyright (c) 2014-2024 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 */
package ca.vanzyl.provisio.archive;

import ca.vanzyl.provisio.archive.perms.FileMode;

/** Controls archive metadata independently of entry ordering. */
public enum ReproducibilityPolicy {
    /** Preserve source timestamps and permissions. */
    PRESERVE,

    /** Use fixed timestamps, canonical permissions, zero ownership, and deterministic container headers. */
    NORMALIZED;

    long timestamp(String name, SourceEntry source) {
        if (this == PRESERVE) {
            return source.getTime();
        }
        return name.endsWith(".class")
                ? Archiver.DOS_EPOCH_IN_JAVA_TIME + Archiver.MINIMUM_TIMESTAMP_INCREMENT
                : Archiver.DOS_EPOCH_IN_JAVA_TIME;
    }

    int fileMode(SourceEntry source, boolean selectedExecutable) {
        int sourceMode = source.getFileMode();
        if (this == PRESERVE) {
            if (sourceMode != -1 && selectedExecutable) {
                return FileMode.makeExecutable(sourceMode);
            }
            if (sourceMode == -1 && selectedExecutable) {
                return FileMode.EXECUTABLE_FILE.getBits();
            }
            return sourceMode;
        }

        switch (source.getType()) {
            case DIRECTORY:
                return 0755;
            case SYMBOLIC_LINK:
                return 0777;
            case FILE:
            case HARD_LINK:
                return selectedExecutable || isExecutable(sourceMode) ? 0755 : 0644;
            default:
                throw new IllegalArgumentException("Unsupported entry type " + source.getType());
        }
    }

    int userId() {
        return this == NORMALIZED ? 0 : -1;
    }

    int groupId() {
        return this == NORMALIZED ? 0 : -1;
    }

    String userName() {
        return this == NORMALIZED ? "" : null;
    }

    String groupName() {
        return this == NORMALIZED ? "" : null;
    }

    private static boolean isExecutable(int mode) {
        return mode != -1 && (mode & 0111) != 0;
    }
}
