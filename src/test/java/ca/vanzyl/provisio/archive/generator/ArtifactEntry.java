/*
 * Copyright (c) 2014-2024 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 */
package ca.vanzyl.provisio.archive.generator;

import java.nio.file.Path;

final class ArtifactEntry {

    private final String name;
    private final Path file;
    private final String content;

    ArtifactEntry(String name, Path file) {
        this.name = name;
        this.file = file;
        this.content = null;
    }

    ArtifactEntry(String name, String content) {
        this.name = name;
        this.file = null;
        this.content = content;
    }

    String name() {
        return name;
    }

    Path file() {
        return file;
    }

    String content() {
        return content;
    }
}
