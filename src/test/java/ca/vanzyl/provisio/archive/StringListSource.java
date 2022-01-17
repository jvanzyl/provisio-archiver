package ca.vanzyl.provisio.archive;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

public class StringListSource implements Source {

  private final List<String> entries;
  
  public StringListSource(List<String> entries) {
    this.entries = entries;
  }

  @Override
  public Iterable<ExtendedArchiveEntry> entries() {
    return () -> new StringEntryIterator(entries.iterator());
  }

  @Override
  public boolean isDirectory() {
    return false;
  }

  @Override
  public void close() {
  }

  static class StringEntry implements ExtendedArchiveEntry {

    final String name;
    
    public StringEntry(String name) {
      this.name = name;
    }

    @Override
    public String getName() {
      return name;
    }

    @Override
    public InputStream getInputStream() {
      return new ByteArrayInputStream(name.getBytes());
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
      return name.length();
    }

    @Override
    public void writeEntry(OutputStream outputStream) throws IOException {
      getInputStream().transferTo(outputStream);
    }

    @Override
    public void setFileMode(int mode) {}

    @Override
    public int getFileMode() {
      return 0;
    }

    @Override
    public void setSize(long size) {}

    @Override
    public void setTime(long time) {}

    @Override
    public boolean isDirectory() {
      return false;
    }

    @Override
    public Date getLastModifiedDate() {
      return null;
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
  
  static class StringEntryIterator implements Iterator<ExtendedArchiveEntry> {

    final Iterator<String> delegate;
    
    public StringEntryIterator(Iterator<String> delegate) {
      this.delegate = delegate;
    }

    @Override
    public boolean hasNext() {
      return delegate.hasNext();
    }

    @Override
    public ExtendedArchiveEntry next() {
      return new StringEntry(delegate.next());
    }

    @Override
    public void remove() {
      throw new UnsupportedOperationException("remove method not implemented");
    }
  }
}
