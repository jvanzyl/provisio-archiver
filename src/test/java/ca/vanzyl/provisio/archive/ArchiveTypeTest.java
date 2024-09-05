package ca.vanzyl.provisio.archive;

import static org.junit.Assert.assertEquals;

import ca.vanzyl.provisio.archive.source.FileSource;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.util.List;
import org.codehaus.plexus.util.Os;
import org.codehaus.swizzle.stream.ReplaceStringInputStream;
import org.junit.Assume;
import org.junit.Test;

public abstract class ArchiveTypeTest {

    //
    // Each archiver type must implement these methods in their test class
    //
    protected abstract String getArchiveExtension();

    protected ArchiveValidator validator(File archive) throws Exception {
        return ArchiveValidatorHelper.getArchiveValidator(archive);
    }

    //
    // Archiver
    //
    @Test
    public void createArchive() throws Exception {
        File archiveDirectory = FileSystemAssert.getArchiveProject("archive-0");
        Archiver archiver = Archiver.builder().build();
        File archive = FileSystemAssert.getTargetArchive("create-archive-0." + getArchiveExtension());
        archiver.archive(archive, archiveDirectory);
        ArchiveValidator validator = validator(archive);

        validator.assertEntries(
                "archive-0/", //
                "archive-0/0/", //
                "archive-0/0/0.txt", //
                "archive-0/1/", //
                "archive-0/1/1.txt", //
                "archive-0/2/", //
                "archive-0/2/2.txt", //
                "archive-0/3/", //
                "archive-0/3/3.txt", //
                "archive-0/4/", //
                "archive-0/4/4.txt" //
                );

        validator.assertContentOfEntryInArchive("archive-0/0/0.txt", "0");
        validator.assertContentOfEntryInArchive("archive-0/1/1.txt", "1");
        validator.assertContentOfEntryInArchive("archive-0/2/2.txt", "2");
        validator.assertContentOfEntryInArchive("archive-0/3/3.txt", "3");
        validator.assertContentOfEntryInArchive("archive-0/4/4.txt", "4");
    }

    @Test
    public void createArchiveWithIncludes() throws Exception {
        File archiveDirectory = FileSystemAssert.getArchiveProject("archive-0");
        Archiver archiver = Archiver.builder() //
                .includes("**/4.txt") //
                .build();
        File archive = FileSystemAssert.getTargetArchive("includes-archive-0." + getArchiveExtension());
        archiver.archive(archive, archiveDirectory);
        ArchiveValidator validator = validator(archive);

        validator.assertEntries(
                "archive-0/", //
                "archive-0/4/", //
                "archive-0/4/4.txt" //
                );

        validator.assertContentOfEntryInArchive("archive-0/4/4.txt", "4");
    }

    @Test
    public void createArchiveWithMultipleIncludes() throws Exception {
        File archiveDirectory = FileSystemAssert.getArchiveProject("archive-0");
        Archiver archiver = Archiver.builder() //
                .includes("**/3.txt") //
                .includes("**/4.txt") //
                .build();
        File archive = FileSystemAssert.getTargetArchive("includes-archive-0." + getArchiveExtension());
        archiver.archive(archive, archiveDirectory);
        ArchiveValidator validator = validator(archive);

        validator.assertEntries(
                "archive-0/", //
                "archive-0/3/", //
                "archive-0/3/3.txt", //
                "archive-0/4/", //
                "archive-0/4/4.txt" //
                );

        validator.assertContentOfEntryInArchive("archive-0/4/4.txt", "4");
    }

    @Test
    public void createArchiveWithEmptyDirectories() throws Exception {
        File archiveDirectory = FileSystemAssert.getArchiveProjectWithEmptyDirectories();
        Archiver archiver = Archiver.builder().build();
        File archive = FileSystemAssert.getTargetArchive("archive-with-empty-directories." + getArchiveExtension());
        archiver.archive(archive, archiveDirectory);
        ArchiveValidator validator = validator(archive);
        validator.assertEntries(
                "archive-with-empty-directories/", //
                "archive-with-empty-directories/0/", //
                "archive-with-empty-directories/1/", //
                "archive-with-empty-directories/2/", //
                "archive-with-empty-directories/3/", //
                "archive-with-empty-directories/4/");
        UnArchiver unArchiver = UnArchiver.builder().build();
        File outputDirectory =
                FileSystemAssert.getOutputDirectory("archive-with-empty-directories/" + getArchiveExtension());
        unArchiver.unarchive(archive, outputDirectory);
    }

