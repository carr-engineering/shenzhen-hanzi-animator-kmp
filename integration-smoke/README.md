# Published-artifact consumer smoke check

This is a separate Gradle build. It resolves Maven publications and target module
metadata without project dependencies, composite builds or `mavenLocal()`.
Five independent JVM consumers verify core/codec, Compose, bundled data, optional
JSON and optional Material 3. Runtime closure checks reject UI from headless
modules, JSON importer/runtime outside JSON, Material outside its adapter and network or
Compose Resources dependencies everywhere. Core/codec/bundled consumers remain
serialization-free; upstream Compose saved-state may bring serialization-core.

Run from the main repository after its tests:

```sh
./integration-smoke/run.sh
```

The script publishes root multiplatform metadata and JVM variants to `build/integration-smoke-repository`, then compiles public
Compose/Material examples and executes session, binary, JSON, classpath corpus,
license and controller checks. It also resolves the published `ios-data` and
`web-data` ZIP classifiers and verifies native/web data and complete notice placement. `SMOKE_REPOSITORY_DIR`, `SMOKE_VERSION` and
`PUBLICATION_REPOSITORY_URL` override the repository directory, version and POM
metadata. Its default metadata URL is for local testing only.

To consume artifacts already published to a staging or local repository:

```sh
./gradlew -p integration-smoke check \
  -PsmokeRepositoryUrl=file:///absolute/path/to/maven-repository \
  -PsmokeVersion=0.1.0
```

Only `engineering.carr` is resolved from the supplied repository; other
dependencies come from Google/Maven Central. Reports are in each consumer's
`build/reports/runtime-dependencies.txt` and `build/reports/tests/`. This check
exercises JVM publication consumption. Android APK, iOS embedding/simulator tests
and JS/Wasm/Canvas distributions are separate CI jobs and release checks.
