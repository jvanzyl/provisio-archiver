package io.tesla.proviso.archive.tar;

import io.tesla.proviso.archive.Archiver;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.ArchiveOutputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.archivers.tar.TarConstants;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;

import io.tesla.proviso.archive.ArchiveHandlerSupport;
import io.tesla.proviso.archive.Entry;
import io.tesla.proviso.archive.ExtendedArchiveEntry;
import io.tesla.proviso.archive.Source;

public class TarGzArchiveHandler extends ArchiveHandlerSupport {

  private final File archive;
  private final boolean posixLongFileMode;
  private final Archiver archiver;

  public TarGzArchiveHandler(File archive, boolean posixLongFileMode, Archiver archiver) {
    this.archive = archive;
    this.posixLongFileMode = posixLongFileMode;
    this.archiver = archiver;
  }

  @Override
  public ExtendedArchiveEntry newEntry(String entryName, Entry entry) {
    if(archiver != null && archiver.useHardLinks()) {
      Entry source = archiver.alreadyProcessed(entry);
      if(source != null) {
        ExtendedTarArchiveEntry tarArchiveEntry = new ExtendedTarArchiveEntry(entryName, TarConstants.LF_LINK);
        tarArchiveEntry.setLinkName(source.getName());
        return tarArchiveEntry;
      }
    }
    ExtendedTarArchiveEntry tarArchiveEntry = new ExtendedTarArchiveEntry(entryName, entry);
    tarArchiveEntry.setSize(entry.getSize());
    return tarArchiveEntry;
  }

  @Override
  public ArchiveInputStream getInputStream() throws IOException {
    return new TarArchiveInputStream(new GzipCompressorInputStream(new FileInputStream(archive)));
  }

  @Override
  public ArchiveOutputStream getOutputStream() throws IOException {
    TarArchiveOutputStream stream = new TarArchiveOutputStream(new GzipCompressorOutputStream(new FileOutputStream(archive)));
    if (posixLongFileMode) {
      stream.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
    }
    return stream;
  }

  @Override
  public Source getArchiveSource() {
    return new TarGzArchiveSource(archive);
  }
}
