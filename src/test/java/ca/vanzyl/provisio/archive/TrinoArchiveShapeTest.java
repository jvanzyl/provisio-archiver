package ca.vanzyl.provisio.archive;

import static org.junit.Assert.assertEquals;

import java.nio.file.Path;
import org.junit.Test;

public class TrinoArchiveShapeTest extends FileSystemAssert {

    @Test
    public void metadataDeduplicationAvoidsReadingDuplicateJarContentOrExpandingATree() throws Exception {
        int entries = 2_048;
        int uniquePayloads = 64;
        Path directory = getOutputDirectory("trino-shape").toPath();

        TrinoArchiveScenario.Result result = new TrinoArchiveScenario().run(directory, entries, uniquePayloads);

        assertEquals(entries, result.entries);
        assertEquals(uniquePayloads, result.uniquePayloads);
        assertEquals(uniquePayloads, result.contentOpens);
        assertEquals(uniquePayloads, result.regularFiles);
        assertEquals(entries - uniquePayloads, result.hardLinks);
        assertEquals(0, result.expandedDirectories);
        assertEquals(0, result.unexpectedFiles);
    }
}
