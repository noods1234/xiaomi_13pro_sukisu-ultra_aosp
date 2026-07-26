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

## Offline verification harness (no device, no Android SDK)

| File | Purpose | Doc |
|---|---|---|
| `verify_compile.sh` | The gate. Compiles every app class against real Android API 34 and **executes** the pure-JVM and Robolectric test tiers, then runs the wiring guard | `docs/TEST_PLAN.md` §0 |
| `check_wiring.py` | Fails the build when a `main/` class has no production-code reference — the mechanical guard against the dead-code pattern | `docs/AUDIT_FINDINGS.md` finding P |
| `unwired_allowlist.txt` | The only classes allowed to be unwired, each with a specific named blocker | — |
| `robolectric_deps.txt` | Pinned Maven Central coordinates for the Robolectric execution tier | `docs/TEST_PLAN.md` §0 |
| `robolectric_shim/` | Minimal `androidx.test` API surface, because that artifact is Google-Maven-only and blocked here | `tools/robolectric_shim/README.md` |
| `verify_stubs/` | Hand-written androidx/AGP stubs for the weakest compile tier — treat tier-3 passes as *plausible*, not verified | `docs/TEST_PLAN.md` §0 |

Run it with `bash tools/verify_compile.sh` (add `--quick` to skip the test tiers, or set
`OIW_SKIP_ROBOLECTRIC=1` to skip the ~290 MB android-all downloads). CI runs the same script on
every push and PR touching `packages/apps/` or the harness.

The field scripts below all require `adb` on `PATH`. `thermal_monitor.sh` and `vendor/oiw/nuwa/extract-files.sh` require
root (`su`) on the device, already available via the SukiSU Ultra kernel this repo builds.
