package io.tesla.proviso.archive;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.TreeMap;

import org.apache.commons.compress.archivers.ArchiveOutputStream;
import org.codehaus.plexus.util.SelectorUtils;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

import io.tesla.proviso.archive.source.DirectoryEntry;
import io.tesla.proviso.archive.source.DirectorySource;

public class Archiver {

  public static final long DOS_EPOCH_IN_JAVA_TIME = 315561600000L;
  // ZIP timestamps have a resolution of 2 seconds.
  // see http://www.info-zip.org/FAQ.html#limits
  public static final long MINIMUM_TIMESTAMP_INCREMENT = 2000L;
  private final Map<String, ExtendedArchiveEntry> entries = new TreeMap<>();

  private final List<String> executables;
  private final boolean useRoot;
  private final boolean flatten;
  private final boolean normalize;
  private final String prefix;
  private final boolean posixLongFileMode;
  private final Selector selector;

  private Archiver(List<String> includes,
                   List<String> excludes,
                   List<String> executables,
                   boolean useRoot,
                   boolean flatten,
                   boolean normalize,
                   String prefix,
                   boolean posixLongFileMode) {
    this.executables = executables;
    this.useRoot = useRoot;
    this.flatten = flatten;
    this.normalize = normalize;
    this.prefix = prefix;
    this.posixLongFileMode = posixLongFileMode;
    this.selector = new Selector(includes, excludes);
  }

  public void archive(File archive, List<String> sourceDirectories) throws IOException {
    File[] fileSourceDirectories = new File[sourceDirectories.size()];
    for (int i = 0; i < sourceDirectories.size(); i++) {
      fileSourceDirectories[i] = new File(sourceDirectories.get(i));
    }
    archive(archive, fileSourceDirectories);
  }

  public void archive(File archive, File... sourceDirectories) throws IOException {
    archive(archive, new DirectorySource(sourceDirectories));
  }

  public void archive(File archive, Source... sources) throws IOException {
    ArchiveHandler archiveHandler = ArchiverHelper.getArchiveHandler(archive, posixLongFileMode);
    try (ArchiveOutputStream aos = archiveHandler.getOutputStream()) {
      //
      // collected archive entry paths mapped to true for explicitly provided entries
      // and to false for implicitly created directory entries duplicate explicitly
      // provided entries result in IllegalArgumentException
      //
      Map<String, Boolean> paths = new HashMap<>();
      for (Source source : sources) {
        for (Entry entry : source.entries()) {
          String entryName = entry.getName();
          if (!selector.include(entryName)) {
            continue;
          }
          if (!useRoot && source.isDirectory()) {
            entryName = entryName.substring(entryName.indexOf('/') + 1);
          }
          if (flatten && source.isDirectory()) {
            if (entry.isDirectory()) {
              continue;
            }
            entryName = entryName.substring(entryName.lastIndexOf('/') + 1);
          }
          if (prefix != null) {
            entryName = prefix + entryName;
          }
          boolean isExecutable = false;
          for (String executable : executables) {
            if (SelectorUtils.match(executable, entry.getName())) {
              isExecutable = true;
              break;
            }
          }
          // If we have a directory entry then make sure we append a trailing "/"
          if (entry.isDirectory() && !entryName.endsWith("/")) {
            entryName += "/";
          }
          // Create any missing intermediate directory entries
          for (String directoryName : getParentDirectoryNames(entryName)) {
            if (!paths.containsKey(directoryName)) {
              paths.put(directoryName, Boolean.FALSE);
              ExtendedArchiveEntry directoryEntry = archiveHandler.createEntryFor(directoryName, new DirectoryEntry(directoryName), false);
              addEntry(directoryName, directoryEntry, aos);
            }
          }
          if (!paths.containsKey(entryName)) {
            paths.put(entryName, Boolean.TRUE);
            ExtendedArchiveEntry archiveEntry = archiveHandler.createEntryFor(entryName, entry, isExecutable);
            addEntry(entryName, archiveEntry, aos);
          } else {
            if (Boolean.TRUE.equals(paths.get(entryName))) {
              throw new IllegalArgumentException("Duplicate archive entry " + entryName);
            }
          }
        }
        source.close();
      }

      if (!entries.isEmpty()) {
        for (Map.Entry<String, ExtendedArchiveEntry> entry : entries.entrySet()) {
          ExtendedArchiveEntry archiveEntry = entry.getValue();
          writeEntry(archiveEntry, aos);
        }
      }
    }
  }

