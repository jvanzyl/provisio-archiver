package io.tesla.proviso.archive.source;

import io.tesla.proviso.archive.Entry;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class DirectoryEntry implements Entry {

  private String name;

  public DirectoryEntry(String name) {
    if (!name.endsWith("/")) {
      throw new IllegalArgumentException();
    }
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
  public long getSize() {
    return 0;
  }

  @Override
  public void writeEntry(OutputStream outputStream) throws IOException {}

  @Override
  public int getFileMode() {
    return -1;
  }

  @Override
  public boolean isDirectory() {
    return true;
  }

}
