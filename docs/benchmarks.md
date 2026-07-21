# Archive benchmarks

The opt-in `TrinoArchiveBenchmark` exercises the workload that motivated the
streaming redesign: a ZIP containing thousands of JAR-like entries, a high
duplicate ratio, tar hard links, and parallel gzip output. It runs both
`SIZE_AND_CRC32` and `VERIFIED` identity against equivalent inputs.

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

The benchmark reports assembly time, entries per second, content-open count,
content bytes read, and input and output sizes for each identity mode. It also
reports the verified-to-CRC time and source-read ratios. It deliberately has no
timing threshold: comparisons should use the same machine, JDK, filesystem,
worker settings, and workload, with warm-up runs discarded.

Structural assertions require CRC identity to open content only once per unique
size/CRC identity, while verified identity opens every entry to calculate its
SHA-256 digest. Both modes must still produce one regular tar file per unique
identity, hard links for all duplicates, no expanded intermediate directory
tree, and no leaked temporary files.

`TrinoArchiveShapeTest` runs a smaller version in the normal suite. It verifies
those structural properties without treating wall-clock time as correctness.
