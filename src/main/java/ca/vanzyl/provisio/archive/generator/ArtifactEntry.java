package ca.vanzyl.provisio.archive.generator;

import java.io.File;

public class ArtifactEntry {

  String name;
  File file;
  String content;
  boolean executable;

  public ArtifactEntry(String name, File file) {
    this.name = name;
    this.file = file;
  }

  public ArtifactEntry(String name, String content) {
    this(name, content, false);
  }

  public ArtifactEntry(String name, String content, boolean executable) {
    this.name = name;
    this.content = content;
    this.executable = executable;
  }

  public String name() {
    return name;
  }

  public File file() {
    return file;
  }

  public String content() {
    return content;
  }
}
