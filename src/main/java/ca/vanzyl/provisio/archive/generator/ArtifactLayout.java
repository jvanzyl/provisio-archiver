package ca.vanzyl.provisio.archive.generator;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

public class ArtifactLayout {

  private final File tarGzDirectory;
  private final List<ArtifactEntry> entries;

  public ArtifactLayout(File tarGzDirectory) {
    this.tarGzDirectory = tarGzDirectory;
    this.entries = new ArrayList<>();
  }

  public ArtifactLayout(File tarGzDirectory, List<ArtifactEntry> entries) {
    this.tarGzDirectory = tarGzDirectory;
    this.entries = entries;
  }

  public ArtifactLayout entry(String name, File file) {
    entries.add(new ArtifactEntry(name, file));
    return this;
  }

  public ArtifactLayout entry(String name, String content) {
    entries.add(new ArtifactEntry(name, content));
    return this;
  }

  public void build() throws IOException {
    for (ArtifactEntry entry : entries) {
      File file = new File(tarGzDirectory, entry.name());
      if (entry.file() != null) {
        if (!file.getParentFile().exists()) {
          file.getParentFile().mkdirs();
        }
        Files.copy(entry.file().toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
      } else if (entry.content() != null) {
        if (!file.getParentFile().exists()) {
          file.getParentFile().mkdirs();
        }
        Files.copy(new ByteArrayInputStream(entry.content().getBytes()), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
      }
    }
  }

  public File directory() {
    return tarGzDirectory;
  }
}
