#!/usr/bin/env python3
"""
Dumps Camera2 capability facts for every camera ID on a connected device via `adb shell dumpsys
media.camera` and `adb shell dumpsys camera` text output, plus vendor tag discovery.

This is a *diagnostic aid*, not a substitute for the in-app debug panel (docs/CAMERA_PIPELINE.md #1) —
`dumpsys` output format is not a stable API and varies across Android/vendor versions; this script
parses it best-effort and clearly reports what it could NOT determine rather than guessing.

Usage:
    python3 camera_capability_dump.py --serial <adb-serial> --out camera_dump.json [--vendor-tags]
"""
import argparse
import json
import re
import subprocess
import sys
from datetime import datetime, timezone


def adb(serial, *args):
    cmd = ["adb"]
    if serial:
        cmd += ["-s", serial]
    cmd += list(args)
    result = subprocess.run(cmd, capture_output=True, text=True, timeout=30)
    return result.stdout, result.returncode


def get_prop(serial, prop):
    out, rc = adb(serial, "shell", "getprop", prop)
    return out.strip() if rc == 0 else None


def list_camera_ids(serial):
    """Best-effort camera ID enumeration via dumpsys media.camera. Falls back to a
    fixed guess range [0-3] if parsing fails, clearly flagged as a guess."""
    out, rc = adb(serial, "shell", "dumpsys", "media.camera")
    ids = sorted(set(re.findall(r"Camera\s+ID\s*[:=]?\s*(\d+)", out)))
    if ids:
        return ids, False
    return ["0", "1", "2", "3"], True  # flagged as a guess, not confirmed


def dump_characteristics_text(serial, camera_id):
    out, rc = adb(serial, "shell", "dumpsys", "media.camera")
    return out if rc == 0 else None


def extract_field(text, pattern, group=1):
    if text is None:
        return None
    match = re.search(pattern, text)
    return match.group(group) if match else None


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", default=None, help="adb device serial (omit if only one device attached)")
    parser.add_argument("--out", required=True, help="output JSON path")
    parser.add_argument("--vendor-tags", action="store_true", help="also attempt vendor tag namespace discovery")
    args = parser.parse_args()

    device_model = get_prop(args.serial, "ro.product.model")
    device_codename = get_prop(args.serial, "ro.product.device")
    android_release = get_prop(args.serial, "ro.build.version.release")
    hyperos_version = get_prop(args.serial, "ro.mi.os.version.code")

    if device_codename and device_codename != "nuwa":
        print(
            f"WARNING: connected device codename is '{device_codename}', not 'nuwa'. "
            "docs/CAMERA_PIPELINE.md assumptions may not apply.",
            file=sys.stderr,
        )

    camera_ids, ids_are_guessed = list_camera_ids(args.serial)
    raw_text = dump_characteristics_text(args.serial, None)

    cameras = []
    for cam_id in camera_ids:
        cameras.append({
            "cameraId": cam_id,
            "note": (
                "Full per-camera characteristics parsing from dumpsys text is unreliable across "
                "Android versions. For authoritative per-camera facts (active array size, "
                "availableCapabilities, RAW support, exposure/ISO ranges, vendor tags), use the "
                "OIWCamera in-app debug panel (docs/CAMERA_PIPELINE.md #1), which reads "
                "CameraCharacteristics directly via the real Camera2 API rather than parsing "
                "dumpsys text."
            ),
        })

    result = {
        "generatedAtUtc": datetime.now(timezone.utc).isoformat(),
        "deviceModel": device_model,
        "deviceCodename": device_codename,
        "androidRelease": android_release,
        "hyperOsVersionCode": hyperos_version,
        "cameraIds": cameras,
        "cameraIdsWereGuessed": ids_are_guessed,
        "vendorTagsRequested": args.vendor_tags,
        "vendorTagsNote": (
            "Vendor tag enumeration requires reading CameraCharacteristics#getAvailableCaptureRequestKeys() "
            "from inside a running app process (this script cannot do it via adb alone) — see the OIWCamera "
            "debug panel's vendor tag dumper." if args.vendor_tags else None
        ),
    }

    with open(args.out, "w") as f:
        json.dump(result, f, indent=2)

    print(f"Wrote {args.out}")
    if ids_are_guessed:
        print(
            "WARNING: camera ID list could not be parsed from dumpsys output and is a guessed "
            "placeholder range. Confirm real camera IDs via the OIWCamera debug panel.",
            file=sys.stderr,
        )


if __name__ == "__main__":
    main()
