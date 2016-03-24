package io.tesla.proviso.archive;

import java.io.File;

public class ArchiveValidatorHelper {

  public static ArchiveValidator getArchiveValidator(File archive) throws Exception {
    ArchiveValidator archiveHandler;
    if (isZip(archive)) {
      archiveHandler = new ZipArchiveValidator(archive);
    } else if (archive.getName().endsWith(".tgz") || archive.getName().endsWith("tar.gz")) {
      archiveHandler = new TarGzArchiveValidator(archive);
    } else {
      throw new RuntimeException("Cannot detect how to create a validator for " + archive.getName());
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
