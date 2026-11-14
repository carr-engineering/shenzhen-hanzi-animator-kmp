# Validation and release

This repository prepares a public release; publishing is a separate deliberate
step. `libraryVersion` in `gradle.properties` supplies the Maven artifact,
generated API and sample version. The initial version is `0.1.0`; the browser
package version must match it. Public coordinates are
`engineering.carr:shenzhen-hanzi-animator-{core,codec,json,data,bundled-data,compose,material3}`.

## Required checks

Use JDK 17, the checked-in Gradle wrapper, Android SDK 36, Node 22 and Python 3.14.
Apple checks require a macOS ARM64 runner with Xcode/simulator support. Release
data regeneration uses the exact tool versions recorded in profile manifests;
different compressors can change encoded bytes without changing decoded data.

```sh
python3 -m unittest discover -s tools -p 'test_*.py'
./gradlew :core:jvmTest :codec:jvmTest :json:jvmTest :data:jvmTest \
  :bundled-data:jvmTest :library:jvmTest :material3:jvmTest
./gradlew :androidApp:assembleRelease
./gradlew :core:iosSimulatorArm64Test :codec:iosSimulatorArm64Test \
  :json:iosSimulatorArm64Test :data:iosSimulatorArm64Test \
  :bundled-data:iosSimulatorArm64Test :library:compileTestKotlinIosSimulatorArm64 \
  :material3:compileTestKotlinIosSimulatorArm64 :sample:linkDebugFrameworkIosSimulatorArm64
./gradlew :sample:wasmJsBrowserDistribution \
  :browser-engine:jsBrowserProductionLibraryDistribution :browser-engine:jsNodeTest
cd browser
npm ci
npm run build
npm test
cd ..
./integration-smoke/run.sh
```

The corpus checks compare every one of the 9,574 enriched records, including
commands, all median points, radicals, repeated component occurrences and null
memberships. Decoder checks cover malformed/truncated archives, checksums and
bounds. Loading checks cover caches, independent requests, failure/retry and
cancellation; Compose checks cover lifecycle and render behavior. The standalone
consumer resolves local Maven publications, compiles public examples, runs basic
core/codec/data/JSON/controller usage and asserts dependency closure.

Inspect the Android APK, iOS bundle and both browser distributions for expected
resource placement and license texts. Check Canvas controls/loading/error states
at narrow and wide widths, light/dark themes and with localized labels; simulator
compilation does not establish physical-device behavior. CI records test reports
and distributions. Any claim of release readiness must name remaining unchecked
platform/device or packaging behavior.

## Local publication consumer

`integration-smoke/run.sh` publishes to an isolated local Maven repository using
`publicationTestRepositoryUrl`, publishes root metadata and JVM variants, then invokes a separate Gradle settings/build
with `smokeRepositoryUrl`. It neither includes the repository's projects nor
uses an included build or `mavenLocal()`. See
[integration-smoke/README](../integration-smoke/README.md) for manual commands.
Pass a concrete GitHub repository URL to the script if it is known; smoke tests
default to a nonpublished local-test metadata URL.

The 2026-10-12 local run passed after publishing all seven root metadata/JVM
pairs and consuming them from the independent build. Its five consumers passed
five runtime tests plus public Compose/Material compilation, dependency closure
and both data-classifier layout checks. Fourteen POMs/module metadata files were
inspected; bundled data POMs include MIT code, Arphic glyph and LGPL metadata
licenses. The local-test SCM URL is deliberately not a public-release claim.
[`publication-smoke-report.json`](publication-smoke-report.json) records the
resolved graph and results. Compose's upstream saved-state support includes
serialization-core; the JSON importer/runtime remains separately optional.

## Maven Central publication

Set the real canonical public GitHub repository URL explicitly:

```sh
./gradlew publishAllPublicationsToMavenCentralRepository \
  -PpublicationRepositoryUrl=https://github.com/owner/repository
```

The Maven Central tasks reject missing/nonconcrete repository metadata. Replace
`owner/repository` with the actual repository; do not publish with a placeholder.
Supply credentials and signing material through Gradle's environment properties:
`ORG_GRADLE_PROJECT_mavenCentralUsername`,
`ORG_GRADLE_PROJECT_mavenCentralPassword`,
`ORG_GRADLE_PROJECT_signingInMemoryKey`, and any required signing password/key ID.
Signing is enabled when the in-memory key is present. Before uploading, inspect
all target POMs/module metadata, source artifacts, licenses and SCM URLs. Verify
that the account owns the `engineering.carr` namespace. Run the isolated consumer
against staging/public coordinates after upload; local success does not prove
Central availability. No release upload is performed by ordinary CI.

## Canvas package and data hosting

Build the engine and run `npm ci`, `npm run build`, `npm test` in `browser/`.
Inspect `npm pack --dry-run` for declarations, self-contained ES modules, MIT and
NOTICE; the package deliberately excludes the dataset and demo. Publish only
when the package name, account and version are final. Deploy the generated web
strokes and their licenses separately with immutable versioned URLs and
Content-Encoding negotiation. The build enforces a 250 KiB gzip JavaScript
budget and writes `browser/dist/size-report.json`.

Retain full Arphic/LGPL/GPL/Unicode notices and corresponding enriched source and
regeneration tools with data distribution. The sample's separate Noto OFL text
and provenance accompany its font. See [third-party record](THIRD_PARTY.md).
`PLAN.md` and older review assets are historical evidence, not current validation.

Current local results and remaining device checks are recorded in [VALIDATION.md](VALIDATION.md). The reproducible compression comparison is `python3 tools/benchmark_compression.py`.
