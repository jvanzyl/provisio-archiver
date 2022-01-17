package ca.vanzyl.provisio.archive;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import org.apache.commons.compress.archivers.ArchiveEntry;

public interface ExtendedArchiveEntry extends ArchiveEntry {
  void setFileMode(int mode);

  int getFileMode();

  void setSize(long size);

  void setTime(long time);

  void writeEntry(OutputStream outputStream) throws IOException;

  InputStream getInputStream() throws IOException;

  boolean isSymbolicLink();

  String getSymbolicLinkPath();

  boolean isHardLink();

  String getHardLinkPath();

  boolean isExecutable();

  long getTime();
}
