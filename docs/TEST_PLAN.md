# OIW-ROM Test Plan

Every test below is either **automated** (unit/instrumented test in the app's test source set) or a **user action**
(requires real hardware; cannot be executed in this environment). Status column reflects what exists in this change.

## 1. Unit tests

| Test | Status |
|---|---|
| `CaptureProfile` JSON (de)serialization against schema | Implemented (`app/src/test/`) |
| Kelvin→RGB gain approximation (WB math) | Implemented |
| `.cube` LUT parser (17/33/65-point, malformed-file handling) | Implemented |
| Metadata sidecar atomic-write logic (temp-file+rename) | Implemented |
| Thermal-mode selection state machine (pure function over status enum) | Implemented |
| Storage preflight bitrate-vs-benchmark comparison logic | Implemented |

## 2. Build tests

| Test | Status |
|---|---|
| `./gradlew assembleDebug` (OIWCamera) | Verify locally — Gradle wrapper included |
| `./gradlew assembleDebug` (OIWLauncher) | Verify locally |
| `./gradlew testDebugUnitTest` both modules | Verify locally |
| Kernel config fragment merge dry-run (`docs/BUILD_SYSTEM.md` §1) | **User action** — requires kernel source checkout |
| Kernel CI clean build with fragment merged | **User action** |
| Signing config resolves for `release` variant (with user-supplied keystore) | **User action** |

## 3. Boot tests

| Test | Status |
|---|---|
| First boot after SukiSU Ultra kernel flash | **User action** |
| Reboot / recovery boot / stock recovery access | **User action** |
| Boot-to-camera (OIWLauncher default) | **User action** |
| No-SIM / airplane-mode boot | **User action** |
| Low-battery boot | **User action** |

## 4. Camera tests

| Test | Status |
|---|---|
| Enumerate + preview every reported camera ID | **User action**, app supports it once run |
| Still capture (YUV/JPEG) | **User action** |
| RAW still capture (only if `RAW_SENSOR` reported) | **User action**, conditionally available |
| RAW **video** streaming attempt (expected to fail — confirms `docs/CAMERA_PIPELINE.md` §3) | **User action** |
| Manual ISO / shutter / WB / focus round-trip via `TotalCaptureResult` | **User action** |
| High-fps (constrained high-speed session) capture | **User action**, conditionally available |
| Stabilization (OIS/EIS) mode toggle | **User action** |
| Vendor tag enumeration | **User action** via `tools/camera_capability_dump.py --vendor-tags` |
| Stream-combination legality check (`isSessionConfigurationSupported`) before every session build | Implemented (defensive check in `CameraController`) |
| Camera-service crash recovery (kill `cameraserver`, confirm app recovers/reports clearly) | **User action** |

## 5. Recording tests

| Test | Duration | Status |
|---|---|---|
| Short clip | 1 min | **User action** |
| Medium clip | 10 min | **User action** |
| Long clip | 30 min | **User action** |
| Long take | 60 min | **User action**, exercises segmentation (§Storage) |
| External SSD clip | any | **User action** |
| Low-storage clip (near-full device) | any | **User action**, exercises preflight refusal |
| Unsafe unplug (external SSD yanked mid-record) | any | **User action**, exercises crash-recovery scan |
| Thermal-warning-triggered clip | any | **User action**, exercises graceful-stop state machine |
| App force-kill mid-recording, relaunch, confirm recovery prompt | any | **User action** |

## 6. Storage tests

| Test | Status |
|---|---|
| Internal write benchmark | Implemented (`tools/storage_benchmark.sh`) |
| External SSD write benchmark | Implemented (same script, different target path) |
| Filesystem mount detection (exFAT/ext4) | Implemented (`StorageManager.getStorageVolumes()` check) |
| File-close integrity (no truncated MP4 after normal stop) | **User action**, verify with `ffprobe`/`MediaExtractor` |
| Hash verification (`--verify-hash`) | Implemented (`tools/metadata_validate.py`) |
| Dropped-frame correlation with write-speed dips | **User action**, cross-reference in-app indicator vs benchmark log |

## 7. Thermal tests

| Test | Status |
|---|---|
| Idle baseline | **User action** (`tools/thermal_monitor.sh`) |
| Preview-only | **User action** |
| 4K recording | **User action** |
| Max-bitrate recording | **User action** |
| External SSD recording | **User action** |
| Charging while recording | **User action** |
| Max brightness vs dimmed | **User action** |
| With/without external cooling accessory | **User action** |

## 8. Audio tests

| Test | Status |
|---|---|
| Internal mic sync | **User action** (clap test, §Audio Timecode) |
| USB mic sync | **User action** |
| Clipping detection | Implemented (unit test on synthetic clipped samples) + **user action** for real-world confirmation |
| Sample-rate/bit-depth negotiation | Implemented (runtime capability query) |
| Round-trip latency measurement | **User action** |
| Long-take drift | **User action** |

## 9. UI tests

| Test | Status |
|---|---|
| Record start/stop (touch + hardware button) | Implemented (instrumented test using `Espresso`/`UiAutomator` stub) + **user action** on real hardware |
| Accidental-touch lock behavior | **User action** |
| Button remapping applies correctly | Implemented (unit test on mapping resolution logic) |
| Profile switching updates all dependent UI (bitrate/codec/etc. shown) | Implemented (instrumented test) |
| Error display shows specific message for each simulated failure path | Implemented (unit test per §Error UX message) |
| External monitor mode (mirrored/clean/overlay) | **User action**, hardware-dependent (`assumptions.md` A-5) |
| Screen rotation lock in rig/gimbal mode | **User action** |
| Brightness lock during recording | **User action** |

## 10. Regression matrix

Before every tagged release: re-run full Camera + Recording + Storage + Thermal + Audio user-action suites on the
reference unit and diff results against the previous release's log — a regression is any test that passed before and
now fails or shows materially worse numbers (write speed, thermal runway, dropped frames).
