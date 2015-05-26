package io.tesla.proviso.archive;

import static org.junit.Assert.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.google.common.base.Function;
import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.google.common.io.ByteStreams;

public abstract class AbstractArchiveValidator implements ArchiverValidator {

  protected final ListMultimap<String, TestEntry> entries;

  protected AbstractArchiveValidator(Source source) throws IOException {
    ListMultimap<String, TestEntry> entries = LinkedListMultimap.create();
    for (Entry entry : source.entries()) {
      OutputStream outputStream = new ByteArrayOutputStream();
      ByteStreams.copy(entry.getInputStream(), outputStream);
      entries.put(entry.getName(), new TestEntry(entry.getName(), outputStream.toString(), entry.getTime()));
    }
    this.entries = entries;
  }

  @Override
  public void assertEntries(String... expectedEntries) throws IOException {
    String expected = toString(Arrays.asList(expectedEntries));
    List<String> actual = new ArrayList<>();
    for (Map.Entry<String, TestEntry> entry : entries.entries()) {
      actual.add(entry.getKey());
    }
    assertEquals("Archive entries", expected, toString(actual));
  }

  private String toString(Collection<String> strings) {
    List<String> sorted = new ArrayList<>(strings);
    Collections.sort(sorted);
    StringBuilder sb = new StringBuilder();
    for (String string : sorted) {
      sb.append(string).append('\n');
    }
    return sb.toString();
  }

  @Override
  public void assertSortedEntries(String... expectedEntries) throws IOException {
    String expected = toString(Arrays.asList(expectedEntries));
    List<String> actual = new ArrayList<>();
    for (Map.Entry<String, TestEntry> entry : entries.entries()) {
      actual.add(entry.getKey());
    }
    assertEquals("Archive entries", expected, toNonSortedString(actual));
  }

  private String toNonSortedString(Collection<String> strings) {
    List<String> sorted = new ArrayList<>(strings);
    StringBuilder sb = new StringBuilder();
    for (String string : sorted) {
      sb.append(string).append('\n');
    }
    return sb.toString();
  }

  @Override
  public void assertNumberOfEntriesInArchive(int expectedEntries) throws IOException {
    assertEquals("Number of archive entries", expectedEntries, entries.size());
  }

  @Override
  public void assertContentOfEntryInArchive(String entryName, String expectedEntryContent) throws IOException {
    List<String> values = Lists.transform(entries.get(entryName), new Function<TestEntry, String>() {
      @Override
      public String apply(TestEntry input) {
        return input.content;
      }
    });
    assertEquals(String.format("Number of archive entries with path  %s", entryName), 1, values.size());
    assertEquals(String.format("Archive entry %s contents", entryName), expectedEntryContent, values.get(0));
  }

  @Override
  public void assertTimeOfEntryInArchive(String entryName, long time) throws IOException {
    List<Long> values = Lists.transform(entries.get(entryName), new Function<TestEntry, Long>() {
      @Override
      public Long apply(TestEntry input) {
        return input.time;
      }
    });
    assertEquals(String.format("Number of archive entries with path  %s", entryName), 1, values.size());
    assertEquals(String.format("Archive entry %s time", entryName), time, values.get(0).longValue());
  }

  class TestEntry {
    String name;
    String content;
    long time;

    TestEntry(String name, String content, long time) {
      this.name = name;
      this.content = content;
      this.time = time;
    }
  }
}


