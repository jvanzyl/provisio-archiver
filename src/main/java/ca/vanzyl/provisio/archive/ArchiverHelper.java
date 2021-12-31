package ca.vanzyl.provisio.archive;

import ca.vanzyl.provisio.archive.tar.TarGzArchiveHandler;
import ca.vanzyl.provisio.archive.zip.ZipArchiveHandler;
import ca.vanzyl.provisio.archive.Archiver.ArchiverBuilder;

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

  public static ArchiveHandler getArchiveHandler(File archive, UnArchiver.UnArchiverBuilder builder) {
    ArchiveHandler archiveHandler;
    if (isZip(archive)) {
      archiveHandler = new ZipArchiveHandler(archive);
    } else if (isGzip(archive)) {
      archiveHandler = new TarGzArchiveHandler(archive, builder.posixLongFileMode, Collections.emptyList(), Collections.emptyList());
    } else {
      throw new RuntimeException("Cannot detect how to read " + archive.getName());
    }
    return archiveHandler;
  }


  private static boolean isZip(File file) {
    return file.getName().endsWith(".zip") ||
        file.getName().endsWith(".jar") ||
        file.getName().endsWith(".war") ||
        file.getName().endsWith(".hpi") ||
        file.getName().endsWith(".jpi");
  }

  private static boolean isGzip(File file) {
    return file.getName().endsWith(".tgz") ||
        file.getName().endsWith("tar.gz");
  }

}
