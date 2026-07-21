# Downstream consumer audit

This audit records downstream consumers that need consideration before a
breaking Provisio Archiver release. It was performed on 2026-07-21 using
deps.dev, GitHub source search, and local Takari checkouts.

## Coordinated consumers

These projects contain current production-code use of the archiver and should be
updated or explicitly assessed with the breaking release.

### Provisio

[jvanzyl/provisio](https://github.com/jvanzyl/provisio) manages
`io.takari:takari-archiver:1.0.9`. Production dependencies occur in
`provisio-core`, `provisio-maven`, and `provisio-maven-plugin`, with additional
integration-test use. Provisio uses both archive creation and extraction APIs.
Its migration includes `Path` arguments, the renamed
`UnarchivingEntryProcessor`, and the root `Sources` facade where tests read
archives.

### Takari Lifecycle

[takari/takari-lifecycle](https://github.com/takari/takari-lifecycle) uses
archiver types directly in its JAR implementation:

* [`Jar.java`](https://github.com/takari/takari-lifecycle/blob/master/takari-lifecycle-plugin/src/main/java/io/takari/maven/plugins/jar/Jar.java);
* [`AggregateSource.java`](https://github.com/takari/takari-lifecycle/blob/master/takari-lifecycle-plugin/src/main/java/io/takari/maven/plugins/jar/AggregateSource.java);
* [`BytesEntry.java`](https://github.com/takari/takari-lifecycle/blob/master/takari-lifecycle-plugin/src/main/java/io/takari/maven/plugins/jar/BytesEntry.java);
* [`PomPropertiesMojo.java`](https://github.com/takari/takari-lifecycle/blob/master/takari-lifecycle-plugin/src/main/java/io/takari/maven/plugins/jar/PomPropertiesMojo.java).

Published `takari-lifecycle-plugin:2.3.4` resolves archiver `1.0.8`, as shown by
its [deps.dev dependency graph](https://api.deps.dev/v3/systems/MAVEN/packages/io.takari.maven.plugins%3Atakari-lifecycle-plugin/versions/2.3.4:dependencies).
Its migration must replace mutable legacy entries with `SourceEntry` and
callback traversal while retaining its custom `Source` implementations.

### Maveniverse Toolrunner

[maveniverse/toolrunner](https://github.com/maveniverse/toolrunner) is a live
consumer not visible in the archiver `1.0.9` reverse-dependency count:

* its [root POM](https://github.com/maveniverse/toolrunner/blob/main/pom.xml)
  manages archiver `1.0.9` on current `main`;
* [`shared/pom.xml`](https://github.com/maveniverse/toolrunner/blob/main/shared/pom.xml)
  declares the production dependency;
* [`Provisioners.java`](https://github.com/maveniverse/toolrunner/blob/main/shared/src/main/java/eu/maveniverse/maven/toolrunner/shared/support/Provisioners.java)
  imports `UnArchiver` and invokes its builder and extraction API.

The latest published `shared:0.4.1` still resolves archiver `1.0.8`, as shown by
its [deps.dev dependency graph](https://api.deps.dev/v3/systems/MAVEN/packages/eu.maveniverse.maven.toolrunner%3Ashared/versions/0.4.1:dependencies).
Its extraction-only migration is limited to `Path` arguments and the current
`UnArchiver` builder contract.

## Other source-visible consumers

These projects are pinned to older archiver releases. They do not break merely
because a new major version is published, but they should not be mistaken for
proof that the API is unused.

* [jvanzyl/provisio-tools](https://github.com/jvanzyl/provisio-tools) contains
  production imports and is pinned to `0.1.27`. It uses `ArtifactEntry` as
  model data and its tests use the synthetic artifact generators. Those
  generator types are removed from the new library API, so Provisio Tools must
  replace them locally before choosing to upgrade.
* [sigstore/sigstore-maven](https://github.com/sigstore/sigstore-maven) is
  archived and uses `JarArtifactGenerator` only in tests, pinned to `0.1.29`.
* [concord-workflow/concord-plugin-support](https://github.com/concord-workflow/concord-plugin-support)
  is a historical consumer of the older `io.tesla.proviso.archive` namespace,
  pinned to `0.1.21`.
* [sitoolkit/sit-util-bth](https://github.com/sitoolkit/sit-util-bth) is a
  historical consumer of the older namespace, pinned to `0.1.19`.
* `sitoolkit/sit-wt-all:sit-wt-util` retains a POM pin to `0.1.9`, although its
  current archive code uses Commons Compress rather than the archiver API.
* `desmax74/kie-takari-lifecycle` is an old lifecycle fork pinned to `0.1.11`.

deps.dev also reports old direct relationships from published Takari artifacts,
including `proto-maven-plugin`, `takari-plugin-testing`,
`takari-plugin-integration-testing`, `npm-model`, `npm-client`,
`nexus-plugin-bundle-maven-plugin`, and `io.takari:maven`. These releases date
from 2014 through 2016 and did not appear in current local Takari source
checkouts.

## deps.dev interpretation

deps.dev reports zero dependents for
[`takari-archiver:1.0.9`](https://deps.dev/_/s/maven/p/io.takari%3Atakari-archiver/v/1.0.9/dependents),
but reports fifteen total dependents for
[`1.0.8`](https://deps.dev/_/s/maven/p/io.takari%3Atakari-archiver/v/1.0.8/dependents).
The `1.0.8` direct families include released Provisio modules, Takari Lifecycle,
and Maveniverse Toolrunner. Toolrunner's tool modules account for its indirect
dependents.

These results have important limits:

* reverse dependencies are exact-version and published-artifact based;
* current snapshots and unreleased source changes are invisible;
* test-scoped dependencies may be omitted from resolved dependency graphs;
* private repositories and local projects are outside deps.dev coverage;
* transitive dependents do not necessarily import the API;
* the deps.dev website reverse-dependent endpoint is not part of the documented
  v3 API and may change.

The audit therefore supports a clean API break, but not the claim that there are
only two consumers. Provisio, Takari Lifecycle, and Maveniverse Toolrunner form
the immediate coordinated migration set. Older pinned consumers remain able to
use their existing archiver version.
