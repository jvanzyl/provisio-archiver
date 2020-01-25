package ca.vanzyl.provisio.archive;

import java.io.IOException;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.ArchiveOutputStream;

public interface ArchiveHandler {
  ArchiveOutputStream getOutputStream() throws IOException;

  ArchiveInputStream getInputStream() throws IOException;

  ExtendedArchiveEntry createEntryFor(String entryName, ExtendedArchiveEntry entry, boolean isExecutable);

  ExtendedArchiveEntry newEntry(String entryName, ExtendedArchiveEntry entry);

  Source getArchiveSource();
}
