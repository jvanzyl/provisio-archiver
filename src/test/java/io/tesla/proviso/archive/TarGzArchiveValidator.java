package io.tesla.proviso.archive;

import java.io.File;

import io.tesla.proviso.archive.tar.TarGzArchiveSource;
import java.io.IOException;

public class TarGzArchiveValidator extends AbstractArchiveValidator {

  public TarGzArchiveValidator(File archive) throws Exception {
    super(new TarGzArchiveSource(archive));
  }
}
