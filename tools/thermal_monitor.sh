#!/usr/bin/env bash
# Polls every /sys/class/thermal/thermal_zone*/temp on a connected device and logs a CSV row per
# interval. Requires root (already granted by SukiSU Ultra) since some zones aren't world-readable.
# docs/THERMAL_POWER.md #1, docs/TEST_PLAN.md #7.
#
# Usage: ./thermal_monitor.sh [--serial <adb-serial>] [--interval-seconds 5] [--out thermal_log.csv]
# Stop with Ctrl-C; the CSV is flushed after every sample so a partial run is still usable.

set -euo pipefail

SERIAL=""
INTERVAL=5
OUT="thermal_log_$(date +%Y%m%d_%H%M%S).csv"

while [[ $# -gt 0 ]]; do
    case "$1" in
        --serial) SERIAL="$2"; shift 2 ;;
        --interval-seconds) INTERVAL="$2"; shift 2 ;;
        --out) OUT="$2"; shift 2 ;;
        *) echo "Unknown arg: $1" >&2; exit 1 ;;
    esac
done

ADB=(adb)
[[ -n "$SERIAL" ]] && ADB=(adb -s "$SERIAL")

if ! "${ADB[@]}" shell su -c 'id -u' 2>/dev/null | grep -q '^0$'; then
    echo "ERROR: root (su) not available on this device. thermal_monitor.sh needs root to read all" >&2
    echo "       thermal zones (some are not world-readable). Confirm SukiSU Ultra grants su." >&2
    exit 1
fi

echo "Logging to $OUT every ${INTERVAL}s. Press Ctrl-C to stop." >&2

# Discover zone list once; if a device model exposes a different set mid-run (unlikely), rerun.
zone_types=$("${ADB[@]}" shell su -c \
    'for z in /sys/class/thermal/thermal_zone*; do echo "$(basename $z):$(cat $z/type 2>/dev/null)"; done')

echo "Discovered zones:" >&2
echo "$zone_types" >&2

header="timestamp_utc"
zone_list=()
while IFS=: read -r zone type; do
    [[ -z "$zone" ]] && continue
    zone_list+=("$zone")
    header+=",${type:-$zone}_millideg"
done <<< "$zone_types"
echo "$header" > "$OUT"

trap 'echo; echo "Stopped. Log saved to $OUT" >&2; exit 0' INT

while true; do
    row="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
    for zone in "${zone_list[@]}"; do
        temp=$("${ADB[@]}" shell su -c "cat /sys/class/thermal/$zone/temp 2>/dev/null" | tr -d '\r\n')
        row+=",${temp:-NA}"
    done
    echo "$row" >> "$OUT"
    sleep "$INTERVAL"
done
