/*
 * Copyright (c) 2014-2024 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 */
package ca.vanzyl.provisio.archive.perms;

/*
 * Copyright (C) 2007, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2006-2008, Shawn O. Pearce <spearce@spearce.org>
 * and other copyright owners as documented in the project's IP log.
 *
 * This program and the accompanying materials are made available
 * under the terms of the Eclipse Distribution License v1.0 which
 * accompanies this distribution, is reproduced below, and is
 * available at http://www.eclipse.org/org/documents/edl-v10.php
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or
 * without modification, are permitted provided that the following
 * conditions are met:
 *
 * - Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above
 *   copyright notice, this list of conditions and the following
 *   disclaimer in the documentation and/or other materials provided
 *   with the distribution.
 *
 * - Neither the name of the Eclipse Foundation, Inc. nor the
 *   names of its contributors may be used to endorse or promote
 *   products derived from this software without specific prior
 *   written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND
 * CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

import static java.nio.file.attribute.PosixFilePermission.GROUP_EXECUTE;
import static java.nio.file.attribute.PosixFilePermission.GROUP_READ;
import static java.nio.file.attribute.PosixFilePermission.GROUP_WRITE;
import static java.nio.file.attribute.PosixFilePermission.OTHERS_EXECUTE;
import static java.nio.file.attribute.PosixFilePermission.OTHERS_READ;
import static java.nio.file.attribute.PosixFilePermission.OTHERS_WRITE;
import static java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE;
import static java.nio.file.attribute.PosixFilePermission.OWNER_READ;
import static java.nio.file.attribute.PosixFilePermission.OWNER_WRITE;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.Set;

/**
 * Constants describing various file modes recognized by GIT.
 * <p>
 * GIT uses a subset of the available UNIX file permission bits. The <code>FileMode</code> class provides access to constants defining the modes actually used by GIT.
 * </p>
 */
public abstract class FileMode {
    /**
     * Mask to apply to a file mode to obtain its type bits.
     *
     * @see #TYPE_TREE
     * @see #TYPE_SYMLINK
     * @see #TYPE_FILE
     * @see #TYPE_GITLINK
     * @see #TYPE_MISSING
     */
    public static final int TYPE_MASK = 0170000;

    /** Bit pattern for {@link #TYPE_MASK} matching {@link #TREE}. */
    public static final int TYPE_TREE = 0040000;

    /** Bit pattern for {@link #TYPE_MASK} matching {@link #SYMLINK}. */
    public static final int TYPE_SYMLINK = 0120000;

    /** Bit pattern for {@link #TYPE_MASK} matching {@link #REGULAR_FILE}. */
    public static final int TYPE_FILE = 0100000;

    /** Bit pattern for {@link #TYPE_MASK} matching {@link #GITLINK}. */
    public static final int TYPE_GITLINK = 0160000;

    /** Bit pattern for {@link #TYPE_MASK} matching {@link #MISSING}. */
    public static final int TYPE_MISSING = 0000000;

    /** Mode indicating an entry is a tree (aka directory). */
    @SuppressWarnings("synthetic-access")
    public static FileMode TREE = new FileMode(TYPE_TREE) {
        public boolean equals(final int modeBits) {
            return (modeBits & TYPE_MASK) == TYPE_TREE;
        }
    };

    /** Mode indicating an entry is a symbolic link. */
    @SuppressWarnings("synthetic-access")
    public static FileMode SYMLINK = new FileMode(TYPE_SYMLINK) {
        public boolean equals(final int modeBits) {
            return (modeBits & TYPE_MASK) == TYPE_SYMLINK;
        }
    };

    /** Mode indicating an entry is a non-executable file. */
    @SuppressWarnings("synthetic-access")
    public static FileMode REGULAR_FILE = new FileMode(0100644) {
        public boolean equals(final int modeBits) {
            return (modeBits & TYPE_MASK) == TYPE_FILE && (modeBits & 0111) == 0;
        }
    };

    /** Mode indicating an entry is an executable file. */
    @SuppressWarnings("synthetic-access")
    public static FileMode EXECUTABLE_FILE = new FileMode(0100755) {
        public boolean equals(final int modeBits) {
            return (modeBits & TYPE_MASK) == TYPE_FILE && (modeBits & 0111) != 0;
        }
    };

    /** Mode indicating an entry is a submodule commit in another repository. */
    @SuppressWarnings("synthetic-access")
    public static FileMode GITLINK = new FileMode(TYPE_GITLINK) {
        public boolean equals(final int modeBits) {
            return (modeBits & TYPE_MASK) == TYPE_GITLINK;
        }
    };

    /** Mode indicating an entry is missing during parallel walks. */
    @SuppressWarnings("synthetic-access")
    public static FileMode MISSING = new FileMode(TYPE_MISSING) {
        public boolean equals(final int modeBits) {
            return modeBits == 0;
        }
    };

    /**
     * Convert a set of mode bits into a FileMode enumerated value.
     *
     * @param bits the mode bits the caller has somehow obtained.
     * @return the FileMode instance that represents the given bits.
     */
    public static FileMode fromBits(final int bits) {
        switch (bits & TYPE_MASK) {
            case TYPE_MISSING:
                if (bits == 0) return MISSING;
                break;
            case TYPE_TREE:
                return TREE;
            case TYPE_FILE:
                if ((bits & 0111) != 0) return EXECUTABLE_FILE;
                return REGULAR_FILE;
            case TYPE_SYMLINK:
                return SYMLINK;
            case TYPE_GITLINK:
                return GITLINK;
        }

        return new FileMode(bits) {
            @Override
            public boolean equals(final int a) {
                return bits == a;
            }
        };
    }

