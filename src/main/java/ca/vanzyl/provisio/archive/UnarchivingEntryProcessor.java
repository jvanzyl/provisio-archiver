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
import org.apache.commons.io.IOUtils;

/** Customizes entry names, file content, and completion handling during extraction. */
public interface UnarchivingEntryProcessor {

    /**
     * Returns the output name for an archive entry before flattening is applied.
     *
     * @param name target entry name
     */
    default String targetName(String name) {
        return name;
    }

    /**
     * Returns the output name used to resolve a hard-link source.
     *
     * @param name source entry name
     */
    default String sourceName(String name) {
        return name;
    }

    /**
     * Copies or transforms one regular file.
     *
     * @param entryName source entry name
     * @param inputStream source content
     * @param outputStream destination content
     */
    default void processStream(String entryName, InputStream inputStream, OutputStream outputStream)
            throws IOException {
        IOUtils.copyLarge(inputStream, outputStream);
    }

    /**
     * Runs after an entry has been created.
     *
     * @param entryName final output entry name
     * @param target actual output path
     */
    default void processed(String entryName, Path target) throws IOException {
        // nothing
    }
}
