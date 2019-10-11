package io.tesla.proviso.archive.tar;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import io.tesla.proviso.archive.Archiver;
import io.tesla.proviso.archive.Selector;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import java.util.List;
import java.util.Map;
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
  private final Map<String,Entry> processedFilesNames;
  private final Selector hardLinkSelector;

  public TarGzArchiveHandler(File archive, boolean posixLongFileMode, List<String> hardLinkIncludes, List<String> hardLinkExcludes) {
    this.archive = archive;
    this.posixLongFileMode = posixLongFileMode;
    this.processedFilesNames = Maps.newLinkedHashMap();
    if (hardLinkIncludes.size() > 0 || hardLinkExcludes.size() > 0) {
      this.hardLinkSelector = new Selector(hardLinkIncludes, hardLinkExcludes);
    } else {
      this.hardLinkSelector = new Selector(null, ImmutableList.of("**/**"));
    }
  }

  @Override
  public ExtendedArchiveEntry newEntry(String entryName, Entry entry) {
    if(hardLinkSelector.include(entryName)) {
      Entry sourceToHardLink = processedFilesNames.get(fileNameOf(entry));
      if(sourceToHardLink != null) {
        ExtendedTarArchiveEntry tarArchiveEntry = new ExtendedTarArchiveEntry(entryName, TarConstants.LF_LINK);
        tarArchiveEntry.setLinkName(sourceToHardLink.getName());
        return tarArchiveEntry;
      }
      processedFilesNames.put(fileNameOf(entry), entry);
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

  private String fileNameOf(Entry entry) {
    return entry.getName().substring(entry.getName().lastIndexOf('/') + 1);
  }
}
