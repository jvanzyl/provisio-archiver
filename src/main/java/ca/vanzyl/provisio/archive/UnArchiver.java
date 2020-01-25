package ca.vanzyl.provisio.archive;

import ca.vanzyl.provisio.archive.perms.FileMode;
import ca.vanzyl.provisio.archive.perms.PosixModes;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.io.ByteStreams;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.inject.Named;
import javax.inject.Singleton;

@Named
@Singleton
public class UnArchiver {

  private final Selector selector;
  private final boolean useRoot;
  private final boolean flatten;
  private final boolean dereferenceHardlinks;
  private final UnArchiverBuilder builder;

  public UnArchiver(UnArchiverBuilder builder) {
    this.builder = builder;
    this.useRoot = builder.useRoot;
    this.flatten = builder.flatten;
    this.dereferenceHardlinks = builder.dereferenceHardlinks;
    this.selector = new Selector(builder.includes, builder.excludes);
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
    Source source = ArchiverHelper.getArchiveHandler(archive, builder).getArchiveSource();
    for (ExtendedArchiveEntry archiveEntry : source.entries()) {
      String entryName = adjustPath(archiveEntry.getName(), entryProcessor);

      if (!selector.include(entryName)) {
        continue;
      }

      if (archiveEntry.isDirectory()) {
        createDir(new File(outputDirectory, entryName));
        continue;
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

      if (archiveEntry.isHardLink()) {
        File hardLinkSource = new File(outputDirectory, adjustPath(archiveEntry.getHardLinkPath(), entryProcessor));
        if(dereferenceHardlinks) {
          Files.copy(hardLinkSource.toPath(), outputFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } else {
          // Remove any existing file or link as Files.createLink has no option to overwrite
          Files.deleteIfExists(outputFile.toPath());
          Files.createLink(outputFile.toPath(), hardLinkSource.toPath());
        }
      } else {
        try (OutputStream outputStream = new BufferedOutputStream(new FileOutputStream(outputFile))) {
          entryProcessor.processStream(archiveEntry.getName(), archiveEntry.getInputStream(), outputStream);
        }
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

  private String adjustPath(String entryName, UnarchivingEntryProcessor entryProcessor) {
    if (useRoot == false) {
      entryName = entryName.substring(entryName.indexOf('/') + 1);
    }
    // Process the entry name before any output is created on disk
    entryName = entryProcessor.processName(entryName);
    // So with an entry we may want to take a set of entry in a set of directories and flatten them
    // into one directory, or we may want to preserve the directory structure.
    if (flatten) {
      entryName = entryName.substring(entryName.lastIndexOf("/") + 1);
    }
    return entryName;
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

    List<String> includes = new ArrayList<String>();
    List<String> excludes = new ArrayList<String>();
    boolean useRoot = true;
    boolean flatten = false;
    boolean posixLongFileMode;
    boolean dereferenceHardlinks = false;

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

    public UnArchiverBuilder dereferenceHardlinks(boolean dereferenceHardlinks) {
      this.dereferenceHardlinks = dereferenceHardlinks;
      return this;
    }

    public UnArchiver build() {
      return new UnArchiver(this);
    }
  }
}
