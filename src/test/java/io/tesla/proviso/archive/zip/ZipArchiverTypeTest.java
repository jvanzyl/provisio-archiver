package io.tesla.proviso.archive.zip;

import io.tesla.proviso.archive.ArchiverTypeTest;
import io.tesla.proviso.archive.ArchiverValidator;

import java.io.File;
import java.io.IOException;

public class ZipArchiverTypeTest extends ArchiverTypeTest {
  protected String getArchiveExtension() {
    return "zip";
  }

  @Override
  protected ArchiverValidator validator(File archive) throws IOException {
    return new ZipArchiveValidator(archive);
  }   
}
