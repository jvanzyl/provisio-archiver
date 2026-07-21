/*
 * Copyright (c) 2014-2024 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 */
package ca.vanzyl.provisio.archive.perms;

import static java.nio.file.attribute.PosixFilePermission.GROUP_EXECUTE;
import static java.nio.file.attribute.PosixFilePermission.GROUP_READ;
import static java.nio.file.attribute.PosixFilePermission.GROUP_WRITE;
import static java.nio.file.attribute.PosixFilePermission.OTHERS_EXECUTE;
import static java.nio.file.attribute.PosixFilePermission.OTHERS_READ;
import static java.nio.file.attribute.PosixFilePermission.OTHERS_WRITE;
import static java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE;
import static java.nio.file.attribute.PosixFilePermission.OWNER_READ;
import static java.nio.file.attribute.PosixFilePermission.OWNER_WRITE;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.Set;

/** Internal conversion between POSIX permissions and archive mode bits. */
public final class FileModes {

    public static final int EXECUTABLE_FILE = 0100755;

    private FileModes() {}

    public static int makeExecutable(int mode) {
        return mode | 0111;
    }

    public static int read(Path file) {
        try {
            return fromPermissions(Files.getPosixFilePermissions(file));
        } catch (IOException | UnsupportedOperationException e) {
            return -1;
        }
    }

    public static Set<PosixFilePermission> toPermissions(int mode) {
        Set<PosixFilePermission> permissions = EnumSet.noneOf(PosixFilePermission.class);
        addIfSet(permissions, mode, 0400, OWNER_READ);
        addIfSet(permissions, mode, 0200, OWNER_WRITE);
        addIfSet(permissions, mode, 0100, OWNER_EXECUTE);
        addIfSet(permissions, mode, 040, GROUP_READ);
        addIfSet(permissions, mode, 020, GROUP_WRITE);
        addIfSet(permissions, mode, 010, GROUP_EXECUTE);
        addIfSet(permissions, mode, 04, OTHERS_READ);
        addIfSet(permissions, mode, 02, OTHERS_WRITE);
        addIfSet(permissions, mode, 01, OTHERS_EXECUTE);
        return permissions;
    }

    public static String toUnix(int mode) {
        char[] unix = "----------".toCharArray();
        setIfPresent(unix, mode, 0400, 1, 'r');
        setIfPresent(unix, mode, 0200, 2, 'w');
        setIfPresent(unix, mode, 0100, 3, 'x');
        setIfPresent(unix, mode, 040, 4, 'r');
        setIfPresent(unix, mode, 020, 5, 'w');
        setIfPresent(unix, mode, 010, 6, 'x');
        setIfPresent(unix, mode, 04, 7, 'r');
        setIfPresent(unix, mode, 02, 8, 'w');
        setIfPresent(unix, mode, 01, 9, 'x');
        return new String(unix);
    }

    static int fromPermissions(Set<PosixFilePermission> permissions) {
        int mode = 0;
        mode = setIfPresent(mode, permissions, OWNER_READ, 0400);
        mode = setIfPresent(mode, permissions, OWNER_WRITE, 0200);
        mode = setIfPresent(mode, permissions, OWNER_EXECUTE, 0100);
        mode = setIfPresent(mode, permissions, GROUP_READ, 040);
        mode = setIfPresent(mode, permissions, GROUP_WRITE, 020);
        mode = setIfPresent(mode, permissions, GROUP_EXECUTE, 010);
        mode = setIfPresent(mode, permissions, OTHERS_READ, 04);
        mode = setIfPresent(mode, permissions, OTHERS_WRITE, 02);
        return setIfPresent(mode, permissions, OTHERS_EXECUTE, 01);
    }

    private static int setIfPresent(
            int mode, Set<PosixFilePermission> permissions, PosixFilePermission permission, int bit) {
        return permissions.contains(permission) ? mode | bit : mode;
    }

    private static void addIfSet(
            Set<PosixFilePermission> permissions, int mode, int bit, PosixFilePermission permission) {
        if ((mode & bit) == bit) {
            permissions.add(permission);
        }
    }

    private static void setIfPresent(char[] unix, int mode, int bit, int index, char value) {
        if ((mode & bit) == bit) {
            unix[index] = value;
        }
    }
}
