package ca.vanzyl.provisio.archive.source;

import ca.vanzyl.provisio.archive.ExtendedArchiveEntry;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Date;

public class DirectoryEntry implements ExtendedArchiveEntry {

  private String name;

  public DirectoryEntry(String name) {
    if (!name.endsWith("/")) {
      throw new IllegalArgumentException();
    }
    this.name = name;
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public InputStream getInputStream() throws IOException {
    return new ByteArrayInputStream(new byte[0]);
  }

  @Override
  public boolean isHardLink() {
    return false;
  }

  @Override
  public String getHardLinkPath() {
    return null;
  }

  @Override
  public long getSize() {
    return 0;
  }

  @Override
  public void writeEntry(OutputStream outputStream) throws IOException {}

  @Override
  public void setFileMode(int mode) {}

  @Override
  public int getFileMode() {
    return -1;
  }

  @Override
  public void setSize(long size) {}

  @Override
  public void setTime(long time) {}

  @Override
  public boolean isDirectory() {
    return true;
  }

  @Override
  public Date getLastModifiedDate() {
    return null;
  }

  @Override
  public boolean isExecutable() {
    return false;
  }

  @Override
  public long getTime() {
    return 0;
  }
}
