#!/usr/bin/env bash
# Collects a logcat slice filtered to OIWCamera/OIWLauncher tags plus a full bugreport, for field
# debugging (docs/RED_TEAM_AUDIT.md camera-crash / thermal-runaway cases, docs/UI_UX.md debug panel).
#
# Usage: ./log_collector.sh [--serial <adb-serial>] [--out-dir ./logs] [--bugreport]

set -euo pipefail

SERIAL=""
OUT_DIR="./logs"
WANT_BUGREPORT=false

while [[ $# -gt 0 ]]; do
    case "$1" in
        --serial) SERIAL="$2"; shift 2 ;;
        --out-dir) OUT_DIR="$2"; shift 2 ;;
        --bugreport) WANT_BUGREPORT=true; shift ;;
        *) echo "Unknown arg: $1" >&2; exit 1 ;;
    esac
done

ADB=(adb)
[[ -n "$SERIAL" ]] && ADB=(adb -s "$SERIAL")

mkdir -p "$OUT_DIR"
STAMP="$(date +%Y%m%d_%H%M%S)"

echo "Clearing then capturing logcat (Ctrl-C to stop capture)..." >&2
"${ADB[@]}" logcat -c
"${ADB[@]}" logcat -v threadtime \
    "OIWCameraController:V" "OIWRecorderWriter:V" "OIWBootToCamera:V" "CameraManager:*" \
    "CameraDevice:*" "AndroidRuntime:E" "*:S" \
    | tee "$OUT_DIR/logcat_${STAMP}.txt"

if $WANT_BUGREPORT; then
    echo "Capturing full bugreport (this can take a couple of minutes)..." >&2
    "${ADB[@]}" bugreport "$OUT_DIR/bugreport_${STAMP}"
fi

echo "Saved logs under $OUT_DIR" >&2
