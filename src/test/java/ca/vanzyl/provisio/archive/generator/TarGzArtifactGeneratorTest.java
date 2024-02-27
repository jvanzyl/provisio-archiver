package ca.vanzyl.provisio.archive.generator;

import ca.vanzyl.provisio.archive.ArchiveValidator;
import ca.vanzyl.provisio.archive.FileSystemAssert;
import ca.vanzyl.provisio.archive.TarGzArchiveValidator;
import java.io.File;
import org.junit.Test;

public class TarGzArtifactGeneratorTest extends FileSystemAssert {

    @Test
    public void generatedTarGzArtifactIsValid() throws Exception {
        File artifactLayoutDirectory = getOutputDirectory("tar-gz-layout");
        File targetArchive = getTargetArchive("generated.tar.gz");

        ArtifactGenerator generator = new TarGzArtifactGenerator(targetArchive, artifactLayoutDirectory)
                .entry("1/one.txt", "one")
                .entry("2/two.txt", "two")
                .entry("3/three.txt", "three");
        generator.generate();

        ArchiveValidator validator = new TarGzArchiveValidator(targetArchive);
        validator.assertEntryExists("1/one.txt");
        validator.assertEntryExists("2/two.txt");
        validator.assertEntryExists("3/three.txt");
        validator.assertContentOfEntryInArchive("1/one.txt", "one");
        validator.assertContentOfEntryInArchive("2/two.txt", "two");
        validator.assertContentOfEntryInArchive("3/three.txt", "three");
    }
}
