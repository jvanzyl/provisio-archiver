package io.tesla.proviso.archive.tar;

import io.tesla.proviso.archive.ExtendedArchiveEntry;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;

public class ExtendedTarArchiveEntry extends TarArchiveEntry implements ExtendedArchiveEntry {

  private ExtendedArchiveEntry entry;
  private boolean hardLink;

  public ExtendedTarArchiveEntry(String entryName, byte linkFlag) {
    super(entryName, linkFlag);
    this.hardLink = true;
  }

  public ExtendedTarArchiveEntry(String entryName, ExtendedArchiveEntry entry) {
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
  public boolean isExecutable() {
    return false;
  }

  @Override
  public long getTime() {
    return 0;
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

  @Override
  public InputStream getInputStream() throws IOException {
    return entry.getInputStream();
  }
}
