# OIW-ROM Test Plan

Every test below is either **automated** (unit/instrumented test in the app's test source set) or a **user action**
(requires real hardware; cannot be executed in this environment). Status column reflects what exists in this change.

## 0. Offline verification harness (`tools/verify_compile.sh`)

Runs without the Android SDK (Google Maven is blocked in some environments). Four tiers, ordered
by strength of evidence — **the tier labels matter, don't conflate them**:

| Tier | What | Strength |
|---|---|---|
| 1 | Pure-JVM logic — compiled **and executed** (69 tests) | Strongest |
| 1.5 | Real Android framework code **executed** on the JVM under Robolectric 4.12.2 (72 tests) | Runs — but in a *simulated* Android runtime, not on the device |
| 2 | Compiled against **real** Android API 34 (`org.robolectric:android-all`) — 93 classes | Compiles, never runs |
| 3 | Compiled against **hand-written androidx/AGP stubs** — 39 classes | Weakest: a wrong stub signature could mask a real error. Report as *plausible*, not *verified*. |

Plus `tools/check_wiring.py`, which fails the build if any `main/` class is referenced only by its
own tests or by comments — the mechanical guard against the dead-code pattern (finding P).

CI runs the whole thing on every push and PR touching `packages/apps/` or the harness
(`.github/workflows/oiw-apps.yml`), on a stock runner with no Android SDK.

### Tier 1.5 — what it does and does not buy

It executes the filesystem, `Context`, `StatFs`, and `ContentProvider` behavior that tiers 2/3 can
only type-check. That is where the last four High-severity defects lived, so it earns its keep: it
found finding T (the 8x storage-throughput unit error plus a user-visible unsubstituted format
string) on its first run.

Two limitations, stated rather than glossed:

1. **It is not the device.** Robolectric's shadows approximate the framework. A tier-1.5 pass means
   "the logic behaves correctly against a simulated Android," never "this works on the phone."
2. **App assets/resources are unavailable in the offline harness.** Robolectric 4.12 runs in BINARY
   resources mode and wants an aapt2-built resource APK, which needs the Android SDK. So
   `context.assets` is empty here and the *bundled-asset* branches of `ProfileRepository` /
   `ButtonMappingLoader` are not exercised by tier 1.5. They are covered instead by
   `BundledProfileAssetsTest` (tier 1), which parses every shipped profile and mapping straight off
   disk with the same Gson types — and by `./gradlew testDebugUnitTest`, where AGP supplies real
   assets and this same test class runs strictly stronger.

Robolectric needs `androidx.test:monitor`, which lives only on Google's Maven (blocked here with a
403). `tools/robolectric_shim/` supplies the 28-class API surface Robolectric actually calls; see
that directory's README for why a wrong shim fails loudly instead of mis-verifying app code, and why
Gradle's result is authoritative when the two disagree.

**What none of this proves:** device behavior. `./gradlew assembleDebug` on a machine with the
Android SDK, then a real on-device take, remain the true acceptance gates.

## 1. Unit tests

All tier-1 and tier-1.5 tests below are compiled with Kotlin 1.9.24 and **executed** on JDK 21 by
`tools/verify_compile.sh` (§0). **Current result: 69 tier-1 + 72 tier-1.5 = 141 tests pass.** The
remaining Android-framework-heavy classes (camera, codec, audio, UI) are compiled but not executed
— they are gated on `./gradlew assembleDebug` and a real device.

