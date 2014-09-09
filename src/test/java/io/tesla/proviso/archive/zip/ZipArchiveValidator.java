package io.tesla.proviso.archive.zip;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import io.tesla.proviso.archive.ArchiverValidator;
import io.tesla.proviso.archive.Entry;
import io.tesla.proviso.archive.Source;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.io.ByteStreams;

public class ZipArchiveValidator implements ArchiverValidator {

  private int count;
  private Map<String, String> entries;
  
  public ZipArchiveValidator(File archive) throws IOException {
    count = 0;
    entries = Maps.newHashMap();
    Source source = new ZipArchiveSource(archive);    
    for(Entry entry : source.entries()) {
      OutputStream outputStream = new ByteArrayOutputStream();
      ByteStreams.copy(entry.getInputStream(), outputStream);
      entries.put(entry.getName(), outputStream.toString());      
      count++;
    }
  }
  
  public int count() {
    return count;
  }
  
  @Override
  public void assertNumberOfEntriesInArchive(int expectedEntries) throws IOException {
    String message = String.format("Expected %s entries.", count);
    assertEquals(message, expectedEntries, count);
  }

  @Override
  public void assertContentOfEntryInArchive(String entryName, String expectedEntryContent) throws IOException {
    assertTrue(String.format("The entry %s is expected to be present, but is not.", entryName), entries.containsKey(entryName));
    assertEquals(String.format("The entry %s is expected to have the content '%s'.", entryName, expectedEntryContent), expectedEntryContent, entries.get(entryName));
  }

  @Override
  public void assertEntries(String... expectedEntries) throws IOException {
    String expected = toString(Sets.newHashSet(expectedEntries));
    String actual = toString(entries.keySet());
    assertEquals("Archive entries", expected, actual);
  }

  private String toString(Iterable<String> strings) {
    StringBuilder sb = new StringBuilder();
    for (String string : Sets.newTreeSet(strings)) {
      sb.append(string).append('\n');
    }
    return sb.toString();
  }
}
