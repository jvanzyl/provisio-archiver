/*
 * Copyright (c) 2014-2024 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 */
package ca.vanzyl.provisio.archive.generator;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/** Filesystem-layout support for archive tests. */
public final class ArtifactLayout {

    private final Path directory;
    private final List<ArtifactEntry> entries = new ArrayList<>();

    public ArtifactLayout(Path directory) {
        this.directory = directory;
    }

    public ArtifactLayout entry(String name, Path file) {
        entries.add(new ArtifactEntry(name, file));
        return this;
    }

    public ArtifactLayout entry(String name, String content) {
        entries.add(new ArtifactEntry(name, content));
        return this;
    }

    public void build() throws IOException {
        for (ArtifactEntry entry : entries) {
            Path file = directory.resolve(entry.name());
            Files.createDirectories(file.getParent());
            if (entry.file() != null) {
                Files.copy(entry.file(), file, StandardCopyOption.REPLACE_EXISTING);
            } else {
                Files.copy(
                        new ByteArrayInputStream(entry.content().getBytes(StandardCharsets.UTF_8)),
                        file,
                        StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }
}
