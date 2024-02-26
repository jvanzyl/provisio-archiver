/*
 * Copyright (c) 2014-2024 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 */
package ca.vanzyl.provisio.archive;

import ca.vanzyl.provisio.archive.Archiver.ArchiverBuilder;
import ca.vanzyl.provisio.archive.UnArchiver.UnArchiverBuilder;
import ca.vanzyl.provisio.archive.tar.TarGzXzArchiveHandler;
import ca.vanzyl.provisio.archive.zip.ZipArchiveHandler;
import java.io.File;
import java.util.Collections;

public class ArchiverHelper {

    public static ArchiveHandler getArchiveHandler(File archive, ArchiverBuilder builder) {
        ArchiveHandler archiveHandler;
        if (isZip(archive)) {
            archiveHandler = new ZipArchiveHandler(archive);
        } else if (isTarGz(archive)) {
            archiveHandler = new TarGzXzArchiveHandler(
                    archive, builder.posixLongFileMode, builder.hardLinkIncludes, builder.hardLinkExcludes);
        } else {
            throw new RuntimeException("Cannot detect how to read " + archive.getName());
        }
        return archiveHandler;
    }

    public static ArchiveHandler getArchiveHandler(File archive, UnArchiverBuilder builder) {
        ArchiveHandler archiveHandler;
        if (isZip(archive)) {
            archiveHandler = new ZipArchiveHandler(archive);
        } else if (isTarGz(archive)) {
            archiveHandler = new TarGzXzArchiveHandler(
                    archive, builder.posixLongFileMode, Collections.emptyList(), Collections.emptyList());
        } else {
            throw new RuntimeException("Cannot detect how to read " + archive.getName());
        }
        return archiveHandler;
    }

    private static boolean isZip(File file) {
        return file.getName().endsWith(".zip")
                || file.getName().endsWith(".jar")
                || file.getName().endsWith(".war")
                || file.getName().endsWith(".hpi")
                || file.getName().endsWith(".jpi");
    }

    private static boolean isTarGz(File file) {
        return file.getName().endsWith(".tgz")
                || file.getName().endsWith("tar.gz")
                || file.getName().endsWith("tar.xz");
    }

    private static boolean isTarXz(File file) {
        return file.getName().endsWith(".txz") || file.getName().endsWith("tar.xz");
    }
}
