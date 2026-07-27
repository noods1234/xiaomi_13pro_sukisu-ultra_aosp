# OIW-ROM Critical Audit — 2026-07-06

Adversarial re-read of the OIWCamera code (beyond the earlier stub audit), looking for real defects.
Severity: **High** (blocks or corrupts core function), **Medium** (wrong behavior / leak), **Low** (latent / hardening).

## Fixed in this pass

| # | Sev | Finding | Fix |
|---|---|---|---|
| A | High | **A/V sync drift.** Audio AAC PTS used `System.nanoTime()` sampled *at each read*, folding the audio-buffer latency into a jittery, uneven timeline while video PTS came from the camera surface clock. | `AudioCapture` now derives PTS from a monotonic **sample counter anchored at the first buffer** — exactly-spaced, drift-free. Remaining device-dependent caveat (nanoTime vs camera `SENSOR_INFO_TIMESTAMP_SOURCE == REALTIME`) is now called out as a bring-up check, not silently assumed. |
| B | High | **Frame rate never locked.** `buildManualRequest` set exposure/ISO/AWB-off but never `SENSOR_FRAME_DURATION`. With AE off the sensor cadence is undefined — a 24p profile could capture at ~30fps (wrong motion cadence). | Sets `SENSOR_FRAME_DURATION = 1e9/fps`, clamped ≥ exposure and ≤ `SENSOR_INFO_MAX_FRAME_DURATION`, with an explicit warning if adjusted. |
| C | High | **Recording could never start in the default flow.** `storage_benchmark.sh` prints JSON to *stdout*; the app read `OIW_MEDIA/benchmarks/<target>.json`, which nothing ever created → `preflight` always refused. | Added a real in-app `StorageManager.runAndStoreBenchmark()` (buffered write + `fsync`, writes the file the app reads). `CameraActivity` benchmarks on first record if none exists, then preflights. `preflight` stays pure/testable (refuses on truly-absent data; caller supplies it). |
| D | Medium | **Anamorphic desqueeze pivot wrong.** `applyDesqueeze` used `textureView.width` at wire time (0 before layout), offsetting the stretch instead of centering it. | Deferred to `textureView.post { … }` so the pivot uses the laid-out width. |
| G | Medium | **Thermal-poll leak.** The 5-s `thermalTick` Runnable was never cancelled in `onDestroy` — leaked the Activity and kept firing after teardown. | `removeCallbacks(thermalTick)` + `coordinator.release()` + `analysisThread.quitSafely()` in `onDestroy`. |
| H | Medium | **Profile-switch resource leak.** `cycleProfile` → `wireSessionWhenSurfaceReady` created a new `HandlerThread` and added another `OverlayView` to the container every switch, never releasing the old ones (stacked overlays, thread leak). | Re-wire now `quitSafely()`s the prior analysis thread and `removeAllViews()` on the overlay container first. |

## Found, NOT yet fixed (flagged honestly — tracked, not hidden)

| # | Sev | Finding | Plan |
|---|---|---|---|
| ~~I~~ | Medium | **FIXED.** Benchmark moved to `Dispatchers.IO` via `lifecycleScope`, record button disabled while measuring so a double-tap can't start two benchmarks. |
| ~~J~~ | Medium | **FIXED.** `LtcEncoder` rewritten with phase-accurate fractional bit timing (anchored to a global bit counter, drift-free); encoder/decoder `frameRate` is now `Double`. Round-trip tests pass at 44100/25, 44100/30, and 48000/29.97 drop-frame. |
| ~~K~~ | Low | **FIXED.** `open()` guards all three StateCallback paths with an `AtomicBoolean` (also fixes a latent hang: `onDisconnected` during open never resumed the coroutine before). |
| ~~L~~ | Low | **FIXED.** Pairing token compared with `MessageDigest.isEqual` (constant-time). Verified compiles in the pure-logic harness. |
| M | Low | `StatusProvider` is `exported=true` with no permission — any app can read storage/thermal/battery/profile summary. Non-sensitive by design, but it is world-readable. | Acceptable for v1; documented. Could gate behind a signature permission shared with OIWLauncher. |
| N | Low | `kelvinToGains` is a coarse approximation (documented as such), not colorimetrically validated; green channel handling is simplistic. | Validate against a gray card on real hardware (Phase 1 recon); consider a measured per-sensor matrix. |

## Findings against my own prior claims

