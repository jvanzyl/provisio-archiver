# Provisio Archiver 2.0.0 release notes

Provisio Archiver 2.0.0 redesigns archive creation around bounded streaming.
It is a coordinated breaking release intended first for Provisio and its known
downstream consumers. It does not include a compatibility layer for the 1.x
entry, source, handler, builder, or filesystem APIs.

## Streaming architecture

- Archive inputs are traversed through checked, callback-scoped source entries.
  TAR and ZIP content is consumed while its source is valid instead of retaining
  source-backed entries after advancing or closing the input.
- `ArchiveSession` owns the mutable paths, content identity state, temporary
  content, writer, and cleanup for exactly one operation. A configured
  `Archiver` can be reused without leaking operation state.
- `SourceEntry`, `EntryContent`, and immutable `OutputEntry` decisions separate
  source metadata and lifetime from output-format encoding.
- `SourceSpec` gives every source its own destination, root-removal, selection,
  and flattening rules.
- `ArchiveWriter` implementations handle output formats only. Archive reading,
  mapping, normalization, ordering, and hard-link selection are separate policy
  stages.
- `EntryOrder.SOURCE` writes entries before advancing their source and avoids a
  global content spool. `EntryOrder.NAME` remains available when canonical
  name ordering is required and explicitly accepts spooling.

## Correctness and reproducibility

- `ReproducibilityPolicy` separates normalized metadata from entry ordering.
  Normalized timestamps, ownership, modes, gzip headers, and deterministic
  source order produce byte-identical output for stable inputs.
- Parallel gzip uses fixed 1 MiB members and emits completed members in input
  order. Worker count can change throughput without changing archive bytes.
- Hard links are selected by content identity rather than filename. Verified
  SHA-256 identity is the default; explicit size-plus-CRC32 identity supports
  trusted, non-adversarial build inputs without decompressing duplicate ZIP
  entries.
- Content identity is shared across sources, allowing loose files, TAR inputs,
  and ZIP inputs to deduplicate against one another.
- Archive paths and mapped link targets reject absolute paths, root traversal,
  Windows drive and UNC forms, NULs, unsafe links, and canonicalized collisions.
- ZIP content that is read is checked against its declared size and CRC32. TAR
  input validates header checksums and rejects unsupported special entry types.
- Output is transactional: a failed write preserves an existing destination and
  removes the incomplete temporary archive.
- Close, interruption, cancellation, input-corruption, and writer failures are
  propagated without losing the primary failure.

## Performance and resource use

- TAR gzip compression is parallel, bounded, and deterministic.
- Compressed members retain their backing buffers and valid lengths, avoiding
  buffer-growth and final-trimming copies.
- The 1 MiB member size reduces peak memory and improves pipeline latency for
  the Trino distribution workload.
- Destination prefixes are parsed once per source and parent discovery walks
  directly to an existing ancestor, reducing archive-path bookkeeping.
- Source-order streaming does not materialize an exploded directory or retain
  an archive-sized sorting spool.

In the same-base Trino server packaging benchmark, these changes contributed to
reducing the streaming Provisio median from 10.76 seconds to 8.24 seconds in the
like-for-like JFR comparison. Maximum resident memory fell from about 2.98 GB to
868 MB, and `Arrays.copyOf(byte[], int)` allocation pressure fell from 40.83%
to 4.07%.

## API and format changes

- The primary filesystem API uses `Path`.
- `Sources` is the public source-construction facade.
- Format implementations and general permission internals are no longer part
  of the supported public surface.
- Obsolete `File` overloads, mutable entry types, handler abstractions,
  implementation-facing constructors, and artifact generators were removed.
- XZ input and output support was removed, including its fixture and tests.
- The historical `TarGzXzArchiveHandler` and `TarGzXzArchiveSource` names were
  removed. Gzip TAR input is exposed as `TarGzArchiveSource`; output is handled
  by `TarGzArchiveWriter`.
- Extraction uses the single `UnarchivingEntryProcessor` callback contract.

The supported version 2 API and migration boundary are documented in
[API](api.md) and [Streaming archive architecture](streaming-architecture.md).
Known consumers that may require coordinated updates are recorded in
[Downstream consumers](downstream-consumers.md).

## Verification at the release candidate

- 143 Archiver tests pass.
- Generated combinations cover TAR.GZ and ZIP output, directory, loose-file,
  ZIP/JAR, and TAR.GZ inputs, mapping, selection, flattening, hard links, and
  both ordering policies.
- Negative coverage includes malformed paths and links, mapped collisions,
  corrupt TAR and ZIP input, transactional failure, lifecycle misuse, gzip
  worker/output failure, interruption, cancellation, and bounded scale tests.
- The Trino-shaped benchmark output validates as complete TAR and gzip streams.

## Release coordination

Release `io.takari:takari-archiver:2.0.0` before releasing the Provisio version
that consumes it. Provisio, `takari-lifecycle`, Maveniverse Toolrunner, and any
other confirmed consumers must move to the version 2 API rather than relying on
a 1.x compatibility layer.
