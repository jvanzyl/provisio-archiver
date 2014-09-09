package io.tesla.proviso.archive.zip;

import io.tesla.proviso.archive.AbstractArchiveValidator;
import io.tesla.proviso.archive.ArchiverValidator;

import java.io.File;
import java.io.IOException;

public class ZipArchiveValidator extends AbstractArchiveValidator implements ArchiverValidator {

  public ZipArchiveValidator(File archive) throws IOException {
    super(new ZipArchiveSource(archive));
  }

}
