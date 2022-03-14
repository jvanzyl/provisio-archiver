package ca.vanzyl.provisio.archive;

import static org.junit.Assert.assertTrue;

import ca.vanzyl.provisio.archive.generator.JarArtifactGenerator;
import ca.vanzyl.provisio.archive.generator.ArtifactLayout;
import java.io.File;
import org.codehaus.plexus.util.FileUtils;
import org.junit.Test;

public class HardLinkInTarGzTest extends FileSystemAssert {

  @Test
  public void validateHardlinksCanBeReadAndWritten() throws Exception {

    File target = new File(getBasedir(), "target");

    File tarGzDirectory = new File(getBasedir(), "target/hardlink");
    if (tarGzDirectory.exists()) {
      FileUtils.deleteDirectory(tarGzDirectory);
    }

    File fileDuplicatedInTarGzArchive = new File(target, "foo-1.0.jar");
    if (!fileDuplicatedInTarGzArchive.getParentFile().exists()) {
      fileDuplicatedInTarGzArchive.getParentFile().mkdirs();
    }

    JarArtifactGenerator jarFileGenerator = new JarArtifactGenerator(fileDuplicatedInTarGzArchive, 5); // 5242880
    jarFileGenerator.generate();

    ArtifactLayout artifactLayout = new ArtifactLayout(tarGzDirectory)
        .entry("1/foo-1.0.jar", fileDuplicatedInTarGzArchive)
        .entry("2/foo-1.0.jar", fileDuplicatedInTarGzArchive)
        .entry("3/foo-1.0.jar", fileDuplicatedInTarGzArchive)
        .entry("4/foo-1.0.jar", fileDuplicatedInTarGzArchive)
        .entry("5/foo-1.0.jar", fileDuplicatedInTarGzArchive)
        .entry("6/same.txt", "super") // same name, different content
        .entry("7/same.txt", "monkey"); // same name, different content
    artifactLayout.build();

    Archiver archiver = Archiver.builder()
        .normalize(true)
        .hardLinkIncludes("**/*.jar")
        .posixLongFileMode(true)
        .build();

    File archive = new File(getBasedir(), "target/hardlink.tar.gz");
    archiver.archive(archive, tarGzDirectory);

    //
    // Make sure there are the correct number of entries and that the sizes are correct
    // with the hardlinks being used in the archive.
    //
    ArchiveValidator validator = new TarGzArchiveValidator(archive);
    validator.showEntries();
    
    validator.assertNumberOfEntriesInArchive(15);

    validator.assertEntries(
        "hardlink/",
        "hardlink/1/",
        "hardlink/1/foo-1.0.jar",
        "hardlink/2/",
        "hardlink/2/foo-1.0.jar",
        "hardlink/3/",
        "hardlink/3/foo-1.0.jar",
        "hardlink/4/",
        "hardlink/4/foo-1.0.jar",
        "hardlink/5/",
        "hardlink/5/foo-1.0.jar",
        "hardlink/6/",
        "hardlink/6/same.txt",
        "hardlink/7/",
        "hardlink/7/same.txt"
    );

    //
    // Make sure that of N entries that are the same only the first has a size > 0
    //
    validator.assertSizeOfEntryInArchive("hardlink/1/foo-1.0.jar", 5307124);
    validator.assertSizeOfEntryInArchive("hardlink/2/foo-1.0.jar", 0);
    validator.assertSizeOfEntryInArchive("hardlink/3/foo-1.0.jar", 0);
    validator.assertSizeOfEntryInArchive("hardlink/4/foo-1.0.jar", 0);
    validator.assertSizeOfEntryInArchive("hardlink/5/foo-1.0.jar", 0);
    validator.assertSizeOfEntryInArchive("hardlink/6/same.txt", 5);
    validator.assertSizeOfEntryInArchive("hardlink/7/same.txt", 6);

    //
    // We'll assume some compression even on the random content and the tar.gz should be smaller
    // than the large JAR in the archive.
    //
    assertTrue(archive.length() < fileDuplicatedInTarGzArchive.length());

    //
    // Now let's unpack and make sure the hardlinks are preserved on unpacking
    //
    UnArchiver unArchiver = UnArchiver.builder().build();
    File unpackedTarGzDirectory = new File(getBasedir(), "target/hardlink-unpacked");
    if (unpackedTarGzDirectory.exists()) {
      FileUtils.deleteDirectory(unpackedTarGzDirectory);
    }

    unArchiver.unarchive(archive, unpackedTarGzDirectory);

    assertFileIsHardLink(unpackedTarGzDirectory, "hardlink/2/foo-1.0.jar");
    assertFileIsHardLink(unpackedTarGzDirectory, "hardlink/3/foo-1.0.jar");
    assertFileIsHardLink(unpackedTarGzDirectory, "hardlink/4/foo-1.0.jar");
    assertFileIsHardLink(unpackedTarGzDirectory, "hardlink/5/foo-1.0.jar");
    assertFileIsNotHardLink(unpackedTarGzDirectory, "hardlink/6/same.txt");
    assertFileIsNotHardLink(unpackedTarGzDirectory, "hardlink/7/same.txt");

    // And now unpack it again and make sure it overwrites the files
    unArchiver.unarchive(archive, unpackedTarGzDirectory);

    // Now unpack the archive with hardlink deref'ing enabled and make sure there are no hardlinks
    UnArchiver hardlinkDerefUnArchiver = UnArchiver.builder().dereferenceHardlinks(true).build();
    File unpackedTarGzDirectoryForHardlinkDeref = new File(getBasedir(), "target/hardlink-unpacked-hardlink-deref");
    if (unpackedTarGzDirectoryForHardlinkDeref.exists()) {
      FileUtils.deleteDirectory(unpackedTarGzDirectoryForHardlinkDeref);
    }
    hardlinkDerefUnArchiver.unarchive(archive, unpackedTarGzDirectoryForHardlinkDeref);
    assertFileIsNotHardLink(unpackedTarGzDirectoryForHardlinkDeref, "hardlink/2/foo-1.0.jar");
    assertFileIsNotHardLink(unpackedTarGzDirectoryForHardlinkDeref, "hardlink/3/foo-1.0.jar");
    assertFileIsNotHardLink(unpackedTarGzDirectoryForHardlinkDeref, "hardlink/4/foo-1.0.jar");
    assertFileIsNotHardLink(unpackedTarGzDirectoryForHardlinkDeref, "hardlink/5/foo-1.0.jar");
    assertFileIsNotHardLink(unpackedTarGzDirectoryForHardlinkDeref, "hardlink/6/same.txt");
    assertFileIsNotHardLink(unpackedTarGzDirectoryForHardlinkDeref, "hardlink/7/same.txt");
  }
}
