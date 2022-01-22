package ca.vanzyl.provisio.archive.zip;

import ca.vanzyl.provisio.archive.ExtendedArchiveEntry;
import ca.vanzyl.provisio.archive.Source;
import ca.vanzyl.provisio.archive.perms.FileMode;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Date;
import java.util.Enumeration;
import java.util.Iterator;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;

public class ZipArchiveSource implements Source {

  private final ZipFile zipFile;
  private final Enumeration<ZipArchiveEntry> entries;

  public ZipArchiveSource(File archive) {
    try {
      zipFile = new ZipFile(archive, "UTF8", false) {
        @Override
        protected void finalize() throws Throwable {
          super.finalize();
          super.close();
        }
      };
      entries = zipFile.getEntries();
    } catch (IOException e) {
      throw new RuntimeException(String.format("Cannot determine the type of archive %s.", archive), e);
    }
  }

  @Override
  public Iterable<ExtendedArchiveEntry> entries() {
    return ArchiveEntryIterator::new;
  }

  class EntrySourceArchiveEntry implements ExtendedArchiveEntry {

    final ZipArchiveEntry archiveEntry;

    public EntrySourceArchiveEntry(ZipArchiveEntry archiveEntry) {
      this.archiveEntry = archiveEntry;
    }

    @Override
    public String getName() {
      return archiveEntry.getName();
    }

    @Override
    public InputStream getInputStream() throws IOException {
      return zipFile.getInputStream(archiveEntry);
    }

    @Override
    public boolean isSymbolicLink() {
      return archiveEntry.isUnixSymlink();
    }

    @Override
    public String getSymbolicLinkPath() {
      try(InputStream is = getInputStream(); ByteArrayOutputStream os = new ByteArrayOutputStream()) {
        is.transferTo(os);
        return os.toString();
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
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
      return archiveEntry.getSize();
    }

    @Override
    public void writeEntry(OutputStream outputStream) throws IOException {
      // We specifically do not close the entry because if you do then you can't read anymore archive entries from the stream
      getInputStream().transferTo(outputStream);
    }

    @Override
    public void setFileMode(int mode) {}

    @Override
    public int getFileMode() {
      return archiveEntry.getUnixMode();
    }

    @Override
    public void setSize(long size) {}

    @Override
    public void setTime(long time) {}

    @Override
    public boolean isDirectory() {
      return archiveEntry.isDirectory();
    }

    @Override
    public Date getLastModifiedDate() {
      return null;
    }

    @Override
    public boolean isExecutable() {
      return FileMode.EXECUTABLE_FILE.equals(getFileMode());
    }

    @Override
    public long getTime() {
      return archiveEntry.getTime();
    }
  }

  class ArchiveEntryIterator implements Iterator<ExtendedArchiveEntry> {

    @Override
    public ExtendedArchiveEntry next() {
      return new EntrySourceArchiveEntry(entries.nextElement());
    }

    @Override
    public boolean hasNext() {
      return entries.hasMoreElements();
    }

    @Override
    public void remove() {
      throw new UnsupportedOperationException("remove method not implemented");
    }
  }

  @Override
  public void close() throws IOException {
    zipFile.close();
  }

  @Override
  public boolean isDirectory() {
    return true;
  }

}
