package io.tesla.proviso.archive;

import java.io.IOException;

public interface ArchiverValidator {
  public void assertNumberOfEntriesInArchive(int expectedEntries) throws IOException;

  public void assertContentOfEntryInArchive(String entryName, String expectedEntryContent) throws IOException;

  public void assertEntries(String... entries) throws IOException;
}
