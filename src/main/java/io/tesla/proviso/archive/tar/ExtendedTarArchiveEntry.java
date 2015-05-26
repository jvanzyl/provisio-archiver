package io.tesla.proviso.archive.tar;

import java.io.IOException;
import java.io.OutputStream;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;

import io.tesla.proviso.archive.Entry;
import io.tesla.proviso.archive.ExtendedArchiveEntry;

public class ExtendedTarArchiveEntry extends TarArchiveEntry implements ExtendedArchiveEntry {

  private Entry entry;

  public ExtendedTarArchiveEntry(String entryName, Entry entry) {
    super(entryName);
    this.entry = entry;
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

  @Override
  public void writeEntry(OutputStream outputStream) throws IOException {
    entry.writeEntry(outputStream);
  }
}
