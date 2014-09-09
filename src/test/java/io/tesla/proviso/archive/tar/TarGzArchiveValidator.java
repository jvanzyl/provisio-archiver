package io.tesla.proviso.archive.tar;

import io.tesla.proviso.archive.AbstractArchiveValidator;
import io.tesla.proviso.archive.ArchiverValidator;

import java.io.File;

public class TarGzArchiveValidator extends AbstractArchiveValidator implements ArchiverValidator {

  public TarGzArchiveValidator(File archive) throws Exception {
    super(new TarGzArchiveSource(archive));
  }

}
