#!/usr/bin/env bash
# Extract vendor blobs listed in proprietary-files.txt from a running, rooted nuwa device.
# Output is written OUTSIDE this repo by default and must never be committed (see README.md).
#
# Usage: ./extract-files.sh --serial <adb-serial> --out /path/outside/this/repo/blobs
#
# This is reference tooling for a future Path A effort (docs/ARCHITECTURE.md §2). It is not part of the
# implemented Path D build and is not invoked by any CI in this repo.

set -euo pipefail

SERIAL=""
OUT_DIR=""
MANIFEST="$(dirname "$0")/proprietary-files.txt"

usage() {
    echo "Usage: $0 --serial <adb-serial> --out <output-dir> [--manifest <proprietary-files.txt>]" >&2
    exit 1
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --serial) SERIAL="$2"; shift 2 ;;
        --out) OUT_DIR="$2"; shift 2 ;;
        --manifest) MANIFEST="$2"; shift 2 ;;
        *) usage ;;
    esac
done

[[ -z "$OUT_DIR" ]] && usage

ADB=(adb)
[[ -n "$SERIAL" ]] && ADB=(adb -s "$SERIAL")

if ! "${ADB[@]}" shell su -c 'id -u' 2>/dev/null | grep -q '^0$'; then
    echo "ERROR: device is not reachable with root (su). extract-files.sh requires root to read /vendor and" >&2
    echo "       /system partitions for files not exposed to a normal adb pull." >&2
    exit 1
fi

if [[ ! -f "$MANIFEST" ]]; then
    echo "ERROR: manifest not found: $MANIFEST" >&2
    exit 1
fi

mkdir -p "$OUT_DIR"
echo "Extracting to: $OUT_DIR"
echo "Manifest:      $MANIFEST"
echo

MANIFEST_HASHES="$OUT_DIR/BLOB_HASHES.sha256"
: > "$MANIFEST_HASHES"

while IFS= read -r line; do
    # Skip blanks and comments
    [[ -z "$line" || "$line" == \#* ]] && continue

    src_path="$line"
    dest_path="$OUT_DIR/${src_path#/}"
    mkdir -p "$(dirname "$dest_path")"

    echo "Pulling: $src_path"
    if "${ADB[@]}" shell su -c "test -e '$src_path'" 2>/dev/null; then
        "${ADB[@]}" shell su -c "cat '$src_path'" > "$dest_path" 2>/dev/null || {
            echo "  WARN: failed to read $src_path (permission or path issue) — skipped" >&2
            rm -f "$dest_path"
            continue
        }
        sha256sum "$dest_path" >> "$MANIFEST_HASHES"
    else
        echo "  WARN: not found on device — skipped" >&2
    fi
done < "$MANIFEST"

echo
echo "Done. Blob hashes recorded in $MANIFEST_HASHES"
echo "Reminder: do not commit anything under $OUT_DIR to any git repository."
