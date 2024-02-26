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
import org.apache.commons.compress.archivers.ArchiveEntry;

public interface ExtendedArchiveEntry extends ArchiveEntry {
    void setFileMode(int mode);

    int getFileMode();

    void setSize(long size);

    void setTime(long time);

    void writeEntry(OutputStream outputStream) throws IOException;

    InputStream getInputStream() throws IOException;

    boolean isSymbolicLink();

    String getSymbolicLinkPath();

    boolean isHardLink();

    String getHardLinkPath();

    boolean isExecutable();

    long getTime();
}
