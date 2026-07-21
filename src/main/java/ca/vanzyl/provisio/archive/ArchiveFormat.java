/*
 * Copyright (c) 2014-2024 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 */
package ca.vanzyl.provisio.archive;

import ca.vanzyl.provisio.archive.tar.TarGzArchiveSource;
import ca.vanzyl.provisio.archive.zip.ZipArchiveSource;
import java.io.IOException;
import java.nio.file.Path;

enum ArchiveFormat {
    TAR_GZ,
    ZIP;

    static ArchiveFormat detect(Path archive) {
        String name = archive.getFileName().toString();
        if (name.endsWith(".zip")
                || name.endsWith(".jar")
                || name.endsWith(".war")
                || name.endsWith(".hpi")
                || name.endsWith(".jpi")) {
            return ZIP;
        }
        if (name.endsWith(".tgz") || name.endsWith("tar.gz")) {
            return TAR_GZ;
        }
        throw new IllegalArgumentException("Cannot detect archive format for " + name);
    }

    Source openSource(Path archive) {
        if (this == ZIP) {
            return new ZipArchiveSource(archive);
        }
        return new TarGzArchiveSource(archive);
    }

    ArchiveWriter openWriter(Path output, boolean posixLongFileMode, GzipCompressionOptions gzipCompression)
            throws IOException {
        if (this == ZIP) {
            return new ZipArchiveWriter(output);
        }
        return new TarGzArchiveWriter(output, posixLongFileMode, gzipCompression);
    }
}