| Test | Status |
|---|---|
| `CaptureProfile` shutter-angle/speed math + Gson round-trip | **Compiled + passing** |
| `.cube` LUT parser (identity sample, malformed-file rejection) | **Compiled + passing** |
| `CsvExporter` (defaults, dot-path resolution, RFC-4180 quoting, non-csv rejection) | **Compiled + passing** |
| `FalseColorOverlay` (IRE band edges, distinct black/white mapping) | **Compiled + passing** |
| `ThermalMonitor` decision matrix (force-stop vs warn vs continue per mode) | **Compiled + passing** |
| `HistogramOverlay` (binning with i+=4 sampling, clip-fraction) | **Compiled + passing (new)** |
| `ZebraOverlay` (high/low threshold masking) | **Compiled + passing (new)** |
| `FocusPeakingOverlay` (Sobel edge fires; flat area quiet; threshold gates) | **Compiled + passing (new)** |
| `LtcTimecode` bit round-trip + drop-frame + sync-word position | **Compiled + passing (new)** |
| `LtcEncoder`→`LtcDecoder` full-PCM round-trip at 48k/25, 44.1k/25, 44.1k/30, 48k/29.97 DF | **Compiled + passing** |
| `WaveformOverlay` (flat/ramp/total-count + 15 ms perf budget) | **Compiled + passing** |
| `VectorscopeOverlay` (centre/quadrant bias, gamut flag + 10 ms perf budget) | **Compiled + passing** |
| `RgbParadeOverlay` + `YuvToRgb` (exact neutrality at U=V=128, per-axis channel response, clamping, column mapping, short-buffer safety + 20 ms perf budget; measured 1.15 ms) | **Compiled + passing (new)** |
| `ProfileOverlayVocabularyTest` (no unknown overlay id in any shipped profile; no implemented overlay unreachable from every profile) | **Compiled + passing (new)** |
| `Slate.applyTo` merge (slate wins where filled; **a blank field never erases a profile default**; take is authoritative; next-take/next-shot semantics; slate display line) | **Compiled + passing (new)** |
| `ChromaExtraction` (packed, row-padded, **interleaved pixelStride=2**, truncated buffer, e2e feed) | **Compiled + passing** |
| Every shipped capture profile + button mapping parses, has usable fields, and yields a legal exposure (`BundledProfileAssetsTest`) | **Compiled + passing (new)** — reads the real `assets/` JSON off disk |
| Kelvin→RGB gain math (`CameraController`) | Written; not in the pure harness (file is Android-heavy) — verify at `gradlew test` |

### Tier 1.5 — Robolectric (executed against a simulated Android runtime)

These nine classes had **never been executed by anything** before this pass; they were type-checked
only, which is exactly the gap findings C, R, T and W lived in.

| Test | Status |
|---|---|
| `OiwPaths` — media root is `/sdcard/OIW_MEDIA`, never under `/Android/data`; all derived paths stay inside it | **Executed + passing (new)** |
| `StorageManager` — benchmark write→read round trip through the file the app actually reads (finding C loop) | **Executed + passing (new)** |
| `StorageManager` — preflight refuse/accept either side of the 1.2x margin, free-space and runtime math | **Executed + passing (new)** |
| `StorageManager` — required throughput derived from **bits**, not bytes (finding T regression guard) | **Executed + passing (new)** |
| `StorageManager` — refusal message carries no unsubstituted `%.1f` placeholders (finding T regression guard) | **Executed + passing (new)** |
| `StorageManager` — clip-folder layout + sanitisation, recovery scan finds orphans and skips finalised clips | **Executed + passing (new)** |
| `MetadataWriter` — atomic tmp→rename leaves a complete sidecar and no `.tmp`; corrupt/stale files read as null | **Executed + passing (new)** |
| `SessionIndexWriter` — real session tree → `session_index.csv`, ordering, RFC-4180 escaping, template defaults | **Executed + passing (new)** |
| `StatusFileWriter` + `StatusProvider` — the OIWCamera→OIWLauncher handshake end to end, incl. the four JSON field names | **Executed + passing (new)** |
| `ProfileRepository` — user-plugin loading, one malformed file skipped and reported, id override, error clearing | **Executed + passing (new)** |
| `ButtonMappingLoader` — user override, unmapped input stays unmapped, malformed file returns null | **Executed + passing (new)** |
| `OverlayView` — flags default off, follow the profile exactly, and clear on a switch to a leaner profile | **Executed + passing (new)** |
| `OverlayView` — every scope renders to a real `Canvas` without indexing past its buffers, before any frame arrives, and in a degenerate 8x8 viewport | **Executed + passing (new)** |
| `SlateStore` — a saved slate survives a process restart; take advance persists; corrupt file falls back; unwritable path reports failure instead of throwing | **Executed + passing (new)** |
| `SlateStore` → `MetadataWriter` end to end — a slate on disk reaches the clip sidecar | **Executed + passing (new)** |

### Bug found and fixed during this audit
Careful review of `Recorder` (not compilable in the harness) found a real defect: segment rollover
happened **after** writing the triggering keyframe, so each new segment began on P-frames and was not
independently decodable until the next GOP. Fixed to roll **before** writing a keyframe
(`rolloverBeforeKeyframeLocked`), so every segment starts self-contained (verified the new muxer
re-adds the track from the saved output format, which carries the codec config/SPS-PPS-VPS).

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
