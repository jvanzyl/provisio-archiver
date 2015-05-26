package io.tesla.proviso.archive;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Iterator;
import java.util.List;

import com.google.common.io.ByteStreams;

public class StringListSource implements Source {

  private List<String> entries;
  
  public StringListSource(List<String> entries) {
    this.entries = entries;
  }

  @Override
  public Iterable<Entry> entries() {
    return new Iterable<Entry>() {
      @Override
      public Iterator<Entry> iterator() {
        return new StringEntryIterator(entries.iterator());
      }
    };
  }

  @Override
  public boolean isDirectory() {
    return false;
  }

  @Override
  public void close() throws IOException {
  }

  class StringEntry implements Entry {

    final String name;
    
    public StringEntry(String name) {
      this.name = name;
    }

    @Override
    public String getName() {
      return name;
    }

    @Override
    public InputStream getInputStream() throws IOException {
      return new ByteArrayInputStream(name.getBytes());
    }
    
    @Override
    public long getSize() {
      return name.length();
    }

    @Override
    public void writeEntry(OutputStream outputStream) throws IOException {
      ByteStreams.copy(getInputStream(), outputStream);
    }

    @Override
    public int getFileMode() {
      return 0;
    }

    @Override
    public boolean isDirectory() {
      return false;
    }

    @Override
    public boolean isExecutable() {
      return false;
    }

    @Override
    public long getTime() {
      return 0;
    }
  }
  
  class StringEntryIterator implements Iterator<Entry> {

    final Iterator<String> delegate;
    
    public StringEntryIterator(Iterator<String> delegate) {
      this.delegate = delegate;
    }

    @Override
    public boolean hasNext() {
      return delegate.hasNext();
    }

    @Override
    public Entry next() {
      return new StringEntry(delegate.next());
    }

    @Override
    public void remove() {
      throw new UnsupportedOperationException("remove method not implemented");
    }
  }
}
