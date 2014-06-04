package io.tesla.proviso.archive;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public interface UnarchivingEntryProcessor {
  String processName(String name);
  void processStream(InputStream inputStream, OutputStream outputStream) throws IOException;
}
