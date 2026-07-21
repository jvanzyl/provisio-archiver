package ca.vanzyl.provisio.archive;

import java.io.File;

public class TarGzArchiveValidator extends AbstractArchiveValidator {

    public TarGzArchiveValidator(File archive) throws Exception {
        super(Sources.tarGz(archive.toPath()));
    }
}
