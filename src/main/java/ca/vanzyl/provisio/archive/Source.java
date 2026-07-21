/*
 * Copyright (c) 2014-2024 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 */
package ca.vanzyl.provisio.archive;

import java.io.Closeable;
import java.io.IOException;

public interface Source extends Closeable {

    /**
     * Visits entries in source order.
     *
     * <p>An entry and its content are valid only for the duration of the consumer callback. A consumer that needs the
     * content afterward must copy or spool it before returning.
     */
    void forEachEntry(EntryConsumer consumer) throws IOException;

    boolean isDirectory();

    @Override
    void close() throws IOException;

    @FunctionalInterface
    interface EntryConsumer {
        void accept(SourceEntry entry) throws IOException;
    }
}
