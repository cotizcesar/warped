#!/usr/bin/env bash
set -euo pipefail

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

echo "==> Running dependency audit (banned Gallery patterns)..."
REPORT=$(./gradlew :app:dependencies --no-daemon 2>&1) || {
  echo "FAIL: gradle :app:dependencies did not succeed"
  echo "$REPORT" | tail -40
  exit 1
}

HITS=()
for pattern in "${BANNED_PATTERNS[@]}"; do
  if echo "$REPORT" | grep -iE "(\\b|\\.)${pattern}(\\b|:|-)" >/dev/null 2>&1; then
    HITS+=("$pattern")
  fi
done

if [ ${#HITS[@]} -gt 0 ]; then
  echo ""
  echo "FAIL: Banned dependencies detected:"
  for hit in "${HITS[@]}"; do
    echo "  - $hit"
  done
  echo ""
  echo "Run with --report for full match details:"
  PATTERN_ALT=$(IFS='|'; echo "${BANNED_PATTERNS[*]}")
  echo "$REPORT" | grep -iE "(\\b|\\.)${PATTERN_ALT}(\\b|:|-)" | head -20
  exit 1
fi

echo "OK: no banned dependencies found"
