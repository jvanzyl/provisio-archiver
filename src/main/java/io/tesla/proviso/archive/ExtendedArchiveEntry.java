package io.tesla.proviso.archive;

import java.io.IOException;
import java.io.OutputStream;

import org.apache.commons.compress.archivers.ArchiveEntry;

public interface ExtendedArchiveEntry extends ArchiveEntry {
  void setFileMode(int mode);

  int getFileMode();

  void setSize(long size);

  void setTime(long time);

  void writeEntry(OutputStream outputStream) throws IOException;
}
