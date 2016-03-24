package io.tesla.proviso.archive;

import java.io.File;

import io.tesla.proviso.archive.tar.TarGzArchiveSource;

public class TarGzArchiveValidator extends AbstractArchiveValidator {

  public TarGzArchiveValidator(File archive) throws Exception {
    super(new TarGzArchiveSource(archive));
  }

}
