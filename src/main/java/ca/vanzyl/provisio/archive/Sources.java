/*
 * Copyright (c) 2014-2024 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 */
package ca.vanzyl.provisio.archive;

import static java.util.Objects.requireNonNull;

import ca.vanzyl.provisio.archive.source.DirectorySource;
import ca.vanzyl.provisio.archive.source.FileSource;
import ca.vanzyl.provisio.archive.tar.TarGzArchiveSource;
import ca.vanzyl.provisio.archive.zip.ZipArchiveSource;
import java.nio.file.Path;

/** Creates built-in streaming archive sources without exposing their implementations. */
public final class Sources {

    private Sources() {}

    /** Creates a source rooted at one filesystem directory. */
    public static Source directory(Path directory) {
        return directories(directory);
    }

    /** Creates one source rooted at each supplied filesystem directory. */
    public static Source directories(Path... directories) {
        requireNonNull(directories, "directories");
        for (Path directory : directories) {
            requireNonNull(directory, "directory");
        }
        return new DirectorySource(directories);
    }

    /** Creates a source whose entry name is the file name. */
    public static Source file(Path file) {
        return new FileSource(requireNonNull(file, "file"));
    }

    /** Creates a source with an explicit archive entry name. */
    public static Source file(String entryName, Path file) {
        return new FileSource(requireNonNull(entryName, "entryName"), requireNonNull(file, "file"));
    }

    /** Creates a streaming source for a ZIP-compatible archive. */
    public static Source zip(Path archive) {
        return new ZipArchiveSource(requireNonNull(archive, "archive"));
    }

    /** Creates a streaming source for a gzip-compressed tar archive. */
    public static Source tarGz(Path archive) {
        return new TarGzArchiveSource(requireNonNull(archive, "archive"));
    }

    /** Creates a streaming source by detecting the format from the archive file name. */
    public static Source archive(Path archive) {
        Path input = requireNonNull(archive, "archive");
        return ArchiveFormat.detect(input).openSource(input);
    }
}
