package ca.vanzyl.provisio.archive;

import static org.junit.Assert.assertEquals;

import java.nio.file.Path;
import java.util.Locale;
import org.junit.Test;

/** Opt-in macrobenchmark; its name intentionally keeps it out of the default Surefire test patterns. */
public class TrinoArchiveBenchmark extends FileSystemAssert {

    @Test
    public void reportTrinoShapedArchiveAssembly() throws Exception {
        int entries = Integer.getInteger("provisio.benchmark.entries", 20_000);
        int uniquePayloads = Integer.getInteger("provisio.benchmark.unique-payloads", 256);
        Path directory = getOutputDirectory("trino-benchmark").toPath();

        TrinoArchiveScenario.Result result = new TrinoArchiveScenario().run(directory, entries, uniquePayloads);

        assertEquals(uniquePayloads, result.contentOpens);
        assertEquals(uniquePayloads, result.regularFiles);
        assertEquals(entries - uniquePayloads, result.hardLinks);
        assertEquals(0, result.expandedDirectories);
        assertEquals(0, result.unexpectedFiles);

        double seconds = result.elapsedNanos / 1_000_000_000.0;
        double entriesPerSecond = entries / seconds;
        System.out.printf(
                Locale.ROOT,
                "Trino-shaped archive: entries=%d unique=%d duplicates=%d inputBytes=%d outputBytes=%d "
                        + "seconds=%.3f entriesPerSecond=%.0f%n",
                entries,
                uniquePayloads,
                entries - uniquePayloads,
                result.sourceBytes,
                result.outputBytes,
                seconds,
                entriesPerSecond);
    }
}
