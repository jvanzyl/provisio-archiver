package io.tesla.proviso.archive;

import io.tesla.proviso.archive.perms.FileMode;

public abstract class ArchiveHandlerSupport implements ArchiveHandler {

  @Override
  public ExtendedArchiveEntry createEntryFor(String entryName, Entry archiveEntry, boolean isExecutable) {
    ExtendedArchiveEntry entry = newEntry(entryName, archiveEntry);
    // TODO: causing problems with hardlinks don't set this as hardlinks are zero
    //entry.setSize(archiveEntry.getSize());
    //
    // If we have a valid file mode then use it for the entry we are creating
    if (archiveEntry.getFileMode() != -1) {
      entry.setFileMode(archiveEntry.getFileMode());
      if (isExecutable) {
        entry.setFileMode(FileMode.makeExecutable(entry.getFileMode()));
      }
    } else {
      if (isExecutable) {
        entry.setFileMode(FileMode.EXECUTABLE_FILE.getBits());
      }
    }
    return entry;
  }
}
