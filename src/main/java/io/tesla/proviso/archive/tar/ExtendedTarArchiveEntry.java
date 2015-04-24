package io.tesla.proviso.archive.tar;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;

import io.tesla.proviso.archive.ExtendedArchiveEntry;

public class ExtendedTarArchiveEntry extends TarArchiveEntry implements ExtendedArchiveEntry {
  public ExtendedTarArchiveEntry(String name) {
    super(name);
  }

  @Override
  public void setFileMode(int mode) {
    setMode(mode);
  }

  @Override
  public int getFileMode() {
    return getMode();
  }

  @Override
  public void setTime(long time) {
    setModTime(time);
  }
}
