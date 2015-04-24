package io.tesla.proviso.archive;

import org.apache.commons.compress.archivers.ArchiveEntry;

public interface ExtendedArchiveEntry extends ArchiveEntry {
  void setFileMode(int mode);

  int getFileMode();

  void setSize(long size);

  void setTime(long time);

}
