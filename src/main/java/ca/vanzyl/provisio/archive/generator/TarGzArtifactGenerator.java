/*
 * Copyright (c) 2014-2024 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 */
package ca.vanzyl.provisio.archive.generator;

import ca.vanzyl.provisio.archive.Archiver;
import ca.vanzyl.provisio.archive.EntryOrder;
import ca.vanzyl.provisio.archive.ReproducibilityPolicy;
import ca.vanzyl.provisio.archive.SourceSpec;
import ca.vanzyl.provisio.archive.source.DirectorySource;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

// TODO: create a simple in-memory source for easily generating test artifacts
public class TarGzArtifactGenerator implements ArtifactGenerator {

    private final Path artifact;
    private final ArtifactLayout artifactLayout;

    public TarGzArtifactGenerator(Path artifact, Path layoutDirectory) {
        this.artifact = artifact;
        this.artifactLayout = new ArtifactLayout(layoutDirectory);
    }

    public TarGzArtifactGenerator(Path artifact, Path layoutDirectory, List<ArtifactEntry> artifactEntries) {
        this.artifact = artifact;
        this.artifactLayout = new ArtifactLayout(layoutDirectory, artifactEntries);
    }

    public TarGzArtifactGenerator(Path artifact, ArtifactLayout layout) {
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
                .reproducibility(ReproducibilityPolicy.NORMALIZED)
                .entryOrder(EntryOrder.NAME)
                .posixLongFileMode(true)
                .build();
        SourceSpec source = SourceSpec.builder(new DirectorySource(artifactLayout.directory()))
                .useRoot(false)
                .build();
        archiver.archive(artifact, source);
    }
}
