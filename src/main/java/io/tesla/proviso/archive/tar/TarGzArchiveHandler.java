package io.tesla.proviso.archive.tar;

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

import io.tesla.proviso.archive.ArchiveHandlerSupport;
import io.tesla.proviso.archive.Entry;
import io.tesla.proviso.archive.ExtendedArchiveEntry;
import io.tesla.proviso.archive.Source;

public class TarGzArchiveHandler extends ArchiveHandlerSupport {

  private final File archive;

  public TarGzArchiveHandler(File archive) {
    this.archive = archive;
  }

  @Override
  public ExtendedArchiveEntry newEntry(String entryName, Entry entry) {
    return new ExtendedTarArchiveEntry(entryName, entry);
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
  public Source getArchiveSource() {
    return new TarGzArchiveSource(archive);
  }
}