| # | Sev | Finding | Resolution |
|---|---|---|---|
| O | Medium | **I asserted a performance number I never measured.** `docs/CINEMA_FEATURES.md` claimed a CPU waveform was ">80 ms/frame at 1080p" and therefore required a GPU compute path, presented as if benchmarked ("prototyped"). It was not — it was a guess that justified deferring the feature. | Wrote the real implementation and **measured**: 1.33 ms/frame at 960x540 (waveform), 0.64 ms/frame at 480x270 chroma (vectorscope) — off by roughly 50x. Both features now shipped on CPU with budget-enforcing regression tests. Docs corrected to mark the old claim as wrong rather than quietly deleting it. Lesson recorded: perf claims get a benchmark or an explicit "unmeasured" label. |

| P | **High (process)** | **Dead-code pattern, 3rd recurrence.** Seven components were built, unit-tested, documented "Implemented", and wired to nothing (`WaveformOverlay`, `VectorscopeOverlay`, `LtcEncoder`, `LtcDecoder`, `DngStillCaptor`, `CsvExporter`, `ExternalControlServer` — plus `CubeLutParser`, which three manual audits all missed). Root cause: the pure-JVM harness can only prove algorithms, so algorithms kept getting built while integration stayed unproven — optimizing for the measurable over the valuable. | **Mechanically fixed, not promised.** `tools/check_wiring.py` fails the build when a `main/` class has no reference from production code. Deliberately strict: test-only references and comment mentions do **not** count, because both were real false-negatives found while building it. Wired waveform/vectorscope/LtcDecoder/CsvExporter; the remaining four are in `tools/unwired_allowlist.txt` with a specific named blocker each. |
| Q | High | **Shipped a component that could not possibly work.** `VectorscopeOverlay` consumes U/V chroma, but nothing in the codebase extracted chroma — `LumaFrame` read `planes[0]` only. It was marked "Implemented (CPU) + benchmarked" in the same commit. A unit test on synthetic input passed happily, which is exactly why unit tests did not catch it. | Added `LumaFrame.extractChroma` + `ChromaExtraction.readPlane` with correct `pixelStride` handling (chroma is interleaved on most devices — reading contiguously would have produced a plausible but wrong scope). 6 tests incl. an end-to-end neutral-gray feed. |
| R | Medium | **64% of production classes had never been compiled by any compiler** (23/36), including the whole capture path and all six audit fixes from the previous round. "Reviewed" was doing work that only a compiler can do. | `tools/verify_compile.sh` type-checks everything against real Android API 34 (`android-all` from Maven Central). It immediately found a genuine build-breaker (nested-comment bug in `ProfileRepository`). Evidence tiers are labelled so tier-3 (androidx stubs) is never mistaken for verification. |

## Round 3 — found by the Robolectric execution tier (2026-07-26)

Adding tier 1.5 (real Android framework code executed on the JVM) was itself the audit: three
defects fell out, two of them in the first run of code that had type-checked cleanly for weeks.

| # | Sev | Finding | Fix |
|---|---|---|---|
| S | **High (process)** | **A failing test suite could not fail the harness.** `tools/verify_compile.sh` ran the tier-1 tests as `java … \| tail -6`. A pipeline's exit status is the *last* command's, and `tail` always succeeds — so every red suite would have been reported green. The harness built to stop unverified claims was itself making one. | Both test tiers now run with output captured and `$?` checked explicitly; the script exits non-zero on failure and prints the log path. Verified by construction (the very next run caught finding T instead of swallowing it). |
| T | **High** | **Storage preflight demanded 8x the throughput it should, and told the user so with raw format placeholders.** Two bugs in one branch: (1) `requiredMbps = requiredBitrateBps / 1_000_000.0` treats a **bits**/s bitrate as mega**bytes**/s, while both benchmark producers report MB/s — the 180 Mbps 4K profile therefore refused any card slower than **216 MB/s** when it actually needs ~27 MB/s, i.e. it would refuse to record on perfectly adequate media. (2) `"…%.1f…" + "…".format(a, b)` binds `.format` to the *second* literal only, so the refusal reached the user as the literal string `Measured write speed %.1f MB/s is below the %.1f MB/s required` — the exact "never show the user a broken message" rule in docs/UI_UX.md. | Bitrate converted bits→bytes; format call parenthesised. `BenchmarkResult.sustainedWriteMbps` renamed to **`sustainedWriteMBps`** because the ambiguous casing *was* the root cause; the JSON key is written under both names and read under either, so older benchmark files still load. `tools/storage_benchmark.sh` updated to match. Two regression tests pin each half. |
| U | Low | **`ChromaExtraction`/scope work was verified only on synthetic input** — same shape as finding Q. Not a new defect, but tier 1.5 does not reach it either (no `Image`/`ImageReader` under Robolectric). | Unchanged and still open by design: it needs a device frame. Recorded here so it is not mistaken for covered. |

