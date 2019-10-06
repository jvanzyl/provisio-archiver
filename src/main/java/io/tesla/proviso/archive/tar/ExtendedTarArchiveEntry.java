package io.tesla.proviso.archive.tar;

import java.io.IOException;
import java.io.OutputStream;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;

import io.tesla.proviso.archive.Entry;
import io.tesla.proviso.archive.ExtendedArchiveEntry;

public class ExtendedTarArchiveEntry extends TarArchiveEntry implements ExtendedArchiveEntry {

  private Entry entry;
  private boolean hardLink;

  public ExtendedTarArchiveEntry(String entryName, byte linkFlag) {
    super(entryName, linkFlag);
    this.hardLink = true;
  }

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

  public boolean isHardLink() {
    return hardLink;
  }

  @Override
  public void setTime(long time) {
    setModTime(time);
  }

  @Override
  public void writeEntry(OutputStream outputStream) throws IOException {
    if(!hardLink) {
      entry.writeEntry(outputStream);
    }
  }
}
