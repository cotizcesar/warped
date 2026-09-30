#!/usr/bin/env bash
set -euo pipefail

# 16KB-01: ELF alignment checker over the release artifact.
#
# Verifies every shipped .so has all PT_LOAD segments with p_align >= 16384
# (0x4000), as required for Android 15+ 16 KB page-size devices.
# Fails closed: nonzero exit on misalignment or missing artifact.

cd "$(dirname "$0")/.."

resolve_artifact() {
  if [ -n "${1:-}" ]; then
    echo "$1"
    return
  fi
  local aab="app/build/outputs/bundle/release/app-release.aab"
  local apk="app/build/outputs/apk/release/app-release.apk"
  if [ -f "$aab" ]; then
    echo "$aab"
  elif [ -f "$apk" ]; then
    echo "$apk"
  else
    echo "ERROR: no release artifact found (looked for $aab and $apk)" >&2
    echo "Usage: $0 [path-to-release.aab-or-.apk]" >&2
    exit 1
  fi
}

ARTIFACT="$(resolve_artifact "${1:-}")"
if [ ! -f "$ARTIFACT" ]; then
  echo "ERROR: artifact not found: $ARTIFACT" >&2
  echo "Usage: $0 [path-to-release.aab-or-.apk]" >&2
  exit 1
fi

TMPDIR="$(mktemp -d)"
trap 'rm -rf "$TMPDIR"' EXIT
unzip -q -o "$ARTIFACT" -d "$TMPDIR"

mapfile -t SOS < <(find "$TMPDIR" -name '*.so' | sort)
if [ "${#SOS[@]}" -eq 0 ]; then
  echo "ERROR: no .so files found in $ARTIFACT" >&2
  exit 1
fi

FAIL=0
for so in "${SOS[@]}"; do
  # Relativize for stable output: strip temp prefix, keep lib/<abi>/<name>
  # (APK layout) or base/lib/<abi>/<name> (AAB layout).
  rel="${so#"$TMPDIR"/}"
  # Check every PT_LOAD segment alignment via readelf. -W (wide) keeps each
  # segment on one line so $NF is the Align column even for 64-bit ELFs
  # (without -W, 64-bit addresses wrap and $NF would be VirtAddr, not Align).
  misaligned=0
  while read -r align; do
    # align is hex like 0x4000; compare numerically.
    dec=$((align))
    if [ "$dec" -lt 16384 ]; then
      misaligned=1
    fi
  done < <(readelf -lW "$so" | awk '/^  LOAD/ {print $NF}')
  if [ "$misaligned" -eq 1 ]; then
    echo "MISALIGNED $rel"
    FAIL=1
  else
    echo "ALIGNED $rel"
  fi
done

if [ "$FAIL" -ne 0 ]; then
  echo "FAIL: misaligned native libraries found (see MISALIGNED lines above)" >&2
  exit 1
fi
echo "OK: all ${#SOS[@]} native libraries are 16 KB-aligned"
