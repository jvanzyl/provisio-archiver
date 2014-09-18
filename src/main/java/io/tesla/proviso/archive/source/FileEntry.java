package io.tesla.proviso.archive.source;

import io.tesla.proviso.archive.Entry;
import io.tesla.proviso.archive.FileMode;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;

import com.google.common.io.ByteStreams;

public class FileEntry implements Entry {

  private final String name;
  private final File file;

  public FileEntry(String name, File file) {
    this.name = name;
    this.file = file;
  }

  public String getName() {
    return name;
  }

  public InputStream getInputStream() throws IOException {
    return new FileInputStream(file);
  }

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
}
