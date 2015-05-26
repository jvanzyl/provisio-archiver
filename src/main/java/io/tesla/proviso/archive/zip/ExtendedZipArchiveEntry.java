package io.tesla.proviso.archive.zip;

import java.io.IOException;
import java.io.OutputStream;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;

import io.tesla.proviso.archive.Entry;
import io.tesla.proviso.archive.ExtendedArchiveEntry;

public class ExtendedZipArchiveEntry extends ZipArchiveEntry implements ExtendedArchiveEntry {

  private Entry entry;

  public ExtendedZipArchiveEntry(String entryName, Entry entry) {
    super(entryName);
    this.entry = entry;
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

  @Override
  public void writeEntry(OutputStream outputStream) throws IOException {
    entry.writeEntry(outputStream);
  }
}
