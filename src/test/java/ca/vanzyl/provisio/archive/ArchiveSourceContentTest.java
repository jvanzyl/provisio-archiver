package ca.vanzyl.provisio.archive;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import ca.vanzyl.provisio.archive.tar.TarGzArchiveSource;
import ca.vanzyl.provisio.archive.zip.ZipArchiveSource;
import java.io.IOException;
import java.io.InputStream;
import org.junit.Test;

public class ArchiveSourceContentTest extends FileSystemAssert {

    @Test
    public void closingTarEntryContentDoesNotCloseSequentialTraversal() throws Exception {
        int[] entryCount = {0};
        boolean[] opened = {false};

        new TarGzArchiveSource(getSourceArchive("apache-maven-3.0.4-bin.tar.gz")).forEachEntry(entry -> {
            entryCount[0]++;
            if (!opened[0] && entry.getType() == EntryType.FILE) {
                try (InputStream inputStream = entry.getContent().open()) {
                    assertTrue(inputStream.read() >= 0);
                }
                opened[0] = true;
            }
        });

        assertTrue(opened[0]);
        assertTrue(entryCount[0] > 1);
    }

    @Test
    public void tarEntryContentIsSingleUseAndExpiresAfterItsCallback() throws Exception {
        EntryContent[] expired = {null};

        new TarGzArchiveSource(getSourceArchive("apache-maven-3.0.4-bin.tar.gz")).forEachEntry(entry -> {
            if (expired[0] == null && entry.getType() == EntryType.FILE) {
                expired[0] = entry.getContent();
                try (InputStream ignored = expired[0].open()) {
                    try {
                        expired[0].open();
                        fail("Expected tar content to reject a second open");
                    } catch (IOException expected) {
                        assertTrue(expected.getMessage().contains("only be opened once"));
                    }
                }
            }
        });

        assertNotNull(expired[0]);
        try {
            expired[0].open();
            fail("Expected tar content to expire after its callback");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("no longer active"));
        }
    }

    @Test
    public void zipEntryExposesMetadataAndIsRepeatableOnlyDuringItsCallback() throws Exception {
        EntryContent[] expired = {null};
        long[] size = {-1};
        long[] crc = {-1};

        new ZipArchiveSource(getSourceArchive("jenv.zip")).forEachEntry(entry -> {
            if (expired[0] == null && entry.getType() == EntryType.FILE) {
                expired[0] = entry.getContent();
                size[0] = expired[0].size();
                crc[0] = expired[0].crc32();
                assertTrue(expired[0].isRepeatable());
                try (InputStream first = expired[0].open();
                        InputStream second = expired[0].open()) {
                    assertEquals(first.read(), second.read());
                }
            }
        });

        assertNotNull(expired[0]);
        assertTrue(size[0] >= 0);
        assertTrue(crc[0] >= 0);
        try {
            expired[0].open();
            fail("Expected ZIP content to expire after its callback");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("no longer active"));
        }
    }
}