    @Test
    public void createArchiveWithExcludes() throws Exception {
        File archiveDirectory = FileSystemAssert.getArchiveProject("archive-0");
        Archiver archiver = Archiver.builder() //
                .excludes("**/4**") // We want to exclude all items with "4" which includes the directory entry
                .build();
        File archive = FileSystemAssert.getTargetArchive("excludes-archive-0." + getArchiveExtension());
        archiver.archive(archive, archiveDirectory);
        ArchiveValidator validator = validator(archive);

        validator.assertEntries(
                "archive-0/", //
                "archive-0/0/", //
                "archive-0/0/0.txt", //
                "archive-0/1/", //
                "archive-0/1/1.txt", //
                "archive-0/2/", //
                "archive-0/2/2.txt", //
                "archive-0/3/", //
                "archive-0/3/3.txt" //
                );

        validator.assertContentOfEntryInArchive("archive-0/0/0.txt", "0");
        validator.assertContentOfEntryInArchive("archive-0/1/1.txt", "1");
        validator.assertContentOfEntryInArchive("archive-0/2/2.txt", "2");
        validator.assertContentOfEntryInArchive("archive-0/3/3.txt", "3");
    }

    @Test
    public void createArchiveWithoutRoot() throws Exception {
        File archiveDirectory = FileSystemAssert.getArchiveProject("archive-0");
        Archiver archiver = Archiver.builder() //
                .useRoot(false) //
                .build();
        File archive = FileSystemAssert.getTargetArchive("without-root-archive-0." + getArchiveExtension());
        archiver.archive(archive, archiveDirectory);
        ArchiveValidator validator = validator(archive);
        validator.assertEntries("0/", "0/0.txt", "1/", "1/1.txt", "2/", "2/2.txt", "3/", "3/3.txt", "4/", "4/4.txt");
        validator.assertContentOfEntryInArchive("0/0.txt", "0");
        validator.assertContentOfEntryInArchive("1/1.txt", "1");
        validator.assertContentOfEntryInArchive("2/2.txt", "2");
        validator.assertContentOfEntryInArchive("3/3.txt", "3");
        validator.assertContentOfEntryInArchive("4/4.txt", "4");
    }

    @Test
    public void createArchiveWithPrefix() throws Exception {
        File archiveDirectory = FileSystemAssert.getArchiveProject("archive-0");
        Archiver archiver = Archiver.builder() //
                .withPrefix("prefix/") //
                .build();
        File archive = FileSystemAssert.getTargetArchive("with-prefix-archive-0." + getArchiveExtension());
        archiver.archive(archive, archiveDirectory);
        ArchiveValidator validator = validator(archive);
        validator.assertEntries(
                "prefix/",
                "prefix/archive-0/",
                "prefix/archive-0/0/",
                "prefix/archive-0/0/0.txt",
                "prefix/archive-0/1/",
                "prefix/archive-0/1/1.txt",
                "prefix/archive-0/2/",
                "prefix/archive-0/2/2.txt",
                "prefix/archive-0/3/",
                "prefix/archive-0/3/3.txt",
                "prefix/archive-0/4/",
                "prefix/archive-0/4/4.txt");
        validator.assertContentOfEntryInArchive("prefix/archive-0/0/0.txt", "0");
        validator.assertContentOfEntryInArchive("prefix/archive-0/1/1.txt", "1");
        validator.assertContentOfEntryInArchive("prefix/archive-0/2/2.txt", "2");
        validator.assertContentOfEntryInArchive("prefix/archive-0/3/3.txt", "3");
        validator.assertContentOfEntryInArchive("prefix/archive-0/4/4.txt", "4");
    }

    @Test
    public void createArchiveUsingFlatten() throws Exception {
        File archiveDirectory = FileSystemAssert.getArchiveProject("archive-0");
        Archiver archiver = Archiver.builder() //
                .useRoot(false) //
                .flatten(true) //
                .build();
        File archive = FileSystemAssert.getTargetArchive("flatten-archive-0." + getArchiveExtension());
        archiver.archive(archive, archiveDirectory);
        ArchiveValidator validator = validator(archive);
        validator.assertEntries("0.txt", "1.txt", "2.txt", "3.txt", "4.txt");
        validator.assertContentOfEntryInArchive("0.txt", "0");
        validator.assertContentOfEntryInArchive("1.txt", "1");
        validator.assertContentOfEntryInArchive("2.txt", "2");
        validator.assertContentOfEntryInArchive("3.txt", "3");
        validator.assertContentOfEntryInArchive("4.txt", "4");
    }

