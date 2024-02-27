/*
 * Copyright (c) 2014-2024 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 */
package ca.vanzyl.provisio.archive;

import java.io.IOException;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.ArchiveOutputStream;

public interface ArchiveHandler {
    ArchiveOutputStream getOutputStream() throws IOException;

    ArchiveInputStream getInputStream() throws IOException;

    ExtendedArchiveEntry createEntryFor(String entryName, ExtendedArchiveEntry entry, boolean isExecutable);

    ExtendedArchiveEntry newEntry(String entryName, ExtendedArchiveEntry entry);

    Source getArchiveSource();
}
