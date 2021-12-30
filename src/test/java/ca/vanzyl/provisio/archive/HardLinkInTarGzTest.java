package ca.vanzyl.provisio.archive;

import static org.junit.Assert.assertTrue;

import com.google.common.collect.Lists;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Random;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
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

    try (OutputStream outputStream = new FileOutputStream(fileDuplicatedInTarGzArchive)) {
      JarRandomContentProvider provider = new JarRandomContentProvider(5); // 5242880
      provider.writeTo(outputStream);
    }

    new TarGzLayoutBuilder(tarGzDirectory)
        .entry("1/foo-1.0.jar", fileDuplicatedInTarGzArchive)
        .entry("2/foo-1.0.jar", fileDuplicatedInTarGzArchive)
        .entry("3/foo-1.0.jar", fileDuplicatedInTarGzArchive)
        .entry("4/foo-1.0.jar", fileDuplicatedInTarGzArchive)
        .entry("5/foo-1.0.jar", fileDuplicatedInTarGzArchive)
        .entry("6/same.txt", "super") // same name, different content
        .entry("7/same.txt", "monkey") // same name, different content
        .build();

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

  public static class TarGzLayoutBuilder {

    File tarGzDirectory;
    List<Entry> entries = Lists.newArrayList();

    public TarGzLayoutBuilder(File tarGzDirectory) {
      this.tarGzDirectory = tarGzDirectory;
    }

    TarGzLayoutBuilder entry(String name, File file) {
      entries.add(new Entry(name, file));
      return this;
    }

    TarGzLayoutBuilder entry(String name, String content) {
      entries.add(new Entry(name, content));
      return this;
    }

    TarGzLayout build() throws IOException {
      for (Entry entry : entries) {
        File file = new File(tarGzDirectory, entry.name());
        if (entry.file() != null) {
          if (!file.getParentFile().exists()) {
            file.getParentFile().mkdirs();
          }
          Files.copy(entry.file().toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } else if (entry.content() != null) {
          if (!file.getParentFile().exists()) {
            file.getParentFile().mkdirs();
          }
          Files.copy(new ByteArrayInputStream(entry.content().getBytes()), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
      }
      return new TarGzLayout();
    }
  }

  public static class TarGzLayout {
  }

  public static class Entry {

    String name;
    File file;
    String content;

    public Entry(String name, File file) {
      this.name = name;
      this.file = file;
    }

    public Entry(String name, String content) {
      this.name = name;
      this.content = content;
    }

    public String name() {
      return name;
    }

    public File file() {
      return file;
    }

    public String content() {
      return content;
    }
  }

  static class JarRandomContentProvider {

    private static final int BUF_SIZE_BYTES = 4096;

    private final long length;

    public JarRandomContentProvider(long length) {
      this.length = length * bytesInMegabyte;
    }

    static long bytesInMegabyte = 1024L * 1024L;

    Random rnd = new Random(12345);

    public void writeTo(OutputStream os) throws IOException {
      int chunk = 100000;
      ZipOutputStream zos = new ZipOutputStream(os);
      int j = 1;
      for (int i = 0; i < length - 1; i += chunk) {
        // use no compression because it is much faster and random contents does not compress anyways
        zos.setLevel(Deflater.NO_COMPRESSION);
        ZipEntry ze = new ZipEntry("content-" + String.format("%03d", j));
        zos.putNextEntry(ze);
        writeBytesTo(zos, rnd.nextLong(), chunk);
        zos.closeEntry();
        // finish without closing to keep os open for other ContentProviders
        j++;
      }
      zos.finish();
    }

    private void writeBytesTo(OutputStream os, long seed, int chunk) throws IOException {
      final Random content = new Random(seed);
      final byte[] buf = new byte[BUF_SIZE_BYTES];
      for (int i = 0; i < chunk / BUF_SIZE_BYTES; i++) {
        content.nextBytes(buf);
        os.write(buf);
      }
      content.nextBytes(buf);
      os.write(buf, 0, chunk % BUF_SIZE_BYTES);
    }
  }
}
