package io.tesla.proviso.archive;

import java.io.IOException;

public interface Source {
  Iterable<ExtendedArchiveEntry> entries();

  boolean isDirectory();

  void close() throws IOException;
}
