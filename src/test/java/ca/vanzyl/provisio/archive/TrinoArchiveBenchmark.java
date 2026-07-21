package ca.vanzyl.provisio.archive;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

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

        TrinoArchiveScenario scenario = new TrinoArchiveScenario();
        TrinoArchiveScenario.Result metadata = scenario.run(
                directory.resolve("size-and-crc32"), entries, uniquePayloads, ContentIdentityMode.SIZE_AND_CRC32);
        TrinoArchiveScenario.Result verified =
                scenario.run(directory.resolve("verified"), entries, uniquePayloads, ContentIdentityMode.VERIFIED);

        assertResult(metadata, entries, uniquePayloads, uniquePayloads);
        assertResult(verified, entries, uniquePayloads, entries);
        assertTrue(verified.contentBytesRead > metadata.contentBytesRead);

        report("size-and-crc32", metadata, entries, uniquePayloads);
        report("verified", verified, entries, uniquePayloads);
        System.out.printf(
                Locale.ROOT,
                "Trino-shaped identity comparison: verifiedToCrcTimeRatio=%.2f " + "verifiedToCrcReadRatio=%.2f%n",
                (double) verified.elapsedNanos / metadata.elapsedNanos,
                (double) verified.contentBytesRead / metadata.contentBytesRead);
    }

    private void assertResult(
            TrinoArchiveScenario.Result result, int entries, int uniquePayloads, int expectedContentOpens) {
        assertEquals(expectedContentOpens, result.contentOpens);
        assertEquals(uniquePayloads, result.regularFiles);
        assertEquals(entries - uniquePayloads, result.hardLinks);
        assertEquals(0, result.expandedDirectories);
        assertEquals(0, result.unexpectedFiles);
    }

    private void report(String identity, TrinoArchiveScenario.Result result, int entries, int uniquePayloads) {
        double seconds = result.elapsedNanos / 1_000_000_000.0;
        double entriesPerSecond = entries / seconds;
        System.out.printf(
                Locale.ROOT,
                "Trino-shaped archive: identity=%s entries=%d unique=%d duplicates=%d contentOpens=%d "
                        + "contentBytesRead=%d inputBytes=%d outputBytes=%d seconds=%.3f entriesPerSecond=%.0f%n",
                identity,
                entries,
                uniquePayloads,
                entries - uniquePayloads,
                result.contentOpens,
                result.contentBytesRead,
                result.sourceBytes,
                result.outputBytes,
                seconds,
                entriesPerSecond);
    }
}
