package io.tesla.proviso.archive;

import java.io.File;
import java.io.IOException;

import org.junit.Test;

public abstract class ArchiverTypeTest extends ArchiverTest {

  //
  // Each archiver type must implement these methods in their test class
  //
  protected abstract String getArchiveExtension();
  protected abstract ArchiverValidator validator(File archive) throws IOException;
      
  //
  // Archiver
  //  
  @Test
  public void createArchive() throws Exception {
    File archiveDirectory = getArchiveProject("archive-0");
    Archiver archiver = Archiver.builder().build();
    File archive = getTargetArchive("create-archive-0." + getArchiveExtension());
    archiver.archive(archive, archiveDirectory);    
    ArchiverValidator validator= validator(archive);
    validator.assertNumberOfEntriesInArchive(10);
    validator.assertPresenceOfEntryInArchive("archive-0/0/");
    validator.assertPresenceOfEntryInArchive("archive-0/0/0.txt");
    validator.assertPresenceOfEntryInArchive("archive-0/1/");
    validator.assertPresenceOfEntryInArchive("archive-0/1/1.txt");
    validator.assertPresenceOfEntryInArchive("archive-0/2/");
    validator.assertPresenceOfEntryInArchive("archive-0/2/2.txt");
    validator.assertPresenceOfEntryInArchive("archive-0/3/");
    validator.assertPresenceOfEntryInArchive("archive-0/3/3.txt");
    validator.assertPresenceOfEntryInArchive("archive-0/4/");
    validator.assertPresenceOfEntryInArchive("archive-0/4/4.txt");
    validator.assertContentOfEntryInArchive("archive-0/0/0.txt", "0");
    validator.assertContentOfEntryInArchive("archive-0/1/1.txt", "1");
    validator.assertContentOfEntryInArchive("archive-0/2/2.txt", "2");
    validator.assertContentOfEntryInArchive("archive-0/3/3.txt", "3");
    validator.assertContentOfEntryInArchive("archive-0/4/4.txt", "4");
  }

  @Test
  public void createArchiveWithIncludes() throws Exception {
    File archiveDirectory = getArchiveProject("archive-0");
    Archiver archiver = Archiver.builder() //
        .includes("**/4.txt") //
        .build();
    File archive = getTargetArchive("includes-archive-0." + getArchiveExtension());
    archiver.archive(archive, archiveDirectory);
    ArchiverValidator validator= validator(archive);
    validator.assertNumberOfEntriesInArchive(1);
    validator.assertAbsenceOfEntryInArchive("archive-0/0/0.txt");
    validator.assertAbsenceOfEntryInArchive("archive-0/1/1.txt");
    validator.assertAbsenceOfEntryInArchive("archive-0/2/2.txt");
    validator.assertAbsenceOfEntryInArchive("archive-0/3/3.txt");
    validator.assertPresenceOfEntryInArchive("archive-0/4/4.txt");
    validator.assertContentOfEntryInArchive("archive-0/4/4.txt", "4");
  }

  @Test
  public void createArchiveWithExcludes() throws Exception {
    File archiveDirectory = getArchiveProject("archive-0");
    Archiver archiver = Archiver.builder() //
        .excludes("**/4.txt") //
        .build();
    File archive = getTargetArchive("excludes-archive-0." + getArchiveExtension());
    archiver.archive(archive, archiveDirectory);
    ArchiverValidator validator= validator(archive);
    validator.assertNumberOfEntriesInArchive(9);
    validator.assertPresenceOfEntryInArchive("archive-0/0/0.txt");
    validator.assertPresenceOfEntryInArchive("archive-0/1/1.txt");
    validator.assertPresenceOfEntryInArchive("archive-0/2/2.txt");
    validator.assertPresenceOfEntryInArchive("archive-0/3/3.txt");
    validator.assertAbsenceOfEntryInArchive("archive-0/4/4.txt");
    validator.assertContentOfEntryInArchive("archive-0/0/0.txt", "0");
    validator.assertContentOfEntryInArchive("archive-0/1/1.txt", "1");
    validator.assertContentOfEntryInArchive("archive-0/2/2.txt", "2");
    validator.assertContentOfEntryInArchive("archive-0/3/3.txt", "3");
  }

  @Test
  public void createArchiveWithoutRoot() throws Exception {
    File archiveDirectory = getArchiveProject("archive-0");
    Archiver archiver = Archiver.builder() //
        .useRoot(false) //
        .build();
    File archive = getTargetArchive("without-root-archive-0." + getArchiveExtension());
    archiver.archive(archive, archiveDirectory);
    ArchiverValidator validator= validator(archive);
    validator.assertNumberOfEntriesInArchive(10);
    validator.assertPresenceOfEntryInArchive("0/0.txt");
    validator.assertPresenceOfEntryInArchive("1/1.txt");
    validator.assertPresenceOfEntryInArchive("2/2.txt");
    validator.assertPresenceOfEntryInArchive("3/3.txt");
    validator.assertPresenceOfEntryInArchive("4/4.txt");
    validator.assertContentOfEntryInArchive("0/0.txt", "0");
    validator.assertContentOfEntryInArchive("1/1.txt", "1");
    validator.assertContentOfEntryInArchive("2/2.txt", "2");
    validator.assertContentOfEntryInArchive("3/3.txt", "3");
    validator.assertContentOfEntryInArchive("4/4.txt", "4");
  }

  @Test
  public void createArchiveUsingFlatten() throws Exception {
    File archiveDirectory = getArchiveProject("archive-0");
    Archiver archiver = Archiver.builder() //
        .useRoot(false) //
        .flatten(true) //
        .build();
    File archive = getTargetArchive("flatten-archive-0." + getArchiveExtension());
    archiver.archive(archive, archiveDirectory);
    ArchiverValidator validator= validator(archive);
    validator.assertNumberOfEntriesInArchive(5);
    validator.assertPresenceOfEntryInArchive("0.txt");
    validator.assertPresenceOfEntryInArchive("1.txt");
    validator.assertPresenceOfEntryInArchive("2.txt");
    validator.assertPresenceOfEntryInArchive("3.txt");
    validator.assertPresenceOfEntryInArchive("4.txt");
    validator.assertContentOfEntryInArchive("0.txt", "0");
    validator.assertContentOfEntryInArchive("1.txt", "1");
    validator.assertContentOfEntryInArchive("2.txt", "2");
    validator.assertContentOfEntryInArchive("3.txt", "3");
    validator.assertContentOfEntryInArchive("4.txt", "4");
  }
    
  //
  // UnArchiver
  //
  
  // test includes/excludes on unarchiving
  
  //
  // File modes
  //
  @Test
  public void testSettingAndPreservationOfExecutables() throws Exception {    
    File sourceDirectory = getArchiveProject("apache-maven-3.0.4");
    Archiver archiver = Archiver.builder() //
        .executable("**/bin/mvn", "**/bin/mvnDebug", "**/bin/mvnyjp") //
        .build();
    File archive = getTargetArchive("apache-maven-3.0.4-bin." + getArchiveExtension());
    archiver.archive(archive, sourceDirectory);
    File outputDirectory = getOutputDirectory("ep-" + getArchiveExtension());
    UnArchiver unArchiver = UnArchiver.builder().build();
    unArchiver.unarchive(archive, outputDirectory);
    assertDirectoryExists(outputDirectory, "apache-maven-3.0.4");
    assertFilesIsExecutable(outputDirectory, "apache-maven-3.0.4/bin/mvn");
  }
}
