#!/usr/bin/env bash
# Sustained sequential write benchmark for internal storage or an external SSD, run ON-DEVICE via adb
# shell (so results reflect the actual device's USB/storage controller, not the host machine's).
# docs/STORAGE_MEDIA.md #4.
#
# Usage: ./storage_benchmark.sh <on-device-path> [--serial <adb-serial>] [--size-mb 256] [--count 4]
#
# Output: JSON on stdout with sustainedWriteMbps, consumed by OIWCamera's StorageManager.preflight().

set -euo pipefail

TARGET_PATH=""
SERIAL=""
SIZE_MB=256
COUNT=4

while [[ $# -gt 0 ]]; do
    case "$1" in
        --serial) SERIAL="$2"; shift 2 ;;
        --size-mb) SIZE_MB="$2"; shift 2 ;;
        --count) COUNT="$2"; shift 2 ;;
        *) TARGET_PATH="$1"; shift ;;
    esac
done

if [[ -z "$TARGET_PATH" ]]; then
    echo "Usage: $0 <on-device-path> [--serial <adb-serial>] [--size-mb 256] [--count 4]" >&2
    exit 1
fi

ADB=(adb)
[[ -n "$SERIAL" ]] && ADB=(adb -s "$SERIAL")

if ! "${ADB[@]}" shell "test -d '$TARGET_PATH' || mkdir -p '$TARGET_PATH'"; then
    echo "ERROR: could not create/access $TARGET_PATH on device" >&2
    exit 1
fi

echo "Benchmarking $TARGET_PATH: $COUNT x ${SIZE_MB}MB sequential writes..." >&2

total_bytes=$((SIZE_MB * 1024 * 1024 * COUNT))
start_ns=$(date +%s%N)

for i in $(seq 1 "$COUNT"); do
    test_file="${TARGET_PATH}/oiw_bench_${i}.bin"
    # oflag=direct bypasses page cache where the on-device dd/kernel supports it; if unsupported,
    # dd will error and we fall back to a plain write + explicit sync, noted in the result.
    if ! "${ADB[@]}" shell "dd if=/dev/zero of='$test_file' bs=1M count=$SIZE_MB oflag=direct 2>&1" > /tmp/oiw_bench_$$.log 2>&1; then
        "${ADB[@]}" shell "dd if=/dev/zero of='$test_file' bs=1M count=$SIZE_MB 2>&1 && sync"
        direct_io_used=false
    else
        direct_io_used=true
    fi
    "${ADB[@]}" shell "rm -f '$test_file'"
done
rm -f /tmp/oiw_bench_$$.log

end_ns=$(date +%s%N)
elapsed_seconds=$(awk "BEGIN { print ($end_ns - $start_ns) / 1000000000.0 }")
mbps=$(awk "BEGIN { print ($total_bytes / 1000000.0) / $elapsed_seconds }")

cat <<EOF
{
  "targetPath": "$TARGET_PATH",
  "sampleSizeBytes": $total_bytes,
  "elapsedSeconds": $elapsed_seconds,
  "sustainedWriteMbps": $mbps,
  "directIoUsed": ${direct_io_used:-false},
  "note": "Sustained sequential write only. Does not measure random I/O or long-duration thermal-throttled write degradation — for that, run this alongside tools/thermal_monitor.sh during an actual long recording test (docs/TEST_PLAN.md Storage Tests)."
}
EOF
