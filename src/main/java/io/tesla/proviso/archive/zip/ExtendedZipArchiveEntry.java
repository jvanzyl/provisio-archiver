package io.tesla.proviso.archive.zip;

import io.tesla.proviso.archive.ExtendedArchiveEntry;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;

public class ExtendedZipArchiveEntry extends ZipArchiveEntry implements ExtendedArchiveEntry {

  private ExtendedArchiveEntry entry;

  public ExtendedZipArchiveEntry(String entryName, ExtendedArchiveEntry entry) {
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

  @Override
  public InputStream getInputStream() throws IOException {
    return entry.getInputStream();
  }

  @Override
  public boolean isHardLink() {
    return false;
  }

  @Override
  public boolean isExecutable() {
    return false;
  }
}
