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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

public class ArtifactLayout {

    private final Path tarGzDirectory;
    private final List<ArtifactEntry> entries;

    public ArtifactLayout(Path tarGzDirectory) {
        this.tarGzDirectory = tarGzDirectory;
        this.entries = new ArrayList<>();
    }

    public ArtifactLayout(Path tarGzDirectory, List<ArtifactEntry> entries) {
        this.tarGzDirectory = tarGzDirectory;
        this.entries = entries;
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
            Path file = tarGzDirectory.resolve(entry.name());
            if (entry.file() != null) {
                Files.createDirectories(file.getParent());
                Files.copy(entry.file(), file, StandardCopyOption.REPLACE_EXISTING);
            } else if (entry.content() != null) {
                Files.createDirectories(file.getParent());
                Files.copy(
                        new ByteArrayInputStream(entry.content().getBytes()),
                        file,
                        StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    public Path directory() {
        return tarGzDirectory;
    }
}
