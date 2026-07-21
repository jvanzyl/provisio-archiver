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

    void forEachEntry(EntryConsumer consumer) throws IOException;

    boolean isDirectory();

    @Override
    void close() throws IOException;

    @FunctionalInterface
    interface EntryConsumer {
        void accept(ExtendedArchiveEntry entry) throws IOException;
    }
}
