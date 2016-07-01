package io.tesla.proviso.archive;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.inject.Named;
import javax.inject.Singleton;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.io.ByteStreams;

import io.tesla.proviso.archive.perms.FileMode;
import io.tesla.proviso.archive.perms.PosixModes;

// useRoot
// directories
// includes
// excludes

// There should be a full inventory of what has gone into the archive
// make a fluent interface

@Named
@Singleton
public class UnArchiver {

  private final Selector selector;
  private final boolean useRoot;
  private final boolean flatten;
  private final boolean posixLongFileMode;

  public UnArchiver(List<String> includes, List<String> excludes, boolean useRoot, boolean flatten, boolean posixLongFileMode) {
    this.useRoot = useRoot;
    this.flatten = flatten;
    this.selector = new Selector(includes, excludes);
    this.posixLongFileMode = posixLongFileMode;
  }

  public void unarchive(File archive, File outputDirectory) throws IOException {
    unarchive(archive, outputDirectory, new NoopEntryProcessor());
  }

  public void unarchive(File archive, File outputDirectory, UnarchivingEntryProcessor entryProcessor) throws IOException {
    //
    // These are the contributions that unpacking this archive is providing
    //
    if (outputDirectory.exists() == false) {
      outputDirectory.mkdirs();
    }
    Source source = ArchiverHelper.getArchiveHandler(archive, posixLongFileMode).getArchiveSource();
    for (Entry archiveEntry : source.entries()) {
      String entryName = archiveEntry.getName();
      if (useRoot == false) {
        entryName = entryName.substring(entryName.indexOf('/') + 1);
      }
      if (!selector.include(entryName)) {
        continue;
      }
      //
      // Process the entry name before any output is created on disk
      //
      entryName = entryProcessor.processName(entryName);
      //
      // So with an entry we may want to take a set of entry in a set of directories and flatten them
      // into one directory, or we may want to preserve the directory structure.
      //
      if (flatten) {
        entryName = entryName.substring(entryName.lastIndexOf("/") + 1);
      } else {
        if (archiveEntry.isDirectory()) {
          createDir(new File(outputDirectory, entryName));
          continue;
        }
      }
      File outputFile = new File(outputDirectory, entryName);
      //
      // If we take an archive and flatten it into the output directory the first entry will
      // match the output directory which exists so it will cause an error trying to make it
      //
      if (outputFile.getAbsolutePath().equals(outputDirectory.getAbsolutePath())) {
        continue;
      }
      if (!outputFile.getParentFile().exists()) {
        createDir(outputFile.getParentFile());
      }

      try (OutputStream outputStream = new BufferedOutputStream(new FileOutputStream(outputFile))) {
        entryProcessor.processStream(archiveEntry.getName(), archiveEntry.getInputStream(), outputStream);
      }
      int mode = archiveEntry.getFileMode();
      //
      // Currently zip entries produced by plexus-archiver return 0 for the unix mode, so I'm doing something wrong or
      // it's not being stored directly. So in the case of unpacking an zip archive we don't want to produce files
      // that are unreadble or unusable so we'll give files 0644 and directories 0755
      //
      if (mode > 0) {
        setFilePermissions(outputFile, FileMode.toPermissionsSet(mode));
      } else {
        if (archiveEntry.isDirectory()) {
          setFilePermissions(outputFile, PosixModes.intModeToPosix(0755));
        } else {
          setFilePermissions(outputFile, PosixModes.intModeToPosix(0644));
        }
      }
    }
    source.close();
  }

  private void setFilePermissions(File file, Set<PosixFilePermission> perms) throws IOException {
    try {
      Files.setPosixFilePermissions(file.toPath(), perms);
    } catch (UnsupportedOperationException e) {
      // ignore, must be windows
    }
  }

  private void createDir(File dir) {
    if (dir.exists() == false) {
      dir.mkdirs();
    }
  }

  //
  // Archiver archiver = Archiver.builder()
  // .includes("**/*.java")
  // .includes(Iterable<String>)
  // .excludes("**/*.properties")
  // .excludes(Iterable<String>)
  // .flatten(true)
  // .useRoot(false)
  // .build();

  public static UnArchiverBuilder builder() {
    return new UnArchiverBuilder();
  }

  /**
   * {@EntryProcesor} that leaves the entry name and content as-is.
   */
  class NoopEntryProcessor implements UnarchivingEntryProcessor {

    @Override
    public String processName(String entryName) {
      return entryName;
    }

    @Override
    public void processStream(String entryName, InputStream inputStream, OutputStream outputStream) throws IOException {
      ByteStreams.copy(inputStream, outputStream);
    }
  }

  public static class UnArchiverBuilder {

    private List<String> includes = new ArrayList<String>();
    private List<String> excludes = new ArrayList<String>();
    private boolean useRoot = true;
    private boolean flatten = false;
    private boolean posixLongFileMode;

    public UnArchiverBuilder includes(String... includes) {
      List<String> i = Lists.newArrayList();
      for (String include : includes) {
        if (include != null) {
          i.add(include);
        }
      }
      return includes(ImmutableList.copyOf(i));
    }

    public UnArchiverBuilder includes(Iterable<String> includes) {
      Iterables.addAll(this.includes, includes);
      return this;
    }

    public UnArchiverBuilder excludes(String... excludes) {
      List<String> i = Lists.newArrayList();
      for (String exclude : excludes) {
        if (exclude != null) {
          i.add(exclude);
        }
      }
      return excludes(ImmutableList.copyOf(i));
    }

    public UnArchiverBuilder excludes(Iterable<String> excludes) {
      Iterables.addAll(this.excludes, excludes);
      return this;
    }

    public UnArchiverBuilder useRoot(boolean useRoot) {
      this.useRoot = useRoot;
      return this;
    }

    public UnArchiverBuilder flatten(boolean flatten) {
      this.flatten = flatten;
      return this;
    }

    public UnArchiverBuilder posixLongFileMode(boolean posixLongFileMode) {
      this.posixLongFileMode = posixLongFileMode;
      return this;
    }

    public UnArchiver build() {
      return new UnArchiver(includes, excludes, useRoot, flatten, posixLongFileMode);
    }
  }
}
