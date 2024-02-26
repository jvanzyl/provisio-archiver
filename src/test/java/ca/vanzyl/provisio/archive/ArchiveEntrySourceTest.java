package ca.vanzyl.provisio.archive;

import ca.vanzyl.provisio.archive.tar.TarGzXzArchiveSource;
import java.io.File;
import org.junit.Test;

public class ArchiveEntrySourceTest extends FileSystemAssert {

    @Test
    public void readEntriesDirectlyFromAnArchiveToMakeAnotherArchive() throws Exception {
        Archiver archiver = Archiver.builder() //
                .build();

        File archive = getTargetArchive("archive-from-archive.tar.gz");
        Source source = new TarGzXzArchiveSource(getSourceArchive("apache-maven-3.0.4-bin.tar.gz"));
        archiver.archive(archive, source);
        ArchiveValidator validator = new TarGzArchiveValidator(archive);
        // note that original archive is missing 3 directory entries
        // apache-maven-3.0.4/,apache-maven-3.0.4/boot/ and apache-maven-3.0.4/bin/
        // I assume this is due to a bug in maven archiver.
        // tar on command line does create 47 entries
        validator.assertEntries(
                "apache-maven-3.0.4/", //
                "apache-maven-3.0.4/LICENSE.txt", //
                "apache-maven-3.0.4/NOTICE.txt", //
                "apache-maven-3.0.4/README.txt", //
                "apache-maven-3.0.4/bin/", //
                "apache-maven-3.0.4/bin/m2.conf", //
                "apache-maven-3.0.4/bin/mvn", //
                "apache-maven-3.0.4/bin/mvn.bat", //
                "apache-maven-3.0.4/bin/mvnDebug", //
                "apache-maven-3.0.4/bin/mvnDebug.bat", //
                "apache-maven-3.0.4/bin/mvnyjp", //
                "apache-maven-3.0.4/boot/", //
                "apache-maven-3.0.4/boot/plexus-classworlds-2.4.jar", //
                "apache-maven-3.0.4/conf/", //
                "apache-maven-3.0.4/conf/settings.xml", //
                "apache-maven-3.0.4/lib/", //
                "apache-maven-3.0.4/lib/aether-api-1.13.1.jar", //
                "apache-maven-3.0.4/lib/aether-connector-wagon-1.13.1.jar", //
                "apache-maven-3.0.4/lib/aether-impl-1.13.1.jar", //
                "apache-maven-3.0.4/lib/aether-spi-1.13.1.jar", //
                "apache-maven-3.0.4/lib/aether-util-1.13.1.jar", //
                "apache-maven-3.0.4/lib/commons-cli-1.2.jar", //
                "apache-maven-3.0.4/lib/ext/", //
                "apache-maven-3.0.4/lib/ext/README.txt", //
                "apache-maven-3.0.4/lib/maven-aether-provider-3.0.4.jar", //
                "apache-maven-3.0.4/lib/maven-artifact-3.0.4.jar", //
                "apache-maven-3.0.4/lib/maven-compat-3.0.4.jar", //
                "apache-maven-3.0.4/lib/maven-core-3.0.4.jar", //
                "apache-maven-3.0.4/lib/maven-embedder-3.0.4.jar", //
                "apache-maven-3.0.4/lib/maven-model-3.0.4.jar", //
                "apache-maven-3.0.4/lib/maven-model-builder-3.0.4.jar", //
                "apache-maven-3.0.4/lib/maven-plugin-api-3.0.4.jar", //
                "apache-maven-3.0.4/lib/maven-repository-metadata-3.0.4.jar", //
                "apache-maven-3.0.4/lib/maven-settings-3.0.4.jar", //
                "apache-maven-3.0.4/lib/maven-settings-builder-3.0.4.jar", //
                "apache-maven-3.0.4/lib/plexus-cipher-1.7.jar", //
                "apache-maven-3.0.4/lib/plexus-component-annotations-1.5.5.jar", //
                "apache-maven-3.0.4/lib/plexus-interpolation-1.14.jar", //
                "apache-maven-3.0.4/lib/plexus-sec-dispatcher-1.3.jar", //
                "apache-maven-3.0.4/lib/plexus-utils-2.0.6.jar", //
                "apache-maven-3.0.4/lib/sisu-guava-0.9.9.jar", //
                "apache-maven-3.0.4/lib/sisu-guice-3.1.0-no_aop.jar", //
                "apache-maven-3.0.4/lib/sisu-inject-bean-2.3.0.jar", //
                "apache-maven-3.0.4/lib/sisu-inject-plexus-2.3.0.jar", //
                "apache-maven-3.0.4/lib/wagon-file-2.2.jar", //
                "apache-maven-3.0.4/lib/wagon-http-2.2-shaded.jar", //
                "apache-maven-3.0.4/lib/wagon-provider-api-2.2.jar" //
                );

        // Need to make sure file modes are preserved when creating an archive from
        // directly reading the entries of another
        File outputDirectory = new File(getOutputDirectory(), "archive-source");
        UnArchiver unArchiver = UnArchiver.builder().build();
        unArchiver.unarchive(archive, outputDirectory);
        assertDirectoryExists(outputDirectory, "apache-maven-3.0.4");
        assertFileIsExecutable(outputDirectory, "apache-maven-3.0.4/bin/mvn");
    }
}
