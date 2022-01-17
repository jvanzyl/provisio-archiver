package ca.vanzyl.provisio.archive.source;

import ca.vanzyl.provisio.archive.perms.FileMode;
import ca.vanzyl.provisio.archive.ExtendedArchiveEntry;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Date;

public class FileEntry implements ExtendedArchiveEntry {

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
  public boolean isSymbolicLink() {
    return false;
  }

  @Override
  public String getSymbolicLinkPath() {
    return null;
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
    return file.length();
  }

  @Override
  public void writeEntry(OutputStream outputStream) throws IOException {
    if (file.isDirectory()) {
      return;
    }
    try (InputStream entryInputStream = getInputStream()) {
      entryInputStream.transferTo(outputStream);
    }
  }

  @Override
  public void setFileMode(int mode) {}

  @Override
  public int getFileMode() {
    return FileMode.getFileMode(file);
  }

  @Override
  public void setSize(long size) {}

  @Override
  public void setTime(long time) {}

  @Override
  public boolean isDirectory() {
    return file.isDirectory();
  }

  @Override
  public Date getLastModifiedDate() {
    return null;
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
