package ca.vanzyl.provisio.archive;

import ca.vanzyl.provisio.archive.tar.TarGzArchiveSource;
import java.io.File;

public class TarGzArchiveValidator extends AbstractArchiveValidator {

  public TarGzArchiveValidator(File archive) throws Exception {
    super(new TarGzArchiveSource(archive));
  }
}
