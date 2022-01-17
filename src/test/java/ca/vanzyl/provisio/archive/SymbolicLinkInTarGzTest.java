package ca.vanzyl.provisio.archive;

import static java.nio.file.Files.isSymbolicLink;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import org.codehaus.plexus.util.FileUtils;
import org.junit.Test;

public class SymbolicLinkInTarGzTest extends FileSystemAssert {

  @Test
  public void symboliclinksAreUnarchivedInTarGzFiles() throws Exception {
    File tarGzDirectory = new File(getBasedir(), "target/symboliclink-tgz");
    if (tarGzDirectory.exists()) {
      FileUtils.deleteDirectory(tarGzDirectory);
    }

    File archive = getSourceArchive("jenv.tar.gz");
    UnArchiver unArchiver = UnArchiver.builder().useRoot(false).build();
    unArchiver.unarchive(archive, tarGzDirectory);

    Path link = tarGzDirectory.toPath().resolve("bin/jenv");
    Path target = Files.readSymbolicLink(link);
    assertTrue(isSymbolicLink(link));
    assertEquals("../libexec/jenv", target.toString());
  }

  @Test
  public void symboliclinksAreUnarchivedInZipFiles() throws Exception {
    File tarGzDirectory = new File(getBasedir(), "target/symboliclink-zip");
    if (tarGzDirectory.exists()) {
      FileUtils.deleteDirectory(tarGzDirectory);
    }

    File archive = getSourceArchive("jenv.zip");
    UnArchiver unArchiver = UnArchiver.builder().useRoot(false).build();
    unArchiver.unarchive(archive, tarGzDirectory);

    Path link = tarGzDirectory.toPath().resolve("bin/jenv");
    Path target = Files.readSymbolicLink(link);
    assertTrue(isSymbolicLink(link));
    assertEquals("../libexec/jenv", target.toString());
  }
}
