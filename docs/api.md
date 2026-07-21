# Provisio Archiver API

Provisio Archiver 2 is a streaming archive assembly library. The supported API
is the single exported package `ca.vanzyl.provisio.archive`; packages below it
contain replaceable implementation details.

The API uses `java.nio.file.Path` for filesystem inputs and outputs. Archive
format is selected from the output name for creation and either explicitly or
from the input name through `Sources`.

## Create an archive

The directory overload preserves the source directory name as the archive root:

```java
Path application = Paths.get("target/application");
Path output = Paths.get("target/application.tar.gz");

Archiver.builder()
        .reproducibility(ReproducibilityPolicy.NORMALIZED)
        .entryOrder(EntryOrder.SOURCE)
        .gzipCompressionThreads(4)
        .build()
        .archive(output, application);
```

Use `Sources` and `SourceSpec` when a source needs selection or mapping:

```java
SourceSpec classes = SourceSpec.builder(Sources.directory(Paths.get("target/classes")))
        .useRoot(false)
        .destinationPrefix("lib/")
        .includes("**/*.class")
        .build();

Archiver.builder()
        .reproducibility(ReproducibilityPolicy.NORMALIZED)
        .entryOrder(EntryOrder.NAME)
        .build()
        .archive(Paths.get("target/classes.zip"), classes);
```

`SOURCE` writes content before advancing the source and does not spool ordinary
entry content. `NAME` spools callback-scoped content and emits canonical
name-sorted output. Reproducibility and ordering are independent.

## Stream one archive into another

Built-in archive sources do not expand their input into an intermediate
directory:

```java
SourceSpec plugins = SourceSpec.builder(Sources.zip(Paths.get("target/plugins.zip")))
        .useRoot(false)
        .destinationPrefix("plugin/")
        .build();

Archiver.builder()
        .entryOrder(EntryOrder.SOURCE)
        .contentIdentity(ContentIdentityMode.SIZE_AND_CRC32)
        .hardLinkIncludes("plugin/**/*.jar")
        .gzipCompressionThreads(4)
        .build()
        .archive(Paths.get("target/distribution.tar.gz"), plugins);
```

Use `Sources.zip(path)` or `Sources.tarGz(path)` when the format is known.
`Sources.archive(path)` detects ZIP-compatible names (`.zip`, `.jar`,
`.war`, `.hpi`, and `.jpi`) and gzip-compressed tar names (`.tgz` and
`.tar.gz`).

`ContentIdentityMode.VERIFIED` is the default and confirms duplicate content
with SHA-256. `SIZE_AND_CRC32` is an explicit trust and performance tradeoff
for sources such as ZIP central directories. Entries that do not report CRC32
remain eligible for links to and from entries that do: their CRC32 is computed
as part of the read already required to verify or write them.

## Supply a custom streaming source

A custom source visits entries in source order. Entry content is valid only
inside its callback:

```java
Source generated = new Source() {
    @Override
    public void forEachEntry(EntryConsumer consumer) throws IOException {
        consumer.accept(SourceEntry.file(
                "version.txt",
                EntryContents.of("2.0".getBytes(StandardCharsets.UTF_8)),
                0644,
                0));
    }

    @Override
    public boolean isDirectory() {
        return false;
    }

    @Override
    public void close() {
    }
};

Archiver.builder().build().archive(Paths.get("target/generated.zip"), generated);
```

`Archiver` owns and closes each supplied `Source`, including when traversal or
writing fails. A source backed by an open archive may invalidate an entry and
its content as soon as the callback returns. A consumer that needs content later
must copy or spool it inside the callback.

## Extract an archive

```java
UnArchiver.builder()
        .useRoot(false)
        .includes("bin/**", "lib/**")
        .build()
        .unarchive(
                Paths.get("target/application.tar.gz"),
                Paths.get("target/application"));
```

Use `UnarchivingEntryProcessor` to rename entries, transform regular-file
content, map hard-link sources consistently, or observe completed outputs.
Path validation runs again after processor mapping and before filesystem output
is created.

Extraction rejects absolute paths, traversal, escaping symbolic or hard links,
canonical path collisions, corrupt ZIP content, corrupt gzip trailers, invalid
tar headers, and unsupported tar entry types.

## Output and resource guarantees

- Output is written transactionally to a temporary sibling and moved into place
  only after the complete archive and compression trailer succeed.
- `SOURCE` ordering keeps source and output streams sequential and avoids an
  entry-content spool.
- `NAME` ordering uses closed temporary spool files and removes them on success
  and failure.
- Parallel gzip uses deterministic ordered 8 MiB members. Pending work is
  bounded to twice the configured worker count.
- ZIP entry size and CRC are checked when content is read to end of stream.
- A configured `Archiver` is reusable and may run concurrent independent
  archive operations.

## Migration from 1.x

This is a coordinated breaking release with no compatibility adapters.

| 1.x API | 2.x API |
| --- | --- |
| `File` archive and extraction arguments | `Path` |
| `new DirectorySource(path)` | `Sources.directory(path)` |
| `new FileSource(path)` | `Sources.file(path)` |
| `new TarGzArchiveSource(path)` | `Sources.tarGz(path)` |
| `new ZipArchiveSource(path)` | `Sources.zip(path)` |
| `UnarchivingEnhancedEntryProcessor` | `UnarchivingEntryProcessor` |
| global source mapping on `ArchiverBuilder` | one immutable `SourceSpec` per source |
| normalization-implied ordering | explicit `ReproducibilityPolicy` and `EntryOrder` |
| filename-based tar hard links | explicit verified or size-and-CRC content identity |
| XZ and `TarGzXz*` types | removed |
| artifact-generator and permission utility packages | removed from the library API |

The former artifact generators created synthetic test fixtures rather than
performing archive operations. Projects that used them should own equivalent
test data builders locally. The old handler hierarchy, mutable archive entries,
legacy extraction processor, implementation constructors, and deprecated
`File` overloads are removed rather than adapted.

No compatibility layer remains for a later refactor to delete. Future internal
refactoring can replace format readers, writers, spooling, deduplication, or
compression without changing the exported source and archiver contracts.
