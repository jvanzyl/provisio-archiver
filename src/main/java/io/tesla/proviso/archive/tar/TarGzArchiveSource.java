package io.tesla.proviso.archive.tar;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Iterator;

import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;

import com.google.common.io.ByteStreams;
import com.google.common.io.Closer;

import io.tesla.proviso.archive.ArchiverHelper;
import io.tesla.proviso.archive.Entry;
import io.tesla.proviso.archive.Source;

public class TarGzArchiveSource implements Source {

  private final ArchiveInputStream archiveInputStream;
  private final Closer closer;

  public TarGzArchiveSource(File archive) {
    closer = Closer.create();
    try {
      archiveInputStream = closer.register(ArchiverHelper.getArchiveHandler(archive).getInputStream());
    } catch (IOException e) {
      throw new RuntimeException(String.format("Cannot determine the type of archive %s.", archive), e);
    }
  }

  @Override
  public Iterable<Entry> entries() {
    return new Iterable<Entry>() {
      @Override
      public Iterator<Entry> iterator() {
        return new ArchiveEntryIterator();
      }
    };
  }

  class EntrySourceArchiveEntry implements Entry {

    final TarArchiveEntry archiveEntry;

    public EntrySourceArchiveEntry(TarArchiveEntry archiveEntry) {
      this.archiveEntry = archiveEntry;
    }

    @Override
    public String getName() {
      return archiveEntry.getName();
    }

    @Override
    public InputStream getInputStream() throws IOException {
      return archiveInputStream;
    }

    @Override
    public long getSize() {
      return archiveEntry.getSize();
    }

    @Override
    public void writeEntry(OutputStream outputStream) throws IOException {
      // We specifically do not close the entry because if you do then you can't read anymore archive entries from the stream
      ByteStreams.copy(getInputStream(), outputStream);
    }

    @Override
    public int getFileMode() {
      return archiveEntry.getMode();
    }

    @Override
    public boolean isDirectory() {
      return archiveEntry.isDirectory();
    }

    @Override
    public boolean isExecutable() {
      return false;
    }

    @Override
    public long getTime() {
      return archiveEntry.getModTime().getTime();
    }
  }

  class ArchiveEntryIterator implements Iterator<Entry> {

    TarArchiveEntry archiveEntry;

    @Override
    public Entry next() {
      return new EntrySourceArchiveEntry(archiveEntry);
    }

    @Override
    public boolean hasNext() {
      try {
        return (archiveEntry = (TarArchiveEntry) archiveInputStream.getNextEntry()) != null;
      } catch (IOException e) {
        return false;
      }
    }

    @Override
    public void remove() {
      throw new UnsupportedOperationException("remove method not implemented");
    }
  }

  @Override
  public void close() throws IOException {
    closer.close();
  }

  @Override
  public boolean isDirectory() {
    return true;
  }

}
