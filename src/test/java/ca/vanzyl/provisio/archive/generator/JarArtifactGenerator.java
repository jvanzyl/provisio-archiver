/*
 * Copyright (c) 2014-2024 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 */
package ca.vanzyl.provisio.archive.generator;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Generates deterministic, incompressible JAR payloads for archive tests. */
public final class JarArtifactGenerator {

    private static final int BUFFER_SIZE = 4096;
    private static final long BYTES_PER_MEGABYTE = 1024L * 1024L;

    private final long size;
    private final Random random = new Random(12345);
    private final Path file;

    public JarArtifactGenerator(Path file, long sizeInMegabytes) {
        this.file = file;
        this.size = sizeInMegabytes * BYTES_PER_MEGABYTE;
    }

    public void generate() throws IOException {
        try (OutputStream output = Files.newOutputStream(file)) {
            writeTo(output);
        }
    }

    private void writeTo(OutputStream output) throws IOException {
        int chunk = 100000;
        ZipOutputStream zip = new ZipOutputStream(output);
        int entryNumber = 1;
        for (int offset = 0; offset < size - 1; offset += chunk) {
            zip.setLevel(Deflater.NO_COMPRESSION);
            zip.putNextEntry(new ZipEntry("content-" + String.format("%03d", entryNumber++)));
            writeBytesTo(zip, random.nextLong(), chunk);
            zip.closeEntry();
        }
        zip.finish();
    }

    private void writeBytesTo(OutputStream output, long seed, int count) throws IOException {
        Random content = new Random(seed);
        byte[] buffer = new byte[BUFFER_SIZE];
        for (int offset = 0; offset < count / BUFFER_SIZE; offset++) {
            content.nextBytes(buffer);
            output.write(buffer);
        }
        content.nextBytes(buffer);
        output.write(buffer, 0, count % BUFFER_SIZE);
    }
}
