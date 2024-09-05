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
import java.io.OutputStream;
import java.nio.file.Path;

/**
 * Enhanced entry processor.
 *
 * @since 1.0.2
 */
public interface UnarchivingEnhancedEntryProcessor {
    /**
     * Invoked when UnArchiver is about to "write" to this entry designated with given name.
     * <p>
     * Note: this may NOT be the final name of the written entry! Things like "flatten" happen after this.
     *
     * @param name the "target entry" name.
     */
    default String targetName(String name) {
        return name;
    }

    /**
     * Invoked when UnArchiver is about to "read" from this entry designated with name (ie link source).
     * <p>
     * Note: this may NOT be the final name of the read entry! Things like "flatten" happen after this.
     *
     * @param name the "source entry" name.
     */
    default String sourceName(String name) {
        return name;
    }

    /**
     * Invoked on UnArchiver IO action. Some sort of filtering may happen here. Default is "copy" operation.
     *
     * @param entryName the entry name created, never {@code null}
     * @param inputStream the input, never {@code null}
     * @param outputStream the output, never {@code null}
     */
    default void processStream(String entryName, InputStream inputStream, OutputStream outputStream)
            throws IOException {
        inputStream.transferTo(outputStream);
    }

    /**
     * Invoked after UnArchiver entry creation action. The final entry name and actual target path is provided. This
     * method is invoked after each entry processed.
     *
     * @param entryName the final entry name created, never {@code null}
     * @param target the actual path of target (maybe file, symlink, hardlink or a directory), never {@code null}
     */
    default void processed(String entryName, Path target) throws IOException {
        // nothing
    }
}
