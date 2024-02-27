/*
 * Copyright (c) 2014-2024 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 */
package ca.vanzyl.provisio.archive.generator;

import ca.vanzyl.provisio.archive.Archiver;
import java.io.File;
import java.io.IOException;
import java.util.List;

// TODO: create a simple in-memory source for easily generating test artifacts
public class TarGzArtifactGenerator implements ArtifactGenerator {

    private final File artifact;
    private final ArtifactLayout artifactLayout;

    public TarGzArtifactGenerator(File artifact, File layoutDirectory) {
        this.artifact = artifact;
        this.artifactLayout = new ArtifactLayout(layoutDirectory);
    }

    public TarGzArtifactGenerator(File artifact, File layoutDirectory, List<ArtifactEntry> artifactEntries) {
        this.artifact = artifact;
        this.artifactLayout = new ArtifactLayout(layoutDirectory, artifactEntries);
    }

    public TarGzArtifactGenerator(File artifact, ArtifactLayout layout) {
        this.artifact = artifact;
        this.artifactLayout = layout;
    }

    public TarGzArtifactGenerator entry(String name, String content) {
        artifactLayout.entry(name, content);
        return this;
    }

    public void generate() throws IOException {
        artifactLayout.build();
        Archiver archiver = Archiver.builder()
                .useRoot(false)
                .normalize(true)
                .posixLongFileMode(true)
                .build();
        archiver.archive(artifact, artifactLayout.directory());
    }
}