    @Test
    // This test is perfunctory because of functionality to satisfy the test below
    public void testSettingAndPreservationOfExecutables() throws Exception {
        File sourceDirectory = FileSystemAssert.getArchiveProject("apache-maven-3.0.4");
        Archiver archiver = Archiver.builder() //
                .executable("**/bin/mvn", "**/bin/mvnDebug", "**/bin/mvnyjp") //
                .build();
        File archive = FileSystemAssert.getTargetArchive("apache-maven-3.0.4-bin." + getArchiveExtension());
        archiver.archive(archive, sourceDirectory);
        File outputDirectory = FileSystemAssert.getOutputDirectory("ep-" + getArchiveExtension());
        UnArchiver unArchiver = UnArchiver.builder().build();
        unArchiver.unarchive(archive, outputDirectory);
        FileSystemAssert.assertDirectoryExists(outputDirectory, "apache-maven-3.0.4");
        FileSystemAssert.assertFileIsExecutable(outputDirectory, "apache-maven-3.0.4/bin/mvn");
    }

    @Test
    public void testSettingAndPreservationOfExecutablesWithSourcesOriginallyNonExecutable() throws Exception {
        //
        // Our build.sh script is not executable in source form but we want to make it executable and
        // make sure it is preserved in an archive/unarchive cycle.
        //
        String archiveDirectory = "archive-source-without-executables";
        String archiveName = archiveDirectory + "." + getArchiveExtension();
        File sourceDirectory = FileSystemAssert.getArchiveProject(archiveDirectory);
        Archiver archiver = Archiver.builder() //
                .executable(archiveDirectory + "/build.sh") //
                .build();
        File archive = FileSystemAssert.getTargetArchive(archiveName);
        archiver.archive(archive, sourceDirectory);
        File outputDirectory = FileSystemAssert.getOutputDirectory(archiveDirectory);
        UnArchiver unArchiver = UnArchiver.builder().build();
        unArchiver.unarchive(archive, outputDirectory);
        FileSystemAssert.assertFileIsExecutable(outputDirectory, archiveDirectory + "/build.sh");
    }

    @Test
    public void testTransferringOfExecutablesFromSourceToArchive() throws Exception {
        //
        // There are executable scripts in the source for our archive and we want
        // the filemode to be picked up correctly so that we can set the executable
        // bit on the archive entries and we verify the executable bits remain
        // for an archive/unarchive cycle.
        //
        File sourceDirectory = FileSystemAssert.getArchiveProject("apache-maven-3.0.4");
        Archiver archiver = Archiver.builder().build();
        File archive = FileSystemAssert.getTargetArchive("apache-maven-3.0.4-bin." + getArchiveExtension());
        archiver.archive(archive, sourceDirectory);
        File outputDirectory = FileSystemAssert.getOutputDirectory("transferring-executables-" + getArchiveExtension());
        UnArchiver unArchiver = UnArchiver.builder().build();
        unArchiver.unarchive(archive, outputDirectory);
        FileSystemAssert.assertDirectoryExists(outputDirectory, "apache-maven-3.0.4");
        FileSystemAssert.assertFileIsExecutable(outputDirectory, "apache-maven-3.0.4/bin/mvn");
    }

    //
    // drwxr-xr-x 0 dain staff 0 Aug 20 18:01 bin/
    // -rwxr-xr-x 0 dain staff 1450 Aug 20 18:01 bin/launcher
    // -rwxr-xr-x 0 dain staff 13762 Aug 20 18:01 bin/launcher.py
    // drwxr-xr-x 0 0 0 0 Aug 20 18:01 bin/procname/
    // drwxr-xr-x 0 dain staff 0 Aug 20 18:01 bin/procname/Linux-x86_64/
    // -rw-r--r-- 0 dain staff 4144 Aug 20 18:01 bin/procname/Linux-x86_64/libprocname.so
    //
    @Test
    public void testPreservervationOfFileModeOnUnarchivedFiles() throws Exception {
        Assume.assumeFalse(Os.isFamily(Os.FAMILY_WINDOWS));

        File archive = FileSystemAssert.getSourceArchive("launcher-0.93-bin." + getArchiveExtension());
        File outputDirectory = FileSystemAssert.getOutputDirectory("preserve-filemode-" + getArchiveExtension());
        UnArchiver unArchiver = UnArchiver.builder().build();
        unArchiver.unarchive(archive, outputDirectory);
        FileSystemAssert.assertFileMode(outputDirectory, "bin/launcher", "-rwxr-xr-x");
        FileSystemAssert.assertFileMode(outputDirectory, "bin/launcher.py", "-rwxr-xr-x");
    }

