package ca.vanzyl.provisio.archive.generator;

import java.io.IOException;

public interface ArtifactGenerator {
  void generate() throws IOException;
}
