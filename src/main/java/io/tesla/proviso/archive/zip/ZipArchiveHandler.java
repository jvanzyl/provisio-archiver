package io.tesla.proviso.archive.zip;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.ArchiveOutputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;

import io.tesla.proviso.archive.ArchiveHandlerSupport;
import io.tesla.proviso.archive.Entry;
import io.tesla.proviso.archive.ExtendedArchiveEntry;
import io.tesla.proviso.archive.Source;

public class ZipArchiveHandler extends ArchiveHandlerSupport {

  private final File archive;

  public ZipArchiveHandler(File archive) {
    this.archive = archive;
  }

  @Override
  public ExtendedArchiveEntry newEntry(String entryName, Entry entry) {
    return new ExtendedZipArchiveEntry(entryName, entry);
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
  public Source getArchiveSource() {
    return new ZipArchiveSource(archive);
  }
}
