package ca.vanzyl.provisio.archive;

import ca.vanzyl.provisio.archive.source.FileSource;
import java.io.File;
import org.junit.Test;

public class FileEntrySourceTest extends FileSystemAssert {

  @Test
  public void readIndividualFilesToMakeArchive() throws Exception {
    Archiver archiver = Archiver.builder() //
        .build();

    File archive = getTargetArchive("archive-from-files.tar.gz");
    Source s0 = new FileSource(getSourceFile("0.txt"));
    Source s1 = new FileSource(getSourceFile("1.txt"));
    Source s2 = new FileSource(getSourceFile("2.txt"));
    Source s3 = new FileSource(getSourceFile("3.txt"));
    Source s4 = new FileSource(getSourceFile("4.txt"));
    archiver.archive(archive, s0, s1, s2, s3, s4);
    ArchiveValidator validator = new TarGzArchiveValidator(archive);
    validator.assertEntries("0.txt", "1.txt", "2.txt", "3.txt", "4.txt");
    validator.assertContentOfEntryInArchive("0.txt", "0");
    validator.assertContentOfEntryInArchive("1.txt", "1");
    validator.assertContentOfEntryInArchive("2.txt", "2");
    validator.assertContentOfEntryInArchive("3.txt", "3");
    validator.assertContentOfEntryInArchive("4.txt", "4");
  }
}
