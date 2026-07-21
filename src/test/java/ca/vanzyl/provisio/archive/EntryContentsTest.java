package ca.vanzyl.provisio.archive;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;
import org.junit.Test;

public class EntryContentsTest {

    @Test
    public void byteArrayContentIsDefensiveRepeatableAndDescribedByMetadata() throws Exception {
        byte[] bytes = "content".getBytes(StandardCharsets.UTF_8);
        EntryContent content = EntryContents.of(bytes);
        bytes[0] = 'X';

        CRC32 expectedCrc = new CRC32();
        expectedCrc.update("content".getBytes(StandardCharsets.UTF_8));

        assertTrue(content.isRepeatable());
        assertEquals(7, content.size());
        assertEquals(expectedCrc.getValue(), content.crc32());
        try (InputStream first = content.open();
                InputStream second = content.open()) {
            assertNotSame(first, second);
            assertArrayEquals("content".getBytes(StandardCharsets.UTF_8), read(first));
            assertArrayEquals("content".getBytes(StandardCharsets.UTF_8), read(second));
        }
    }

    private byte[] read(InputStream inputStream) throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[32];
        int read;
        while ((read = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, read);
        }
        return outputStream.toByteArray();
    }
}
