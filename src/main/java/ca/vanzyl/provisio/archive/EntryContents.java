/*
 * Copyright (c) 2014-2024 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 */
package ca.vanzyl.provisio.archive;

import static java.util.Objects.requireNonNull;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.zip.CRC32;

/** Factory methods for source content that is independent of a source traversal. */
public final class EntryContents {

    private static final EntryContent EMPTY = of(new byte[0]);

    private EntryContents() {}

    public static EntryContent empty() {
        return EMPTY;
    }

    public static EntryContent of(byte[] bytes) {
        byte[] content = Arrays.copyOf(requireNonNull(bytes), bytes.length);
        CRC32 crc32 = new CRC32();
        crc32.update(content);
        return new EntryContent() {
            @Override
            public InputStream open() {
                return new ByteArrayInputStream(content);
            }

            @Override
            public long size() {
                return content.length;
            }

            @Override
            public long crc32() {
                return crc32.getValue();
            }

            @Override
            public boolean isRepeatable() {
                return true;
            }
        };
    }

    public static EntryContent of(Path file) throws IOException {
        Path content = requireNonNull(file);
        long size = Files.size(content);
        return new EntryContent() {
            @Override
            public InputStream open() throws IOException {
                return Files.newInputStream(content);
            }

            @Override
            public long size() {
                return size;
            }

            @Override
            public boolean isRepeatable() {
                return true;
            }
        };
    }
}
