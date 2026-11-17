#!/usr/bin/env bash
# Incremental release build. No clean: Kotlin/resource/native caches are reused.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

find_jdk17() {
  local candidate
  for candidate in \
    "${JAVA_HOME:-}" \
    "$HOME"/.local/share/mise/installs/java/17* \
    /usr/lib/jvm/*17* \
    /opt/homebrew/opt/openjdk@17 \
    /usr/local/opt/openjdk@17; do
    [ -n "$candidate" ] && [ -x "$candidate/bin/java" ] || continue
    if "$candidate/bin/java" -version 2>&1 | grep -qE 'version "17'; then
      printf '%s\n' "$candidate"
      return 0
    fi
  done
  return 1
}

JDK17="$(find_jdk17 || true)"
if [ -z "$JDK17" ]; then
  echo "需要 JDK 17：请安装后设置 JAVA_HOME，或把它放到常见路径。" >&2
  exit 1
fi
export JAVA_HOME="$JDK17"

PROXY_ARGS=()
PROXY_URL="${HTTPS_PROXY:-${https_proxy:-}}"
if [ -n "$PROXY_URL" ]; then
  host_port="${PROXY_URL#*://}"
  host="${host_port%%:*}"
  port="${host_port##*:}"
  if [ -n "$host" ] && [ -n "$port" ] && [ "$host" != "$host_port" ]; then
    PROXY_ARGS=(
      "-Dhttps.proxyHost=$host" "-Dhttps.proxyPort=$port"
      "-Dhttp.proxyHost=$host" "-Dhttp.proxyPort=$port"
    )
    # Also lets the wrapper download Gradle itself through the proxy on a
    # fresh machine before the daemon starts.
    export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:-} -Dhttps.proxyHost=$host -Dhttps.proxyPort=$port -Dhttp.proxyHost=$host -Dhttp.proxyPort=$port"
  fi
fi

VERSION="$(sed -n 's/.*versionName = "\(.*\)".*/\1/p' app/build.gradle.kts | head -1)"
VERSION_CODE="$(sed -n 's/.*versionCode = \([0-9]*\).*/\1/p' app/build.gradle.kts | head -1)"
[ -n "$VERSION" ] || { echo "无法从 app/build.gradle.kts 读取 versionName" >&2; exit 1; }

if [ "${1:-}" != "--no-build" ]; then
  echo "==> assembleRelease ($VERSION / $VERSION_CODE, JDK 17: $JAVA_HOME)"
  ./gradlew ${PROXY_ARGS[@]+"${PROXY_ARGS[@]}"} :app:assembleRelease --console=plain
fi

APK="app/build/outputs/apk/release/app-release.apk"
[ -f "$APK" ] || { echo "没有找到 $APK" >&2; exit 1; }

mkdir -p dist
OUT="dist/LumaSR-${VERSION}.apk"
cp "$APK" "$OUT"
if command -v sha256sum >/dev/null 2>&1; then
  SHA="$(sha256sum "$OUT" | awk '{print $1}')"
else
  SHA="$(shasum -a 256 "$OUT" | awk '{print $1}')"
fi
printf '%s  %s\n' "$SHA" "LumaSR-${VERSION}.apk" > "dist/LumaSR-${VERSION}.sha256"

SIZE="$(wc -c < "$OUT" | tr -d ' ')"
printf '\n==> %s\n' "$OUT"
printf '    %s bytes\n    SHA-256 %s\n' "$SIZE" "$SHA"
