package io.tesla.proviso.archive.tar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import io.airlift.command.Command;
import io.airlift.command.CommandResult;
import io.airlift.units.Duration;
import io.tesla.proviso.archive.ArchiverValidator;
import io.tesla.proviso.archive.Entry;
import io.tesla.proviso.archive.Source;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.collect.Maps;
import com.google.common.io.ByteStreams;
import com.google.common.util.concurrent.ThreadFactoryBuilder;

public class TarGzArchiveValidator implements ArchiverValidator {

  private int count;
  private Map<String, String> entries;
  private static final Executor executor = Executors.newCachedThreadPool(new ThreadFactoryBuilder().setDaemon(true).setNameFormat("tar-command-%s").build());

  public TarGzArchiveValidator(File archive) throws Exception {
    entries = tarEntries(archive);
    count = entries.size();
    Source source = new TarGzArchiveSource(archive);
    for (Entry entry : source.entries()) {
      OutputStream outputStream = new ByteArrayOutputStream();
      ByteStreams.copy(entry.getInputStream(), outputStream);
      entries.put(entry.getName(), outputStream.toString());
    }
  }

  public int count() {
    return entries.size();
  }

  public void assertNumberOfEntriesInArchive(int expectedEntries) throws IOException {
    String message = String.format("Expected %s entries.", count);
    assertEquals(message, expectedEntries, count);
  }

  public void assertPresenceOfEntryInArchive(String entryName) throws IOException {
    assertTrue(String.format("The entry %s is expected to be present, but it is not.", entryName), entries.containsKey(entryName));
  }

  public void assertAbsenceOfEntryInArchive(String entryName) throws IOException {
    assertFalse(String.format("The entry %s is not expected to be present, but is not.", entryName), entries.containsKey(entryName));
  }

  public void assertContentOfEntryInArchive(String entryName, String expectedEntryContent) throws IOException {
    assertTrue(String.format("The entry %s is expected to be present, but is not.", entryName), entries.containsKey(entryName));
    assertEquals(String.format("The entry %s is expected to have the content '%s'.", entryName, expectedEntryContent), expectedEntryContent, entries.get(entryName));
  }

  //
  // Not sure why I can't get the accurate entries from the Java TAR command but entries are missed. Could be my code or a bug in commons-compress but
  // I'm using native code for now to get all the entries. The output looks something like:
  //
  // archive-0/0/0.txt
  // archive-0/1/1.txt
  // archive-0/2/2.txt
  // archive-0/3/3.txt
  // archive-0/4/4.txt
  // archive-0/0/
  // archive-0/1/
  // archive-0/2/
  // archive-0/3/
  // archive-0/4/  
  //
  public Map<String, String> tarEntries(File tarFile) throws Exception {
    Map<String, String> entries = Maps.newHashMap();
    Duration timeLimit = new Duration(5, TimeUnit.MINUTES);
    Preconditions.checkNotNull(tarFile, "tarFile is null");
    Command command = new Command("tar", "tf", tarFile.getAbsolutePath());
    command.setTimeLimit(timeLimit);
    CommandResult result = command.execute(executor);
    Splitter splitter = Splitter.on(System.getProperty("line.separator")).trimResults().omitEmptyStrings();
    for (String line : splitter.split(result.getCommandOutput())) {
      entries.put(line, "");
    }
    return entries;
  }
}
