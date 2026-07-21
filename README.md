# Provisio Archiver

[![Maven Central](https://img.shields.io/maven-central/v/io.takari/takari-archiver.svg?label=Maven%20Central)](https://search.maven.org/artifact/io.takari/takari-archiver)
[![Verify](https://github.com/jvanzyl/provisio-archiver/actions/workflows/ci.yml/badge.svg)](https://github.com/jvanzyl/provisio-archiver/actions/workflows/ci.yml)
[![Reproducible Builds](https://img.shields.io/endpoint?url=https://raw.githubusercontent.com/jvm-repo-rebuild/reproducible-central/master/content/io/takari/takari-archiver/badge.json)](https://github.com/jvm-repo-rebuild/reproducible-central/blob/master/content/io/takari/takari-archiver/README.md)

Provisio Archiver creates ZIP and gzip-compressed tar archives from filesystem,
generated, and archive-backed sources. Its streaming pipeline supports
transactional output, reproducible metadata, explicit ordering, verified tar
hard links, bounded parallel gzip compression, and safe extraction.

- [API and 1.x migration guide](docs/api.md)
- [Streaming architecture](docs/streaming-architecture.md)
- [Benchmark methodology](docs/benchmarks.md)
- [Downstream consumer audit](docs/downstream-consumers.md)
