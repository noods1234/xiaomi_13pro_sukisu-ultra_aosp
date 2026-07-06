# tools/

Host-side (adb-driven) field tooling. None of these require the OIWCamera app to be running — they
talk to the device directly via `adb`/`adb shell`, so they work even during initial bring-up before
any app is installed.

| Script | Purpose | Doc |
|---|---|---|
| `camera_capability_dump.py` | Best-effort camera ID enumeration + device identity dump via `dumpsys`; defers detailed per-camera characteristics to the in-app debug panel (dumpsys text isn't a stable API) | `docs/CAMERA_PIPELINE.md` #1 |
| `storage_benchmark.sh` | On-device sustained sequential write benchmark (internal or external SSD path) | `docs/STORAGE_MEDIA.md` #4 |
| `thermal_monitor.sh` | Polls all `/sys/class/thermal/thermal_zone*` via root, logs CSV | `docs/THERMAL_POWER.md` #1 |
| `log_collector.sh` | Filtered logcat capture + optional full bugreport | `docs/RED_TEAM_AUDIT.md` |
| `metadata_validate.py` | Validates clip JSON sidecars against `configs/schema/clip_metadata.schema.json`; optional SHA-256 integrity check | `docs/STORAGE_MEDIA.md` #8 |

All scripts require `adb` on `PATH`. `thermal_monitor.sh` and `vendor/oiw/nuwa/extract-files.sh` require
root (`su`) on the device, already available via the SukiSU Ultra kernel this repo builds.