    private final byte[] octalBytes;

    private final int modeBits;

    private FileMode(int mode) {
        modeBits = mode;
        if (mode != 0) {
            final byte[] tmp = new byte[10];
            int p = tmp.length;

            while (mode != 0) {
                tmp[--p] = (byte) ('0' + (mode & 07));
                mode >>= 3;
            }

            octalBytes = new byte[tmp.length - p];
            for (int k = 0; k < octalBytes.length; k++) {
                octalBytes[k] = tmp[p + k];
            }
        } else {
            octalBytes = new byte[] {'0'};
        }
    }

    /**
     * Test a file mode for equality with this {@link FileMode} object.
     *
     * @param modebits
     * @return true if the mode bits represent the same mode as this object
     */
    public abstract boolean equals(final int modebits);

    /**
     * Copy this mode as a sequence of octal US-ASCII bytes.
     * <p>
     * The mode is copied as a sequence of octal digits using the US-ASCII character encoding. The sequence does not use a leading '0' prefix to indicate octal notation. This method is suitable for
     * generation of a mode string within a GIT tree object.
     * </p>
     *
     * @param os stream to copy the mode to.
     * @throws IOException the stream encountered an error during the copy.
     */
    public void copyTo(final OutputStream os) throws IOException {
        os.write(octalBytes);
    }

    /**
     * Copy this mode as a sequence of octal US-ASCII bytes.
     *
     * The mode is copied as a sequence of octal digits using the US-ASCII character encoding. The sequence does not use a leading '0' prefix to indicate octal notation. This method is suitable for
     * generation of a mode string within a GIT tree object.
     *
     * @param buf buffer to copy the mode to.
     * @param ptr position within {@code buf} for first digit.
     */
    public void copyTo(byte[] buf, int ptr) {
        System.arraycopy(octalBytes, 0, buf, ptr, octalBytes.length);
    }

    /**
     * @return the number of bytes written by {@link #copyTo(OutputStream)}.
     */
    public int copyToLength() {
        return octalBytes.length;
    }

    /** Format this mode as an octal string (for debugging only). */
    public String toString() {
        return Integer.toOctalString(modeBits);
    }

    /**
     * @return The mode bits as an integer.
     */
    public int getBits() {
        return modeBits;
    }

    //
    // Utilities for dealing with file modes
    //

    public static int makeExecutable(int fileMode) {
        return fileMode | 0111;
    }

    public static int getFileMode(File file) {
        Set<PosixFilePermission> posixPermissions;
        try {
            posixPermissions = Files.getPosixFilePermissions(file.toPath());
        } catch (IOException | UnsupportedOperationException e) {
            return -1;
        }
        int result = 0;
        if (posixPermissions.contains(OWNER_READ)) {
            result = result | 0400;
        }
        if (posixPermissions.contains(OWNER_WRITE)) {
            result = result | 0200;
        }
        if (posixPermissions.contains(OWNER_EXECUTE)) {
            result = result | 0100;
        }
        if (posixPermissions.contains(GROUP_READ)) {
            result = result | 040;
        }
        if (posixPermissions.contains(GROUP_WRITE)) {
            result = result | 020;
        }
        if (posixPermissions.contains(GROUP_EXECUTE)) {
            result = result | 010;
        }
        if (posixPermissions.contains(OTHERS_READ)) {
            result = result | 04;
        }
        if (posixPermissions.contains(OTHERS_WRITE)) {
            result = result | 02;
        }
        if (posixPermissions.contains(OTHERS_EXECUTE)) {
            result = result | 01;
        }
        return result;
    }

    public static Set<PosixFilePermission> toPermissionsSet(int mode) {
        Set<PosixFilePermission> result = EnumSet.noneOf(PosixFilePermission.class);
        if (isSet(mode, 0400)) {
            result.add(OWNER_READ);
        }
        if (isSet(mode, 0200)) {
            result.add(OWNER_WRITE);
        }
        if (isSet(mode, 0100)) {
            result.add(OWNER_EXECUTE);
        }

        if (isSet(mode, 040)) {
            result.add(GROUP_READ);
        }
        if (isSet(mode, 020)) {
            result.add(GROUP_WRITE);
        }
        if (isSet(mode, 010)) {
            result.add(GROUP_EXECUTE);
        }
        if (isSet(mode, 04)) {
            result.add(OTHERS_READ);
        }
        if (isSet(mode, 02)) {
            result.add(OTHERS_WRITE);
        }
        if (isSet(mode, 01)) {
            result.add(OTHERS_EXECUTE);
        }
        return result;
    }

    //
    // drwxrwxrwx
    //
    public static String toUnix(int mode) {
        char[] unix = new char[10];
        for (int i = 0; i < 10; i++) {
            unix[i] = '-';
        }
        if (isSet(mode, 0400)) {
            unix[1] = 'r';
        }
        if (isSet(mode, 0200)) {
            unix[2] = 'w';
        }
        if (isSet(mode, 0100)) {
            unix[3] = 'x';
        }
        if (isSet(mode, 040)) {
            unix[4] = 'r';
        }
        if (isSet(mode, 020)) {
            unix[5] = 'w';
        }
        if (isSet(mode, 010)) {
            unix[6] = 'x';
        }
        if (isSet(mode, 04)) {
            unix[7] = 'r';
        }
        if (isSet(mode, 02)) {
            unix[8] = 'w';
        }
        if (isSet(mode, 01)) {
            unix[9] = 'x';
        }
        return new String(unix);
    }

    private static boolean isSet(int mode, int bit) {
        return (mode & bit) == bit;
    }
}
