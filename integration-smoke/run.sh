#!/usr/bin/env bash
set -euo pipefail
smoke_root="$(cd "$(dirname "$0")/.." && pwd)"
smoke_repo="${SMOKE_REPOSITORY_DIR:-$smoke_root/build/integration-smoke-repository}"
smoke_url="$(python3 -c 'import pathlib,sys; print(pathlib.Path(sys.argv[1]).resolve().as_uri())' "$smoke_repo")"
smoke_version="${SMOKE_VERSION:-$(sed -n 's/^libraryVersion=//p' "$smoke_root/gradle.properties")}"
smoke_metadata="${PUBLICATION_REPOSITORY_URL:-https://github.com/local-test/hanzi-consumer}"
smoke_publications=()
for smoke_module in core codec json data bundled-data library material3; do
  smoke_publications+=(":$smoke_module:publishKotlinMultiplatformPublicationToIntegrationSmokeRepository")
  smoke_publications+=(":$smoke_module:publishJvmPublicationToIntegrationSmokeRepository")
done
"$smoke_root/gradlew" -p "$smoke_root" "${smoke_publications[@]}" \
  "-PpublicationTestRepositoryUrl=$smoke_url" "-PpublicationRepositoryUrl=$smoke_metadata" --stacktrace
"$smoke_root/gradlew" -p "$smoke_root/integration-smoke" check \
  "-PsmokeRepositoryUrl=$smoke_url" "-PsmokeVersion=$smoke_version" --no-configuration-cache --stacktrace
