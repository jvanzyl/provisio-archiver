package io.tesla.proviso.archive.source;

import io.tesla.proviso.archive.Entry;
import io.tesla.proviso.archive.Source;

import java.io.File;
import java.io.IOException;
import java.util.Collections;

public class FileSource implements Source {

  private final String archiveEntryName;
  private final File file;

  public FileSource(File file) {
    this.archiveEntryName = file.getName();
    this.file = file;
  }

  public FileSource(String archiveEntryName, File file) {
    this.archiveEntryName = archiveEntryName;
    this.file = file;
  }

  @Override
  public Iterable<Entry> entries() {
    return Collections.<Entry>singleton(new FileEntry(archiveEntryName, file));
  }

  @Override
  public void close() throws IOException {
  }

  @Override
  public boolean isDirectory() {
    return false;
  }
}
