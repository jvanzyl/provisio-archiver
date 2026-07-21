# Archive benchmarks

The opt-in `TrinoArchiveBenchmark` exercises the workload that motivated the
streaming redesign: a ZIP containing thousands of JAR-like entries, a high
duplicate ratio, metadata-only duplicate recognition, tar hard links, and
parallel gzip output.

Run it explicitly so ordinary unit-test execution remains fast:

```shell
./mvnw -Dtest=TrinoArchiveBenchmark test
```

The default workload contains 20,000 entries backed by 256 unique payloads.
Both values can be changed:

```shell
./mvnw \
  -Dtest=TrinoArchiveBenchmark \
  -Dprovisio.benchmark.entries=50000 \
  -Dprovisio.benchmark.unique-payloads=512 \
  test
```

The benchmark reports assembly time, entries per second, and input and output
sizes. It deliberately has no timing threshold: comparisons should use the same
machine, JDK, filesystem, worker settings, and workload, with warm-up runs
discarded. Structural assertions still require only one ZIP content read per
unique size/CRC identity, one regular tar file per identity, hard links for all
duplicates, no expanded intermediate directory tree, and no leaked temporary
files.

`TrinoArchiveShapeTest` runs a smaller version in the normal suite. It verifies
those structural properties without treating wall-clock time as correctness.