  private Iterable<String> getParentDirectoryNames(String entryName) {
    List<String> directoryNames = new ArrayList<>();
    StringTokenizer st = new StringTokenizer(entryName, "/");
    if (st.hasMoreTokens()) {
      StringBuilder directoryName = new StringBuilder(st.nextToken());
      while (st.hasMoreTokens()) {
        directoryName.append('/');
        directoryNames.add(directoryName.toString());
        directoryName.append(st.nextToken());
      }
    }
    return directoryNames;
  }

  /**
   * Returns the normalized timestamp for a jar entry based on its name. This is necessary since javac will, when loading a class X, prefer a source file to a class file, if both files have the same
   * timestamp. Therefore, we need to adjust the timestamp for class files to slightly after the normalized time.
   * 
   * @param name The name of the file for which we should return the normalized timestamp.
   * @return the time for a new Jar file entry in milliseconds since the epoch.
   */
  private long normalizedTimestamp(String name) {
    if (name.endsWith(".class")) {
      return DOS_EPOCH_IN_JAVA_TIME + MINIMUM_TIMESTAMP_INCREMENT;
    } else {
      return DOS_EPOCH_IN_JAVA_TIME;
    }
  }

  /**
   * Returns the time for a new Jar file entry in milliseconds since the epoch. Uses {@link JarCreator#DOS_EPOCH_IN_JAVA_TIME} for normalized entries, {@link System#currentTimeMillis()} otherwise.
   *
   * @param filename The name of the file for which we are entering the time
   * @return the time for a new Jar file entry in milliseconds since the epoch.
   */
  private long newEntryTimeMillis(String filename) {
    return normalize ? normalizedTimestamp(filename) : System.currentTimeMillis();
  }

  /**
   * Adds an entry to the Jar file, normalizing the name.
   *
   * @param entryName the name of the entry in the Jar file
   * @param fileName the name of the input file for the entry
   */
  private void addEntry(String entryName, ExtendedArchiveEntry entry, ArchiveOutputStream aos) throws IOException {
    if (entryName.startsWith("/")) {
      entryName = entryName.substring(1);
    } else if (entryName.startsWith("./")) {
      entryName = entryName.substring(2);
    }
    if (normalize) {
      entry.setTime(newEntryTimeMillis(entryName));
      entries.put(entryName, entry);
    } else {
      writeEntry(entry, aos);
    }
  }

  private void writeEntry(ExtendedArchiveEntry entry, ArchiveOutputStream aos) throws IOException {
    aos.putArchiveEntry(entry);
    if (!entry.isDirectory()) {
      entry.writeEntry(aos);
    }
    aos.closeArchiveEntry();
  }


  public static ArchiverBuilder builder() {
    return new ArchiverBuilder();
  }

  public static class ArchiverBuilder {
    private List<String> includes = Lists.newArrayList();
    private List<String> excludes = Lists.newArrayList();
    private List<String> executables = Lists.newArrayList();
    private boolean useRoot = true;
    private boolean flatten = false;
    private boolean normalize = false;
    private String prefix;
    private boolean posixLongFileMode;

    public ArchiverBuilder includes(String... includes) {
      return includes(ImmutableList.copyOf(includes));
    }

    public ArchiverBuilder includes(Iterable<String> includes) {
      Iterables.addAll(this.includes, includes);
      return this;
    }

    public ArchiverBuilder excludes(String... excludes) {
      return excludes(ImmutableList.copyOf(excludes));
    }

    public ArchiverBuilder excludes(Iterable<String> excludes) {
      Iterables.addAll(this.excludes, excludes);
      return this;
    }

    public ArchiverBuilder useRoot(boolean useRoot) {
      this.useRoot = useRoot;
      return this;
    }

    /**
     * Enables or disables the Jar entry normalization.
     *
     * @param normalize If true the timestamps of Jar entries will be set to the DOS epoch.
     */
    public ArchiverBuilder normalize(boolean normalize) {
      this.normalize = normalize;
      return this;
    }

    public ArchiverBuilder executable(String... executables) {
      return executable(ImmutableList.copyOf(executables));
    }

    public ArchiverBuilder executable(Iterable<String> executables) {
      Iterables.addAll(this.executables, executables);
      return this;
    }

    public ArchiverBuilder flatten(boolean flatten) {
      this.flatten = flatten;
      return this;
    }

    public ArchiverBuilder withPrefix(String prefix) {
      this.prefix = prefix;
      return this;
    }

    public ArchiverBuilder posixLongFileMode(boolean posixLongFileMode) {
      this.posixLongFileMode = posixLongFileMode;
      return this;
    }

    public Archiver build() {
      return new Archiver(includes, excludes, executables, useRoot, flatten, normalize, prefix, posixLongFileMode);
    }
  }
}
