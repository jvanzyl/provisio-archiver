package io.tesla.proviso.archive.tar;

import io.tesla.proviso.archive.ArchiveHandler;
import io.tesla.proviso.archive.Entry;
import io.tesla.proviso.archive.ExtendedArchiveEntry;
import io.tesla.proviso.archive.FileMode;
import io.tesla.proviso.archive.Source;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.ArchiveOutputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;

public class TarGzArchiveHandler implements ArchiveHandler {

  private final File archive;

  public TarGzArchiveHandler(File archive) {
    this.archive = archive;
  }

  @Override
  public ArchiveInputStream getInputStream() throws IOException {
    return new TarArchiveInputStream(new GzipCompressorInputStream(new FileInputStream(archive)));
  }

  @Override
  public ArchiveOutputStream getOutputStream() throws IOException {
    return new TarArchiveOutputStream(new GzipCompressorOutputStream(new FileOutputStream(archive)));
  }

  @Override
  public ExtendedArchiveEntry createEntryFor(String entryName, Entry archiveEntry, boolean isExecutable) {
    ExtendedTarArchiveEntry entry = new ExtendedTarArchiveEntry(entryName);
    entry.setSize(archiveEntry.getSize());
    if (archiveEntry.getFileMode() != -1) {
      entry.setMode(archiveEntry.getFileMode());
      if (isExecutable) {
        entry.setMode(FileMode.makeExecutable(entry.getMode()));
      }
    } else {
      if (isExecutable) {
        entry.setMode(FileMode.EXECUTABLE_FILE.getBits());
      }
    }
    return entry;
  }

  @Override
  public Source getArchiveSource() {
    return new TarGzArchiveSource(archive);
  }
}