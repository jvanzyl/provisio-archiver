package io.tesla.proviso.archive;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;

import org.codehaus.plexus.util.FileUtils;

public abstract class ArchiverTest {

  private String basedir;

  //
  // Assertions for tests
  //
  protected void assertDirectoryExists(File outputDirectory, String directoryName) {
    File directory = new File(outputDirectory, directoryName);
    assertTrue(String.format("We expect to find the directory %s, but it doesn't exist or is not a directory.", directoryName), directory.exists() && directory.isDirectory());
  }

  protected void assertDirectoryDoesNotExist(File outputDirectory, String directoryName) {
    File directory = new File(outputDirectory, directoryName);
    assertFalse(String.format("We expect not to find the directory %s, but it is there.", directoryName), directory.exists() && directory.isDirectory());
  }

  protected void assertFilesExists(File outputDirectory, String fileName) {
    File file = new File(outputDirectory, fileName);
    assertTrue(String.format("We expect to find the file %s, but it doesn't exist or is not a file.", fileName), file.exists() && file.isFile());
  }

  protected void assertPresenceAndSizeOf(File file, int size) {
    assertTrue(String.format("We expect to find the file %s, but it doesn't exist or is not a file.", file.getName()), file.exists() && file.isFile());
    assertEquals(String.format("We expect the file to be size = %s, but it not.", size), size, file.length());
  }

  protected void assertPresenceAndSizeOf(File outputDirectory, String fileName, int size) {
    File file = new File(outputDirectory, fileName);
    assertPresenceAndSizeOf(file, size);
  }

  protected void assertPresenceAndContentOf(File file, String expectedContent) throws IOException {
    assertTrue(String.format("We expect to find the file %s, but it doesn't exist or is not a file.", file.getName()), file.exists() && file.isFile());
    assertEquals(String.format("We expect the content of the file to be %s, but is not.", expectedContent), expectedContent, FileUtils.fileRead(file));
  }

  protected void assertPresenceAndContentOf(File outputDirectory, String fileName, String expectedContent) throws IOException {
    File file = new File(outputDirectory, fileName);
    assertPresenceAndContentOf(file, expectedContent);
  }

  protected void assertFileIsExecutable(File outputDirectory, String fileName) {
    File file = new File(outputDirectory, fileName);
    assertTrue(String.format("We expect to find the file %s, but it doesn't exist or is not executable.", fileName), file.exists() && file.isFile() && file.canExecute());
  }

  //
  // Helper methods for tests
  //
  protected final String getBasedir() {
    if (null == basedir) {
      basedir = System.getProperty("basedir", new File("").getAbsolutePath());
    }
    return basedir;
  }

  protected File file(File outputDirectory, String fileName) {
    return new File(outputDirectory, fileName);
  }

  protected final File getOutputDirectory() {
    return new File(getBasedir(), "target/archives");
  }

  protected final File getOutputDirectory(String name) throws IOException {
    File outputDirectory = new File(getBasedir(), "target/archives/" + name);
    if (outputDirectory.exists()) {
      FileUtils.deleteDirectory(outputDirectory);
    }
    return outputDirectory;
  }

  protected final File getSourceArchiveDirectory() {
    return new File(getBasedir(), "src/test/archives");
  }

  protected final File getSourceArchive(String name) {
    return new File(getSourceArchiveDirectory(), name);
  }

  protected final File getSourceFileDirectory() {
    return new File(getBasedir(), "src/test/files");
  }

  protected final File getSourceFile(String name) {
    return new File(getSourceFileDirectory(), name);
  }

  protected final File getTargetArchive(String name) {
    File archive = new File(getOutputDirectory(), name);
    if (!archive.getParentFile().exists()) {
      archive.getParentFile().mkdirs();
    }
    return archive;
  }

  protected final File getArchiveProject(String name) {
    return new File(getBasedir(), String.format("src/test/archives/%s", name));
  }

  protected void assertFileMode(File outputDirectory, String string, String expectedUnix) {
    File f = new File(outputDirectory, string);
    String unix = FileMode.toUnix(FileMode.getFileMode(f));
    assertEquals(expectedUnix, unix);
  }
}
