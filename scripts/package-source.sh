#!/usr/bin/env bash
# Build a source + docs archive for hand-off, excluding build caches and APKs.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

VERSION="$(sed -n 's/.*versionName = "\(.*\)".*/\1/p' app/build.gradle.kts | head -1)"
VERSION_CODE="$(sed -n 's/.*versionCode = \([0-9]*\).*/\1/p' app/build.gradle.kts | head -1)"
[ -n "$VERSION" ] || { echo "无法读取 versionName" >&2; exit 1; }

STAGE="$(mktemp -d "${TMPDIR:-/tmp}/lumasr-source.XXXXXX")"
trap 'rm -rf "$STAGE"' EXIT

DEST="$STAGE/LumaSR-${VERSION}"
mkdir -p "$DEST"

rsync -a \
  --exclude='.git/' \
  --exclude='.gradle/' \
  --exclude='.kotlin/' \
  --exclude='.idea/' \
  --exclude='build/' \
  --exclude='dist/' \
  --exclude='local.properties' \
  --exclude='*.apk' \
  --exclude='*.zip' \
  --exclude='*.jks' \
  --exclude='*.keystore' \
  --exclude='*.hprof' \
  --exclude='captures/' \
  --exclude='.externalNativeBuild/' \
  --exclude='.cxx/' \
  --exclude='.DS_Store' \
  "$ROOT/" "$DEST/"

{
  echo "LumaSR source hand-off"
  echo "version: $VERSION ($VERSION_CODE)"
  echo "date: $(date -u +%Y-%m-%dT%H:%M:%SZ)"
  echo "package: io.lumasr.app"
  echo "abi: arm64-v8a"
  echo "build: JDK 17 + Android SDK 35, ./gradlew :app:assembleRelease"
  echo "docs: README.md, docs/DEV_HANDOFF.md, docs/PLAN.md, docs/MODELS.md, docs/DESIGN.md"
  echo
  echo "Excluded: build/, .gradle/, .kotlin/, dist/, local.properties, APK, signing keys"
} > "$DEST/SOURCE_INFO.txt"

mkdir -p "$ROOT/dist"
OUT="$ROOT/dist/LumaSR-${VERSION}-source.zip"
rm -f "$OUT" "$OUT.sha256"
(
  cd "$STAGE"
  zip -r -q "$OUT" "LumaSR-${VERSION}"
)

if command -v sha256sum >/dev/null 2>&1; then
  SHA="$(sha256sum "$OUT" | awk '{print $1}')"
else
  SHA="$(shasum -a 256 "$OUT" | awk '{print $1}')"
fi
printf '%s  %s\n' "$SHA" "$(basename "$OUT")" > "$OUT.sha256"

SIZE="$(wc -c < "$OUT" | tr -d ' ')"
printf '==> %s\n    %s bytes\n    SHA-256 %s\n' "$OUT" "$SIZE" "$SHA"
