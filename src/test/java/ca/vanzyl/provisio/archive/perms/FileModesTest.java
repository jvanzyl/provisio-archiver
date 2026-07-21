package ca.vanzyl.provisio.archive.perms;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class FileModesTest {

    @Test
    public void allPermissionModesRoundTrip() {
        for (int mode = 0; mode <= 0777; mode++) {
            assertEquals(mode, FileModes.fromPermissions(FileModes.toPermissions(mode)));
        }
    }

    @Test
    public void archiveTypeBitsDoNotAffectPermissions() {
        assertEquals(FileModes.toPermissions(0755), FileModes.toPermissions(FileModes.EXECUTABLE_FILE));
    }

    @Test
    public void unixRenderingPreservesPermissionPositions() {
        assertEquals("-rwxr-xr-x", FileModes.toUnix(0755));
        assertEquals("-rw-r--r--", FileModes.toUnix(0644));
        assertEquals("----------", FileModes.toUnix(0));
    }
}
