package io.tesla.proviso.archive.zip;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;

import io.tesla.proviso.archive.ExtendedArchiveEntry;

public class ExtendedZipArchiveEntry extends ZipArchiveEntry implements ExtendedArchiveEntry {
  public ExtendedZipArchiveEntry(String name) {
    super(name);
  }

  @Override
  public void setFileMode(int mode) {
    setUnixMode(mode);
  }

  @Override
  public int getFileMode() {
    return getUnixMode();
  }

  @Override
  public void setTime(long time) {
    super.setTime(time);
  }
}
