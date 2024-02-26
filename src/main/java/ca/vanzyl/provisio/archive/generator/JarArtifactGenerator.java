/*
 * Copyright (c) 2014-2024 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 */
package ca.vanzyl.provisio.archive.generator;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Random;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class JarArtifactGenerator implements ArtifactGenerator {

    private static final int BUF_SIZE_BYTES = 4096;
    private static final long bytesInMegabyte = 1024L * 1024L;

    private final long sizeInMegabytes;
    private final Random rnd;
    private final File file;

    public JarArtifactGenerator(File file, long sizeInMegabytes) {
        this.file = file;
        this.sizeInMegabytes = sizeInMegabytes * bytesInMegabyte;
        this.rnd = new Random(12345);
    }

    public void generate() throws IOException {
        try (OutputStream outputStream = new FileOutputStream(file)) {
            writeTo(outputStream);
        }
    }

    private void writeTo(OutputStream os) throws IOException {
        int chunk = 100000;
        ZipOutputStream zos = new ZipOutputStream(os);
        int j = 1;
        for (int i = 0; i < sizeInMegabytes - 1; i += chunk) {
            // use no compression because it is much faster and random content does not compress anyway
            zos.setLevel(Deflater.NO_COMPRESSION);
            ZipEntry ze = new ZipEntry("content-" + String.format("%03d", j));
            zos.putNextEntry(ze);
            writeBytesTo(zos, rnd.nextLong(), chunk);
            zos.closeEntry();
            // finish without closing to keep os open for other ContentProviders
            j++;
        }
        zos.finish();
    }

    private void writeBytesTo(OutputStream os, long seed, int chunk) throws IOException {
        final Random content = new Random(seed);
        final byte[] buf = new byte[BUF_SIZE_BYTES];
        for (int i = 0; i < chunk / BUF_SIZE_BYTES; i++) {
            content.nextBytes(buf);
            os.write(buf);
        }
        content.nextBytes(buf);
        os.write(buf, 0, chunk % BUF_SIZE_BYTES);
    }
}
