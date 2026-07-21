# Streaming archive architecture

## Context

Provisio Archiver has been redesigned to assemble normalized archives directly from
other archives. The immediate motivation is the streaming packaging approach in
[Trino PR 30400](https://github.com/trinodb/trino/pull/30400): avoid expanding
source archives to disk, identify duplicate ZIP content from central-directory
metadata, and compress the output efficiently without sacrificing
reproducibility.

The original implementation wrote entries immediately when normalization was
disabled. Normalized output retained entries in a name-sorted map, closed each
source, and wrote the retained entries afterward. Entries backed by a sequential
tar stream or an open `ZipFile` could not be used safely after their source had
advanced or closed.

Several original abstractions combined responsibilities:

* `ExtendedArchiveEntry` represented source metadata, source content, and a
  mutable output entry.
* `ArchiveHandler` combined format detection, reading, output creation, entry
  conversion, and archive policy.
* `TarGzArchiveHandler` decided hard-link identity using only the
  source filename.
* Normalization controlled both metadata normalization and entry ordering.
* Mutable entries retained by `Archiver` belonged to the `Archiver` instance
  rather than to one archive operation.

Those boundaries made direct streaming harder to reason about and made resource
lifetime and error propagation fragile.

## Design direction

The intended internal pipeline is:

```text
Source
  -> source entry and content
  -> per-source mapping and archive-path validation
  -> archive session
       - duplicate-path detection
       - implicit directories
       - metadata normalization
       - entry ordering
       - content identity and hard-link selection
  -> format-specific writer
```

An archive session owns all mutable state and temporary content for exactly one
output operation. A configured `Archiver` must not retain entries, paths,
hard-link candidates, or source-backed content between invocations.

`ArchiveSession` now owns the writer, content spool, mapped-path index, delayed
entries, and transitional hard-link candidates for one operation. `Archiver`
captures an immutable configuration snapshot when it is built, so later builder
mutation and concurrent archive operations cannot share or alter session state.

### Source traversal and lifetime

Sources need a checked, lifetime-aware traversal operation. Archive-backed
sources will consume an entry within a callback before advancing to the next
entry. This permits tar read failures to propagate as `IOException` and makes it
impossible for the normal streaming path to retain an entry beyond the lifetime
of its source stream.

The source contract now uses checked callback traversal directly. The iterable
`Source.entries()` contract was removed. Tar and ZIP sources implement the same
lifetime rule: an entry is consumed within its callback before the source
advances. Tar content is single-use; ZIP content is repeatable while its callback
is active. Both reject access after the callback returns.

Every source and writer must be closed with try-with-resources. When processing
and closing both fail, the processing failure remains primary and the close
failure is suppressed.

Archive-backed sources validate the content they actually consume. ZIP entry
streams check their uncompressed size and central-directory CRC at end of input.
Tar sources reject invalid header checksums and special entry types rather than
treating every non-directory entry as a regular file. Metadata-only ZIP
deduplication remains an explicit trust mode: duplicate content deliberately not
opened in that mode cannot be independently validated.

### Ordering is separate from normalization

Normalization describes reproducible metadata. Ordering describes when entries
can be emitted. They are independent policies.

The entry-order choices are:

* `NAME`: retain current normalized, name-sorted behavior. Source content is
  safely spooled while its source is open and sorted only after it is no longer
  source-bound.
* `SOURCE`: preserve deterministic source order and write each entry before
  advancing its source. This is the direct streaming path.

The `EntryOrder` API now makes ordering explicit instead of deriving it from
normalization. `SOURCE` is the default direct-streaming path. `NAME` is available
when a caller requires canonical sorting and accepts callback-time spooling.
Metadata normalization has no effect on entry order.

`SOURCE` retains no entry-content spool files. `NAME` uses one closed temporary
file per retained file entry, so its total temporary disk use is proportional to
the input while live source and spool stream handles remain sequential. Every
spool is removed when the archive session closes, on both success and failure.

### Entries and content

`SourceEntry` now separates immutable source metadata from mutable output-format
entries. `EntryContent` carries the operations and facts needed by assembly,
including:

* content length;
* an optional CRC or other content identity known without reading;
* whether content is repeatable or single-use;
* copying or opening content;
* temporary spooling when sorting, filtering, or tar identity calculation
  requires it.

ZIP sources expose size and CRC from their central directory without opening
compressed content. Sequential tar content is consumed immediately in source
order. The name-sorted path safely spools callback-scoped content inside the
archive session, while source order writes before the callback returns.

### Per-source mapping and safe paths

Mapping now belongs to each source rather than to the global `Archiver`
configuration. An immutable `SourceSpec` carries its source, destination prefix,
`useRoot`, includes, excludes, and flattening. The global mapping methods were
removed from `ArchiverBuilder`, so one operation can combine independently
mapped sources without hidden policy leaking between them.

All source and mapped entry names and link targets now pass through the shared
`ArchivePath` representation. It rejects absolute paths, `.` and `..` traversal,
Windows drive and UNC paths, NUL characters, and collisions produced by
canonicalization or mapping. Repeated separators and `\` are canonicalized to
`/` independently of the host platform.

Hard-link targets are archive-root paths and receive the same root-removal and
prefix mapping as their entries. Symbolic-link targets remain relative: parent
segments are accepted only when resolving the target against the link location
stays inside the archive root. Unarchiving applies the same validation after an
entry processor changes a path and before creating anything at that path.

### Format writers and hard links

Format-specific writers now encode immutable `OutputEntry` decisions and do not
read archives or decide assembly policy. The combined `ArchiveHandler`,
`ArchiveHandlerSupport`, and `ExtendedArchiveEntry` hierarchy was removed.
Hard-link eligibility and content identity belong to assembly. A tar writer
receives either a regular output entry or an already selected hard-link entry;
the ZIP writer rejects hard links because ZIP has no representation for them.

Filename-based hard-link selection has been removed. Eligible regular files are
fingerprinted with SHA-256, and only matching size and digest produce a hard
link. Source-ordered output hashes first occurrences as they are written and
spools only single-use entries that have a possible prior size match. Name-order
output performs identity decisions in final output order, ensuring every target
is written before its links.

Size and CRC remain useful candidate metadata but are not exact identity.
`ContentIdentityMode.VERIFIED` is therefore the default. The explicit
`SIZE_AND_CRC32` mode trusts source-reported metadata and avoids opening matching
duplicate content. In name order, one representative is spooled for each
metadata identity and supplies the content for the first sorted target. Missing
or invalid metadata falls back to verified identity instead of producing an
unsafe link.

### Output integrity and reproducibility

Output is written to a temporary sibling and moved into place only after all
entries and compression trailers have been written successfully. A failure must
not leave a partial archive at the requested destination.

`ReproducibilityPolicy` now separates preserved source metadata from normalized
output. `NORMALIZED` fixes timestamps (including the historical `.class`
adjustment), canonicalizes modes while retaining executable semantics, clears
tar user and group ownership, and fixes gzip header fields. `PRESERVE` carries
source timestamps and modes. Entry ordering remains an independent policy.
Tar output now uses fixed 8 MiB gzip members compressed by a configurable worker
pool. Completed members are emitted in submission order, so output is identical
across worker counts. At most twice the worker count is pending, bounding source
and compressed-result memory. Empty streams, worker and output failures,
interruption, cancellation, concatenated-member reads, and deterministic output
are covered directly.

## Compatibility decision

This is a coordinated breaking release. Provisio Archiver does not retain
source or binary compatibility with the existing entry, source, handler, or
builder APIs. Avoiding a transitional compatibility layer keeps the new pipeline
as the only implementation and prevents old lifetime and policy assumptions from
leaking into it.

The clean break permits this work to:

* replace `ExtendedArchiveEntry` with separate source and output entry types;
* replace iterable source traversal with checked callback traversal;
* replace `ArchiveHandler` with format readers and writers;
* make per-source mapping part of the source specification;
* use `Path` and the root-package `Sources` facade as the primary API;
* expose ordering and reproducibility as independent policies;
* stop exporting format implementation packages;
* remove obsolete `File` overloads and implementation-facing constructors;
* remove synthetic artifact generators and general permission utilities from
  the published API;
* rename the sole extraction callback to `UnarchivingEntryProcessor`.

The release version must communicate the API break. No API-compatibility
exclusions or deprecated adapters are required. XZ support and the old
`TarGzXzArchiveHandler` and `TarGzXzArchiveSource` names remain removed as part
of the same clean boundary.

Behavior is preserved where it is a product requirement rather than an API
accident. Tests continue to cover reproducible output, normalized metadata,
permissions, safe paths, duplicate rejection, filtering, mapping, and correct
link behavior. Ordering and performance behavior are specified by the new API
rather than inherited implicitly from the old builder.

## Coordinated consumer migration

The known current consumers to coordinate with this repository are:

* [Provisio](https://github.com/jvanzyl/provisio), which uses the archiver for
  runtime assembly, unpacking, and archive production;
* [Takari Lifecycle](https://github.com/takari/takari-lifecycle), whose JAR
  implementation supplies custom sources and entries and invokes `Archiver`.
* [Maveniverse Toolrunner](https://github.com/maveniverse/toolrunner), whose
  shared module uses `UnArchiver` in production code.

Provisio Tools is pinned to an older archiver and does not block the coordinated
upgrade above. Before it adopts this release, it must replace its use of the
removed synthetic artifact-generator package with project-local test/data
builders.

Development proceeds against a locally installed archiver build. Each consumer
is updated on its own branch after the new archiver contract is stable. Consumer
tests must pass before the breaking archiver release is considered usable.
Publishing and pull requests remain separate, explicitly authorized operations.
The evidence and limits of the downstream search are recorded in
[downstream-consumers.md](downstream-consumers.md).
Supported API examples and the complete 1.x migration table are in
[api.md](api.md).

## Testing expectations

Each architectural step is accompanied by positive, negative, lifecycle, and
consumer-contract tests. Coverage includes source reuse, close and suppressed-error
behavior, corrupt and truncated inputs, duplicate and unsafe paths, mapping
collisions, link targets, content-identity false positives, metadata-only ZIP
deduplication, deterministic compression, bounded buffering, and semantic
equivalence with expected archive contents and metadata.

Performance measurements are benchmarks rather than timing assertions in the
unit suite. Tests should instead prove structural properties such as avoiding an
expanded intermediate tree and avoiding reads of ZIP content already identified
as duplicate. The opt-in workload and comparison guidance are described in
[benchmarks.md](benchmarks.md).

## Future refactoring

The clean break happens in this work rather than being deferred. No legacy
adapter layer remains for a future refactor to remove. Once the coordinated
consumers have migrated, future refactoring can operate on the single
source-entry, archive-session, and format-writer model.

Public API should remain intentionally small even without a compatibility
promise. Format implementations, spooling, deduplication indexes, compression
workers, and archive-session state stay internal so they can be replaced without
another coordinated consumer migration.