    @Test
    public void unarchive() throws Exception {
        File archiveDirectory = FileSystemAssert.getArchiveProject("archive-0");
        Archiver archiver = Archiver.builder().build();
        File archive = FileSystemAssert.getTargetArchive("create-archive-0." + getArchiveExtension());

        archiver.archive(archive, archiveDirectory);
        UnArchiver unArchiver = UnArchiver.builder().build();
        File outputDirectory = FileSystemAssert.getOutputDirectory("archive-0-extracted/" + getArchiveExtension());
        unArchiver.unarchive(archive, outputDirectory);

        FileSystemAssert.assertPresenceAndSizeOf(FileSystemAssert.file(outputDirectory, "archive-0/0/0.txt"), 1);
        FileSystemAssert.assertPresenceAndContentOf(FileSystemAssert.file(outputDirectory, "archive-0/0/0.txt"), "0");
        FileSystemAssert.assertPresenceAndSizeOf(FileSystemAssert.file(outputDirectory, "archive-0/1/1.txt"), 1);
        FileSystemAssert.assertPresenceAndContentOf(FileSystemAssert.file(outputDirectory, "archive-0/1/1.txt"), "1");
        FileSystemAssert.assertPresenceAndSizeOf(FileSystemAssert.file(outputDirectory, "archive-0/2/2.txt"), 1);
        FileSystemAssert.assertPresenceAndContentOf(FileSystemAssert.file(outputDirectory, "archive-0/2/2.txt"), "2");
        FileSystemAssert.assertPresenceAndSizeOf(FileSystemAssert.file(outputDirectory, "archive-0/3/3.txt"), 1);
        FileSystemAssert.assertPresenceAndContentOf(FileSystemAssert.file(outputDirectory, "archive-0/3/3.txt"), "3");
        FileSystemAssert.assertPresenceAndSizeOf(FileSystemAssert.file(outputDirectory, "archive-0/4/4.txt"), 1);
        FileSystemAssert.assertPresenceAndContentOf(FileSystemAssert.file(outputDirectory, "archive-0/4/4.txt"), "4");
    }

    @Test
    public void unarchiveWithEntryProcoessor() throws Exception {
        String name = "archive-with-entry-processor";
        File archiveDirectory = FileSystemAssert.getArchiveProject(name);
        Archiver archiver = Archiver.builder().build();
        File archive = FileSystemAssert.getTargetArchive(name + "." + getArchiveExtension());

        archiver.archive(archive, archiveDirectory);
        UnArchiver unArchiver = UnArchiver.builder().build();
        File outputDirectory = FileSystemAssert.getOutputDirectory(name + "-extracted/" + getArchiveExtension());
        unArchiver.unarchive(archive.toPath(), outputDirectory.toPath(), new UnarchivingEnhancedEntryProcessor() {

            @Override
            public void processStream(String entryName, InputStream inputStream, OutputStream outputStream)
                    throws IOException {
                new ReplaceStringInputStream(inputStream, "REPLACE_ME", "PROCESSED_TEXT").transferTo(outputStream);
            }

            @Override
            public String targetName(String name) {
                name = name.replace("${packagePath}", "io/takari/app");
                return name;
            }
        });
        FileSystemAssert.assertPresenceAndContentOf(
                FileSystemAssert.file(
                        outputDirectory, "archive-with-entry-processor/src/main/java/io/takari/app/file.txt"),
                "PROCESSED_TEXT");
        FileSystemAssert.assertDirectoryDoesNotExist(
                outputDirectory, "archive-with-entry-processor/src/main/java/${packagePath}");
    }

