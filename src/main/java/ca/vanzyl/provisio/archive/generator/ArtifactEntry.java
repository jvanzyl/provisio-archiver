/*
 * Copyright (c) 2014-2024 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 */
package ca.vanzyl.provisio.archive.generator;

import java.nio.file.Path;

public class ArtifactEntry {

    String name;
    Path file;
    String content;
    boolean executable;

    public ArtifactEntry(String name, Path file) {
        this.name = name;
        this.file = file;
    }

    public ArtifactEntry(String name, String content) {
        this(name, content, false);
    }

    public ArtifactEntry(String name, String content, boolean executable) {
        this.name = name;
        this.content = content;
        this.executable = executable;
    }

    public String name() {
        return name;
    }

    public Path file() {
        return file;
    }

    public String content() {
        return content;
    }
}
