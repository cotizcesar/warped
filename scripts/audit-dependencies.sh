#!/usr/bin/env bash
set -euo pipefail

# RUNTIME-12 dependency audit.
#
# Gate: Warped must not adopt Gallery anti-pattern libraries as DIRECT
# dependencies, and no pre-release artifact (SNAPSHOT/alpha/beta/RC/milestone)
# may enter the release graph.
#
# Scope note (Phase 45-01): the banned list is checked against DIRECT
# declarations in gradle/libs.versions.toml (the single source of truth for
# every coordinate). Transitive hits are explicitly NOT counted — gson
# (via tink-android <- security-crypto, and via litertlm-android),
# kotlin-reflect (via litertlm-android), moshi (via benchmark-common <-
# benchmark-macro-junit4 androidTest), and datastore-preferences-proto
# (via datastore-preferences itself) are unavoidable transitive deps of
# required first-party Google libraries and cannot be removed without
# dropping security-crypto / litertlm / benchmark / datastore.

BANNED_PATTERNS=(
  "kapt"
  "firebase"
  "moshi"
  "gson"
  "kotlin-reflect"
  "ktor"
  "mcp"
  "tflite"
  "mlkit-genai"
  "appauth"
  "compose-richtext"
  "camerax"
  "datastore.*proto"
)

cd "$(dirname "$0")/.."

FAIL=0

echo "==> [1/2] Checking direct declarations (gradle/libs.versions.toml)..."
DECLARED=$(cat gradle/libs.versions.toml)
for pattern in "${BANNED_PATTERNS[@]}"; do
  if echo "$DECLARED" | grep -iE "(\\b|\\.|_)${pattern}(\\b|:|-|_)"; then
    echo "FAIL: banned direct dependency pattern '${pattern}' in gradle/libs.versions.toml"
    FAIL=1
  fi
done

echo "==> [2/2] Checking release graph for pre-release artifacts..."
REPORT=$(./gradlew :app:dependencies --configuration releaseRuntimeClasspath --no-daemon 2>&1) || {
  echo "FAIL: gradle :app:dependencies did not succeed"
  echo "$REPORT" | tail -40
  exit 1
}

if echo "$REPORT" | grep -iE 'SNAPSHOT|alpha|beta|rc[0-9]|cr[0-9]|-m[0-9]'; then
  echo "FAIL: pre-release artifact in releaseRuntimeClasspath (see match above)"
  FAIL=1
fi

if [ "$FAIL" -ne 0 ]; then
  echo ""
  echo "FAIL: dependency audit found banned entries (see above)"
  exit 1
fi

echo "OK: no banned direct dependencies, no pre-release artifacts in release graph"