    @Test
    public void testIntermediateDirectoryEntries() throws Exception {
        Archiver archiver = Archiver.builder().build();
        File archive = FileSystemAssert.getTargetArchive("create-intermediate-directories." + getArchiveExtension());
        archiver.archive(archive, new FileSource("1/2/file.txt", new File("src/test/files/0.txt")));
        ArchiveValidator validator = validator(archive);
        validator.assertEntries("1/", "1/2/", "1/2/file.txt");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testDuplicateEntries() throws Exception {
        Archiver archiver = Archiver.builder().build();
        File archive = FileSystemAssert.getTargetArchive("create-intermediate-directories." + getArchiveExtension());
        archiver.archive(
                archive,
                new FileSource("1/2/file.txt", new File("src/test/files/0.txt")),
                new FileSource("1/2/file.txt", new File("src/test/files/0.txt")));
        ArchiveValidator validator = validator(archive);
        validator.assertEntries("1/", "1/2/", "1/2/file.txt");
    }

    @Test
    public void validateArchiveHasDOSEpochTimes() throws Exception {
        File archiveDirectory = FileSystemAssert.getArchiveProject("archive-time-0");
        Archiver archiver = Archiver.builder().normalize(true).build();
        File archive = FileSystemAssert.getTargetArchive("create-archive-time-0." + getArchiveExtension());
        archiver.archive(archive, archiveDirectory);
        ArchiveValidator validator = validator(archive);

        validator.assertEntries(
                "archive-time-0/", //
                "archive-time-0/0/", //
                "archive-time-0/0/0.txt", //
                "archive-time-0/1/", //
                "archive-time-0/1/1.txt", //
                "archive-time-0/2/", //
                "archive-time-0/2/2.txt", //
                "archive-time-0/3/", //
                "archive-time-0/3/3.txt", //
                "archive-time-0/4/", //
                "archive-time-0/4/4.txt", //
                "archive-time-0/4/Foo.class" //
                );

        validator.assertContentOfEntryInArchive("archive-time-0/0/0.txt", "0");
        validator.assertContentOfEntryInArchive("archive-time-0/1/1.txt", "1");
        validator.assertContentOfEntryInArchive("archive-time-0/2/2.txt", "2");
        validator.assertContentOfEntryInArchive("archive-time-0/3/3.txt", "3");
        validator.assertContentOfEntryInArchive("archive-time-0/4/4.txt", "4");
        validator.assertContentOfEntryInArchive("archive-time-0/4/Foo.class", "Foo.class");

        validator.assertTimeOfEntryInArchive("archive-time-0/0/0.txt", Archiver.DOS_EPOCH_IN_JAVA_TIME);
        validator.assertTimeOfEntryInArchive("archive-time-0/1/1.txt", Archiver.DOS_EPOCH_IN_JAVA_TIME);
        validator.assertTimeOfEntryInArchive("archive-time-0/2/2.txt", Archiver.DOS_EPOCH_IN_JAVA_TIME);
        validator.assertTimeOfEntryInArchive("archive-time-0/3/3.txt", Archiver.DOS_EPOCH_IN_JAVA_TIME);
        validator.assertTimeOfEntryInArchive("archive-time-0/4/4.txt", Archiver.DOS_EPOCH_IN_JAVA_TIME);
        validator.assertTimeOfEntryInArchive(
                "archive-time-0/4/Foo.class", Archiver.DOS_EPOCH_IN_JAVA_TIME + Archiver.MINIMUM_TIMESTAMP_INCREMENT);
    }

    @Test
    public void validateArchiveNormalized() throws Exception {
        Archiver archiver = Archiver.builder().normalize(true).build();
        File archive = FileSystemAssert.getTargetArchive("deterministicOrdering-0." + getArchiveExtension());
        // StringListSource with reverse order
        archiver.archive(archive, new StringListSource(List.of("e", "d", "c", "b", "a")));
        ArchiveValidator validator = validator(archive);
        validator.assertSortedEntries("a", "b", "c", "d", "e");
        String hash0 = sha1(archive);

        archive = FileSystemAssert.getTargetArchive("deterministicOrdering-1." + getArchiveExtension());
        // StringListSource with "random" order
        archiver.archive(archive, new StringListSource(List.of("c", "e", "d", "a", "b")));
        validator = validator(archive);
        validator.assertSortedEntries("a", "b", "c", "d", "e");
        String hash1 = sha1(archive);
        assertEquals("We expect a normalized archives to have the same outer hash.", hash0, hash1);
    }

    @Test
    public void validateArchiveWithLongPath() throws Exception {
        Archiver archiver =
                Archiver.builder().useRoot(false).posixLongFileMode(true).build();
        File archive = FileSystemAssert.getTargetArchive("archive-with-long-path." + getArchiveExtension());
        File archiveDirectory = FileSystemAssert.getArchiveProject("archive-with-long-path");
        archiver.archive(archive, archiveDirectory);
        ArchiveValidator validator = validator(archive);
        validator.assertContentOfEntryInArchive(
                "one/two/three/four/five/six/seven/eight/nine/ten/eleven/twelve/thirteen/fourteen/fifteen/sixteen/seventeen/entry.txt",
                "entry.txt");
    }

    public static String sha1(File file) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA1");
        FileInputStream fis = new FileInputStream(file);
        byte[] dataBytes = new byte[1024];
        int i;
        while ((i = fis.read(dataBytes)) != -1) {
            md.update(dataBytes, 0, i);
        }
        StringBuilder sb = new StringBuilder();
        for (byte b : md.digest()) {
            sb.append(Integer.toString((b & 0xff) + 0x100, 16).substring(1));
        }
        return sb.toString();
    }
}
