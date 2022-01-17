package ca.vanzyl.provisio.archive;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import ca.vanzyl.provisio.archive.perms.FileMode;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import org.codehaus.plexus.util.FileUtils;

public class FileSystemAssert {

  public static void assertDirectoryExists(File outputDirectory, String directoryName) {
    File directory = new File(outputDirectory, directoryName);
    assertTrue(String.format("We expect to find the directory %s, but it doesn't exist or is not a directory.", directoryName), directory.exists() && directory.isDirectory());
  }

  public static void assertDirectoryDoesNotExist(File outputDirectory, String directoryName) {
    File directory = new File(outputDirectory, directoryName);
    assertFalse(String.format("We expect not to find the directory %s, but it is there.", directoryName), directory.exists() && directory.isDirectory());
  }

  public static void assertFilesExists(File outputDirectory, String fileName) {
    File file = new File(outputDirectory, fileName);
    assertTrue(String.format("We expect to find the file %s, but it doesn't exist or is not a file.", fileName), file.exists() && file.isFile());
  }

  public static void assertPresenceAndSizeOf(File file, int size) {
    assertTrue(String.format("We expect to find the file %s, but it doesn't exist or is not a file.", file.getName()), file.exists() && file.isFile());
    assertEquals(String.format("We expect the file to be size = %s, but it not.", size), size, file.length());
  }

  public static void assertPresenceAndSizeOf(File outputDirectory, String fileName, int size) {
    File file = new File(outputDirectory, fileName);
    assertPresenceAndSizeOf(file, size);
  }

  public static void assertPresenceAndContentOf(File file, String expectedContent) throws IOException {
    assertTrue(String.format("We expect to find the file %s, but it doesn't exist or is not a file.", file.getName()), file.exists() && file.isFile());
    assertEquals(String.format("We expect the content of the file to be %s, but is not.", expectedContent), expectedContent, FileUtils.fileRead(file));
  }

  public static void assertPresenceAndContentOf(File outputDirectory, String fileName, String expectedContent) throws IOException {
    File file = new File(outputDirectory, fileName);
    assertPresenceAndContentOf(file, expectedContent);
  }

  public static void assertFileIsExecutable(File outputDirectory, String fileName) {
    File file = new File(outputDirectory, fileName);
    assertTrue(String.format("We expect to find the file %s, but it doesn't exist or is not executable.", fileName), file.exists() && file.isFile() && file.canExecute());
  }

  public static void assertFileIsHardLink(File outputDirectory, String fileName) throws Exception {
    File file = new File(outputDirectory, fileName);
    boolean hardlink = Files.getAttribute( file.toPath(), "unix:nlink" ).equals(5);
    assertTrue(String.format("We expect to find the file %s, but it doesn't exist or is not a hardlink.", fileName), file.exists() && file.isFile() && hardlink);
  }

  public static void assertFileIsNotHardLink(File outputDirectory, String fileName) throws Exception {
    File file = new File(outputDirectory, fileName);
    boolean hardlink = Files.getAttribute(file.toPath(), "unix:nlink" ).equals(5);
    assertFalse(String.format("We expect to find the file %s, but is a hardlink and it should not be.", fileName), file.exists() && file.isFile() && hardlink);
  }

  public static void assertFileMode(File outputDirectory, String string, String expectedUnix) {
    File f = new File(outputDirectory, string);
    String unix = FileMode.toUnix(FileMode.getFileMode(f));
    assertEquals(expectedUnix, unix);
  }

  //
  // Helper methods for tests
  //

  private static String basedir;

  public static String getBasedir() {
    if (null == basedir) {
      basedir = System.getProperty("basedir", new File("").getAbsolutePath());
    }
    return basedir;
  }

  public static File file(File outputDirectory, String fileName) {
    return new File(outputDirectory, fileName);
  }

  public static File getOutputDirectory() {
    return new File(getBasedir(), "target/archives");
  }

  public static File getOutputDirectory(String name) throws IOException {
    File outputDirectory = new File(getBasedir(), "target/archives/" + name);
    if (outputDirectory.exists()) {
      FileUtils.deleteDirectory(outputDirectory);
    }
    return outputDirectory;
  }

  public static File getSourceArchiveDirectory() {
    return new File(getBasedir(), "src/test/archives");
  }

  public static File getSourceArchive(String name) {
    return new File(getSourceArchiveDirectory(), name);
  }

  public static File getSourceFileDirectory() {
    return new File(getBasedir(), "src/test/files");
  }

  public static File getSourceFile(String name) {
    return new File(getSourceFileDirectory(), name);
  }

  public static File getTargetArchive(String name) {
    File archive = new File(getOutputDirectory(), name);
    if (!archive.getParentFile().exists()) {
      archive.getParentFile().mkdirs();
    }
    return archive;
  }

  public static File getArchiveProject(String name) {
    return new File(getBasedir(), String.format("src/test/archives/%s", name));
  }

  // Git no longer seems to checkout empty directories. I don't recall this being an issue in the past, but
  // it's likely I just never did a clean checkout and noticed before. jvz.
  public static File getArchiveProjectWithEmptyDirectories() {
    File directory = new File(getBasedir(),"target/generated-archives/archive-with-empty-directories");
    new File(directory, "0").mkdirs();
    new File(directory, "1").mkdirs();
    new File(directory, "2").mkdirs();
    new File(directory, "3").mkdirs();
    new File(directory, "3").mkdirs();
    new File(directory, "4").mkdirs();
    return directory;
  }
}
