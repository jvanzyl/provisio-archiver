package io.tesla.proviso.archive;

import java.io.File;
import java.io.IOException;

import io.tesla.proviso.archive.zip.ZipArchiveSource;

public class ZipArchiveValidator extends AbstractArchiveValidator {

  public ZipArchiveValidator(File archive) throws IOException {
    super(new ZipArchiveSource(archive));
  }
}