Worth stating plainly: findings S and T are both cases where **review had already passed over the
code and seen nothing**. A compiler cannot catch a unit mismatch between two `Double`s, and no
amount of reading catches a shell pipeline's exit status. Execution caught both within minutes.

## Round 4 — found while building the RGB parade (2026-07-26)

| # | Sev | Finding | Fix |
|---|---|---|---|
| V | Medium | **The parade's colour matrix is an assumption, and is labelled as one.** `YuvToRgb` uses BT.601 full-range, the conventional reading of `YUV_420_888`. Android exposes no way to query the stream's actual encoding, so on a device delivering limited-range or BT.709 chroma the traces will sit off where a hardware scope would put them. | **Not "fixed" — bounded and disclosed.** It affects monitoring only; nothing here touches recorded pixels. The conversion is isolated in one testable object with an exact-neutrality test (U=V=128 must give R=G=B=Y, so a grey frame can never show a false cast). Verification against a colour chart is a Phase-1 hardware task. The docs say "not colorimetrically calibrated" rather than implying otherwise. |
| W | **High** | **`cinema_stealth_street` drew four overlays it never asked for.** Every `OverlayView` flag (`showHistogram`, `showZebra`, `showPeaking`, `showGuides`) defaulted to `true` and only `false_color`/`waveform`/`vectorscope` were read from the profile. So the one profile whose entire purpose is an unadorned frame — the profile you pick when you must not draw attention — still painted a histogram, zebra, peaking and guides over the preview. | All flags default **off**; a single `OverlayView.applyProfileOverlays` drives every one of them from `MonitoringOverlays.viewFlags`. Pinned by a pure-JVM test on the mapping and a Robolectric test on a real `OverlayView`, including that switching to a leaner profile turns the old overlays **off** (stale state across a profile switch was the other half of this). |
| X | **High (process)** | **A feature can be fully "wired" and still unreachable.** `WaveformOverlay` and `VectorscopeOverlay` were built, unit-tested, benchmarked, hooked into the analysis path, and documented "Implemented + wired" — and **no shipped capture profile listed them**, so no user could ever switch one on. `tools/check_wiring.py` passed, correctly: the *code* was referenced. This is finding P's pattern one level up — the gap moved from code to config. | `ProfileOverlayVocabularyTest` (tier 1) now fails the build in both directions: any overlay id a profile names must be in `MonitoringOverlays.ALL`, and every id in `ALL` must be enabled by at least one shipped profile. Profiles updated so waveform/vectorscope/parade are actually reachable. A misspelled id now also raises an on-screen error instead of silently doing nothing. |

The lesson from X is worth keeping separate from the fix: **"wired" was being measured against the
compiler, not against the user.** A guard that asks "is this class referenced?" cannot see a feature
that no shipped configuration turns on. The new check asks the question that actually matters — can
someone reach this? — and it is the config, not the code, that has to answer.

## Round 5 — found while building the slate UI (2026-07-26)

| # | Sev | Finding | Fix |
|---|---|---|---|
| Y | Medium (process) | **The harness's tier split was a hardcoded filename list.** `verify_compile.sh` sent everything except two named files to tier 2 (android-all, no androidx). The first new androidx-using Activity therefore landed in tier 2, failed to compile against a classpath that has no `AppCompatActivity`, and took the entire run down — a harness failure masquerading as a code failure, on correct code. | The split is now derived from the source: any file importing `androidx.*` or referencing the AGP-generated `R` goes to tier 3, and tier 3 is the exact complement. Adding an Activity no longer requires remembering to edit the harness. Tier 3 also now fails if it ends up with no sources, so a filter mistake cannot silently skip a whole tier. |
| Z | Low | **A CSV assertion was pinned to column indices.** `SessionIndexWriterRobolectricTest` checked `columns[16]` and `columns[18]`; adding the `Circled` column shifted every field after `Take`, so a correct change broke unrelated assertions. That trains you to bump the numbers instead of reading the test. | Columns are resolved by header name, and the header is asserted as a set of required names rather than a prefix string. |

## Method / status

`tools/verify_compile.sh` is green as of 2026-07-26: **132 classes compiled** against real Android
API 34, **141 tests executed** (69 pure-JVM + 72 Robolectric), wiring guard clean. It runs in CI on
every push and PR touching the apps or the harness (`.github/workflows/oiw-apps.yml`).

Scope discipline for anything recorded here: tiers 1/1.5 mean *executed on a JVM*; tier 1.5 is a
**simulated** Android runtime, not the device. Camera, codec, audio-capture and UI paths remain
compile-only and are gated on `./gradlew assembleDebug` plus a real on-device take.
This document is the standing defect register — new findings append here rather than being silently fixed.
