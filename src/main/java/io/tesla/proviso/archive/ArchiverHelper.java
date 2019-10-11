package io.tesla.proviso.archive;

import io.tesla.proviso.archive.Archiver.ArchiverBuilder;
import io.tesla.proviso.archive.UnArchiver.UnArchiverBuilder;
import io.tesla.proviso.archive.tar.TarGzArchiveHandler;
import io.tesla.proviso.archive.zip.ZipArchiveHandler;
import java.io.File;
import java.util.Collections;

public class ArchiverHelper {

  public static ArchiveHandler getArchiveHandler(File archive, ArchiverBuilder builder) {
    ArchiveHandler archiveHandler;
    if (isZip(archive)) {
      archiveHandler = new ZipArchiveHandler(archive);
    } else if (archive.getName().endsWith(".tgz") || archive.getName().endsWith("tar.gz")) {
      archiveHandler = new TarGzArchiveHandler(archive, builder.posixLongFileMode, builder.hardLinkIncludes, builder.hardLinkExcludes);
    } else {
      throw new RuntimeException("Cannot detect how to read " + archive.getName());
    }
    return archiveHandler;
  }

  public static ArchiveHandler getArchiveHandler(File archive, UnArchiverBuilder builder) {
    ArchiveHandler archiveHandler;
    if (isZip(archive)) {
      archiveHandler = new ZipArchiveHandler(archive);
    } else if (archive.getName().endsWith(".tgz") || archive.getName().endsWith("tar.gz")) {
      archiveHandler = new TarGzArchiveHandler(archive, builder.posixLongFileMode, Collections.emptyList(), Collections.emptyList());
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
