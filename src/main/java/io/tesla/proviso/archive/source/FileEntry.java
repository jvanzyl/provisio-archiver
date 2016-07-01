package io.tesla.proviso.archive.source;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import com.google.common.io.ByteStreams;

import io.tesla.proviso.archive.Entry;
import io.tesla.proviso.archive.perms.FileMode;

public class FileEntry implements Entry {

  private final String name;
  private final File file;

  public FileEntry(String name, File file) {
    this.name = name;
    this.file = file;
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public InputStream getInputStream() throws IOException {
    return new FileInputStream(file);
  }

  @Override
  public long getSize() {
    return file.length();
  }

  @Override
  public void writeEntry(OutputStream outputStream) throws IOException {
    if (file.isDirectory()) {
      return;
    }
    try (InputStream entryInputStream = getInputStream()) {
      ByteStreams.copy(entryInputStream, outputStream);
    }
  }

  @Override
  public int getFileMode() {
    return FileMode.getFileMode(file);
  }

  @Override
  public boolean isDirectory() {
    return file.isDirectory();
  }

  @Override
  public boolean isExecutable() {
    return FileMode.EXECUTABLE_FILE.equals(getFileMode());
  }

  @Override
  public long getTime() {
    return file.lastModified();
  }
}
