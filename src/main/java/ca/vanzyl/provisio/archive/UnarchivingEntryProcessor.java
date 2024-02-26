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

public interface UnarchivingEntryProcessor {
    String processName(String name);

    void processStream(String entryName, InputStream inputStream, OutputStream outputStream) throws IOException;
}
