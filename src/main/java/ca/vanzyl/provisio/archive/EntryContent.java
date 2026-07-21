/*
 * Copyright (c) 2014-2024 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 */
package ca.vanzyl.provisio.archive;

import java.io.IOException;
import java.io.InputStream;

/** Content associated with a source entry. */
public interface EntryContent {

    /**
     * Opens the content while its {@link Source.EntryConsumer} callback is active.
     *
     * <p>A caller must close the returned stream. Source implementations may reject multiple opens or any access after
     * the callback returns.
     */
    InputStream open() throws IOException;

    /** Returns the uncompressed content size. */
    long size();

    /** Returns the CRC-32 when it is known without reading the content, or {@code -1}. */
    default long crc32() {
        return -1;
    }

    /** Returns whether the content can be opened more than once during its source callback. */
    default boolean isRepeatable() {
        return false;
    }
}
