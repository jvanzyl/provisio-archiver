package io.tesla.proviso.archive.tar;

import java.io.File;

import io.tesla.proviso.archive.ArchiverTypeTest;
import io.tesla.proviso.archive.ArchiverValidator;

public class TarGzArchiverTypeTest extends ArchiverTypeTest {
  @Override
  protected String getArchiveExtension() {
    return "tar.gz";
  }

  @Override
  protected ArchiverValidator validator(File archive) throws Exception {
    return new TarGzArchiveValidator(archive);
  }
}
