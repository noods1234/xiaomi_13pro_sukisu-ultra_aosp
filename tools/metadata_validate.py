#!/usr/bin/env python3
"""
Validates OIWCamera JSON sidecars against configs/schema/clip_metadata.schema.json, and optionally
verifies the recorded integrity SHA-256 hash against the actual clip file (docs/STORAGE_MEDIA.md #8).

Usage:
    python3 metadata_validate.py <sidecar.json> [<sidecar2.json> ...]
    python3 metadata_validate.py --verify-hash <sidecar.json>
    python3 metadata_validate.py --dir /path/to/OIW_MEDIA   # recursively validate every *.json sidecar

Uses only the Python standard library (no jsonschema dependency) with a small hand-rolled
type/required-field checker — sufficient for this schema's shape and keeps the tool dependency-free
for field use on a machine that may not have pip access.
"""
import argparse
import glob
import hashlib
import json
import os
import sys

REQUIRED_FIELDS = {
    "project": str,
    "dateTimeIso8601": str,
    "deviceModel": str,
    "cameraId": str,
    "resolution": str,
    "frameRateFps": int,
    "codec": str,
    "bitrateBps": int,
    "storageMedium": str,
}

OPTIONAL_FIELDS = {
    "scene": str, "shot": str, "take": int, "romBuild": str, "kernelBuild": str,
    "sensorMode": str, "shutter": str, "isoSensitivity": int, "whiteBalanceKelvin": int,
    "tint": int, "focusDistance": float, "stabilizationMode": str, "colorProfile": str,
    "lutId": str, "lutBaked": bool, "measuredWriteSpeedMbps": float, "droppedFrames": int,
    "thermalStateAtStop": str, "batteryPercentAtStop": int, "lensName": str,
    "focalLengthMm": float, "aperture": str, "adapter": str, "filterStack": list,
    "ndValue": str, "anamorphicSqueeze": float, "gpsLocation": str, "notes": str,
    "recovered": bool, "recoveryNotes": str, "integritySha256": str,
    "avSyncOffsetNanos": int, "slateMarkers": list,
}


def validate_one(path):
    errors = []
    try:
        with open(path) as f:
            data = json.load(f)
    except json.JSONDecodeError as e:
        return [f"{path}: invalid JSON ({e})"]
    except OSError as e:
        return [f"{path}: could not read ({e})"]

    for field, expected_type in REQUIRED_FIELDS.items():
        if field not in data:
            errors.append(f"{path}: missing required field '{field}'")
        elif expected_type is float and isinstance(data[field], (int, float)):
            pass
        elif not isinstance(data[field], expected_type):
            errors.append(
                f"{path}: field '{field}' has type {type(data[field]).__name__}, expected {expected_type.__name__}"
            )

    known_fields = set(REQUIRED_FIELDS) | set(OPTIONAL_FIELDS)
    for field in data:
        if field not in known_fields:
            errors.append(f"{path}: unrecognized field '{field}' (not in clip_metadata.schema.json)")

    return errors


def verify_hash(sidecar_path):
    with open(sidecar_path) as f:
        data = json.load(f)
    expected = data.get("integritySha256")
    if not expected:
        return [f"{sidecar_path}: no integritySha256 recorded, nothing to verify"]

    clip_path = sidecar_path.rsplit(".json", 1)[0] + ".mp4"
    if not os.path.exists(clip_path):
        return [f"{sidecar_path}: clip file not found at expected path {clip_path}"]

    sha256 = hashlib.sha256()
    with open(clip_path, "rb") as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b""):
            sha256.update(chunk)
    actual = sha256.hexdigest()

    if actual != expected:
        return [f"{clip_path}: HASH MISMATCH — expected {expected}, got {actual}. File may be corrupted."]
    return []


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("paths", nargs="*", help="sidecar JSON files to validate")
    parser.add_argument("--dir", help="recursively validate every *.json sidecar under this directory")
    parser.add_argument("--verify-hash", action="store_true", help="also verify integritySha256 against the clip file")
    args = parser.parse_args()

    paths = list(args.paths)
    if args.dir:
        paths += glob.glob(os.path.join(args.dir, "**", "*.json"), recursive=True)

    if not paths:
        parser.error("no sidecar files given (pass paths or --dir)")

    all_errors = []
    for path in paths:
        all_errors += validate_one(path)
        if args.verify_hash:
            all_errors += verify_hash(path)

    if all_errors:
        for e in all_errors:
            print(f"FAIL: {e}", file=sys.stderr)
        print(f"\n{len(all_errors)} problem(s) across {len(paths)} file(s).", file=sys.stderr)
        sys.exit(1)

    print(f"OK: {len(paths)} sidecar(s) valid.")


if __name__ == "__main__":
    main()
