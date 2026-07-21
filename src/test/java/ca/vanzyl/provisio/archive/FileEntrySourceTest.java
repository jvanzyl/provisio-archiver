package ca.vanzyl.provisio.archive;

import java.io.File;
import org.junit.Test;

public class FileEntrySourceTest extends FileSystemAssert {

    @Test
    public void readIndividualFilesToMakeArchive() throws Exception {
        Archiver archiver = Archiver.builder() //
                .build();

        File archive = getTargetArchive("archive-from-files.tar.gz");
        Source s0 = Sources.file(getSourceFile("0.txt").toPath());
        Source s1 = Sources.file(getSourceFile("1.txt").toPath());
        Source s2 = Sources.file(getSourceFile("2.txt").toPath());
        Source s3 = Sources.file(getSourceFile("3.txt").toPath());
        Source s4 = Sources.file(getSourceFile("4.txt").toPath());
        archiver.archive(archive.toPath(), s0, s1, s2, s3, s4);
        ArchiveValidator validator = new TarGzArchiveValidator(archive);
        validator.assertEntries("0.txt", "1.txt", "2.txt", "3.txt", "4.txt");
        validator.assertContentOfEntryInArchive("0.txt", "0");
        validator.assertContentOfEntryInArchive("1.txt", "1");
        validator.assertContentOfEntryInArchive("2.txt", "2");
        validator.assertContentOfEntryInArchive("3.txt", "3");
        validator.assertContentOfEntryInArchive("4.txt", "4");
    }
}
