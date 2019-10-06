package io.tesla.proviso.archive;

import java.io.File;

import io.tesla.proviso.archive.tar.TarGzArchiveHandler;
import io.tesla.proviso.archive.zip.ZipArchiveHandler;

public class ArchiverHelper {

  public static ArchiveHandler getArchiveHandler(File archive, boolean posixLongFileMode, Archiver archiver) {
    ArchiveHandler archiveHandler;
    if (isZip(archive)) {
      archiveHandler = new ZipArchiveHandler(archive);
    } else if (archive.getName().endsWith(".tgz") || archive.getName().endsWith("tar.gz")) {
      archiveHandler = new TarGzArchiveHandler(archive, posixLongFileMode, archiver);
    } else {
      throw new RuntimeException("Cannot detect how to read " + archive.getName());
    }
    return archiveHandler;
  }

  private static boolean isZip(File file) {
    return file.getName().endsWith(".zip") || //
        file.getName().endsWith(".jar") || //
        file.getName().endsWith(".war") ||
        file.getName().endsWith(".hpi") ||
        file.getName().endsWith(".jpi");
  }
}
