package io.tesla.proviso.archive.zip;

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
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;

public class ZipArchiveHandler implements ArchiveHandler {

  private final File archive;

  public ZipArchiveHandler(File archive) {
    this.archive = archive;
  }

  @Override
  public ArchiveInputStream getInputStream() throws IOException {
    return new ZipArchiveInputStream(new FileInputStream(archive));
  }

  @Override
  public ArchiveOutputStream getOutputStream() throws IOException {
    return new ZipArchiveOutputStream(new FileOutputStream(archive));
  }

  @Override
  public ExtendedArchiveEntry createEntryFor(String entryName, Entry archiveEntry, boolean isExecutable) {
    ExtendedZipArchiveEntry entry = new ExtendedZipArchiveEntry(entryName);
    entry.setSize(archiveEntry.getSize());
    if (archiveEntry.getFileMode() != -1) {
      entry.setUnixMode(archiveEntry.getFileMode());
      if(isExecutable) {
        entry.setUnixMode(FileMode.makeExecutable(entry.getUnixMode()));
      }
    } else {
      if (isExecutable) {
        entry.setUnixMode(FileMode.EXECUTABLE_FILE.getBits());
      }
    }
    return entry;
  }

  @Override
  public Source getArchiveSource() {
    return new ZipArchiveSource(archive);
  }
}
