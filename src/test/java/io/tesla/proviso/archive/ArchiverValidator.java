package io.tesla.proviso.archive;

import java.io.IOException;

public interface ArchiverValidator {
  public void assertNumberOfEntriesInArchive(int expectedEntries) throws IOException;

  public void assertContentOfEntryInArchive(String entryName, String expectedEntryContent) throws IOException;

  public void assertTimeOfEntryInArchive(String entryName, long time) throws IOException;

  public void assertEntries(String... entries) throws IOException;

  public void assertSortedEntries(String... entries) throws IOException;
}
