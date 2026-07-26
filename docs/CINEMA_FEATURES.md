# OIW-ROM Cinema Features

Every filmmaker-facing feature, UI behavior, and workflow. Implementation status is marked per feature; unmarked
items are implemented in the `OIWCamera`/`OIWLauncher` skeletons in this repo.

## 1. UI behavior

See `docs/UI_UX.md` for the full layout spec. Summary: top status bar (res/fps/codec/bitrate/color/storage/time/
battery/thermal), left rail (ISO/shutter/WB/focus/aperture-metadata), right rail (record/still/profile/monitor/
settings), bottom bar (audio meters/timecode/dropped-frames/LUT state/stabilization state).

## 2. Monitoring tools

| Tool | Status | Notes |
|---|---|---|
| Histogram | Implemented | Real luma histogram computed from `ImageReader` YUV planes |
| Zebra | Implemented | Real luma-threshold overlay, threshold configurable 0–255 |
| Focus peaking | Implemented | Real Sobel-edge-magnitude overlay on luma plane |
| False color | Implemented (burn-down pass: `FalseColorOverlay`, IRE-banded, unit-tested, rendered by `OverlayView`, off by default per profile) | Standard exposure-band convention |
| Waveform (luma) | **Implemented + wired + benchmarked** — `WaveformOverlay` -> `OverlayView`, enabled via a profile's `monitoringOverlays: ["waveform"]` | **Correction:** the earlier ">80 ms/frame, needs GPU" note was an *unmeasured assumption* and was wrong. Measured **1.33 ms/frame** at 960x540 (quarter-res of 4K) on JVM; a regression test enforces a 15 ms budget. GPU path is an optimization, not a prerequisite. |
| RGB parade | **Not built** — now genuinely cheap: chroma extraction exists, so this is a per-channel reuse of `WaveformOverlay` | Same column-histogram as `WaveformOverlay`, run per channel |
| Vectorscope | **Implemented + wired + benchmarked** — `VectorscopeOverlay` fed by real chroma extraction (`ChromaExtraction`, pixelStride-aware); density grid + out-of-gamut fraction + centre-bias WB readout; enabled via `monitoringOverlays: ["vectorscope"]` | Measured **0.64 ms/frame** on 480x270 chroma; 10 ms budget enforced by test |
| LUT preview | **library — not wired.** Parser + trilinear sampling unit-tested; needs the GL shader path (`tools/unwired_allowlist.txt`) | `.cube` parser + trilinear sampling are real and unit-tested; applying to the preview needs the GL pipeline |
| Anamorphic desqueeze preview | Implemented (burn-down pass: non-uniform `TextureView.setTransform` from profile squeeze) | Preview-only; recorded geometry untouched |
| Frame guides / safe area | Implemented | Configurable aspect-ratio and title/action-safe overlay boxes |
| Dropped-frame indicator | Implemented (burn-down pass: counter + status-bar indicator + explicit warning message) | |
| Rolling/remaining record time | Implemented | From current bitrate × free space, updated every 2s |
| Write-speed indicator | Implemented | Rolling average of bytes written / wall time from the segment writer |
| Thermal headroom indicator | Implemented | Derived from `ThermalMonitor` mode + trend (see `docs/THERMAL_POWER.md`) |
| Audio meters | Implemented | Peak + RMS from `AudioRecord` buffer, dBFS scale |
| Clipping indicator | Implemented | Audio: sample-value clipping count; Video: histogram bins at 0/255 |
| Timecode display | **Implemented + wired** — LTC decoded from the audio input (`LtcDecoder` on `AudioCapture.pcmSink`, enabled by `monitoringOverlays: ["timecode"]`) and shown live in the status bar | Tiers per `docs/AUDIO_TIMECODE.md` |
| Slate metadata screen | **Sidecar fields exist; no slate UI yet** (stub-audit correction) | Fields in `MetadataWriter.ClipMetadata` are real; the entry screen is unbuilt |

## 3. Button mappings

Default map (`configs/button_mappings/default.json`), user-editable:

| Input | Action |
|---|---|
| Volume Up (short) | Start/stop recording |
| Volume Down (short) | Still capture / marker |
| Volume Up (long ~800ms) | Cycle capture profile |
| Volume Down (long ~800ms) | Toggle focus-assist (peaking) |
| Double-tap preview | Hide/show overlays |
| Two-finger tap | Screen lock (recording continues) |
| Three-finger tap | Emergency stop (immediate safe-stop + finalize file) |
| Power (short, while recording) | Lock screen only — **never** stops recording |
| Power (long) | System power menu, with an on-screen "recording in progress" warning dialog requiring explicit confirmation |
| USB HID keyboard `R` | Start/stop recording (rig/external-control alias) |
| USB HID keyboard `Space` | Still capture |

Rationale for power-button safety: many accidental-stop bugs in camera apps come from power button double-mapping;
OIW-ROM deliberately makes the power button inert with respect to recording state.

## 4. Storage strategy

See `docs/STORAGE_MEDIA.md`. Per-profile storage target (internal `/sdcard/OIW_MEDIA` or external SSD via SAF/root
path), preflight benchmark gate, folder layout `/OIW_MEDIA/<project>/<date>/<cam>/<clip>/`.

## 5. Audio sync

See `docs/AUDIO_TIMECODE.md`.

## 6. LUT workflow

- Import: user drops a `.cube` file into `/OIW_MEDIA/LUTs/` or imports via SAF file picker.
- `configs/lut/index.json` catalogs available LUTs with a display name, source path, and intended use tag
  (`technical`, `creative`, `desqueeze-neutral`).
- LUTs are **preview-only by default** (applied to the `TextureView` overlay shader). "Bake into recording" is a
  separate, explicit, off-by-default toggle in Settings, and every recorded clip's sidecar always records which LUT
  (if any) was active and whether it was baked or preview-only — never silent.
- Gray card / color chart calibration workflow: capture a reference still under the LUT-off, flat-profile-on state;
  user visually confirms neutral gray in the histogram (R≈G≈B peak alignment) as a lightweight in-field WB sanity
  check. Full spectrophotometric calibration is out of scope without an X-Rite-class reference and desktop tooling.

## 7. External monitor behavior

- **PRIMARY TARGET (aurora / Xiaomi 14 Ultra, retarget 2026-07-06): native DP Alt Mode IS available** — USB 3.2
  port with DisplayPort Alt Mode (14-series confirmed; verify on-unit per `assumptions.md` E-1). OIWCamera's
  `Presentation`-based clean-feed/overlay-feed modes run natively on the wired external display, and USB 3.x also
  lifts the external-SSD bandwidth ceiling. The rest of this section documents the **legacy nuwa target**:
- **nuwa (13 Pro) — clean HDMI/DP-Alt-Mode output: CONFIRMED NOT POSSIBLE on that hardware** — the Xiaomi 13 Pro's USB-C port is
  USB 2.0 with no DP Alt Mode wiring (`assumptions.md` A-5, verified against community sources 2026-07-06; the 13
  Ultra was Xiaomi's first DP-alt-mode phone). This is a hardware limit, so instead of a dead end, the design ships
  three real workarounds:
- **UVC webcam-mode output (primary workaround, Path A):** AOSP 14+ includes **DeviceAsWebcam** — the phone
  enumerates as a standard UVC webcam over its USB port. Any laptop, UVC field monitor, or capture-card-equipped
  recorder then displays the feed. The Path A LineageOS build enables this (`DeviceAsWebcam` service); on stock
  HyperOS it depends on Xiaomi having shipped the Android 14 webcam feature. Bandwidth is USB 2.0, so this is a
  monitoring feed, not a clean 4K master — label it as such in the UI.
- **scrcpy over USB (zero-install-on-phone workaround):** low-latency full-resolution mirroring to any host laptop
  via ADB; works today on stock HyperOS, no root needed. Documented as the DIT-cart/video-village option.
- **Wireless cast fallback:** Miracast/Google Cast mirroring where a receiver is available; highest latency of the
  three, acceptable for director's-eyeline only, not focus judgment.
- When any external feed is active, OIWCamera offers clean-feed vs overlay-feed framing via its `Presentation`
  path where the display is an Android `Display` (cast), and via the preview-surface-only layout when the consumer
  is UVC/scrcpy (which mirror the panel).
- Display sleep is disabled (`FLAG_KEEP_SCREEN_ON`) during any active recording session, on both internal and mirrored
  external displays.
- Notifications are suppressed system-wide during recording via `NotificationListenerService`-driven Do Not Disturb
  activation (public API, no framework patch needed), restored automatically on stop.

## 8. Rigging assumptions

See §3 button mappings, and `docs/UI_UX.md` for glove-friendly touch target sizing (minimum 48dp, cinema controls
sized to 64dp), stealth/dim UI mode, and gimbal-mode considerations (locked orientation option to prevent the OS from
rotating UI when the rig itself is inverted/side-mounted).

## 9. Lens metadata mode

Implemented as a form + sidecar fields: lens name, focal length, aperture (manual, since adapted lenses have no
electronic contact), adapter, speed booster/focal reducer factor, anamorphic squeeze, filter stack (free-text list +
ND value), focus notes, calibration notes, distortion profile (free-text reference to an external correction LUT/grid
if the user has one), crop mode, sensor mode. All fields are optional and persist per capture profile as defaults,
overridable per clip.

## 10. Anamorphic mode

Squeeze presets: 1.33×, 1.5×, 1.6×, 1.8×, 2.0×, custom (free entry). Desqueeze is preview-only (§2); frame guides
account for the desqueezed aspect ratio; sidecar always records the squeeze factor used so the edit-bay desqueeze
step is unambiguous.

## 11. IR / experimental imaging mode

Documented for **hardware-modified units only** (IR-converted sensor or external IR-pass filter). Because the stock
`nuwa` sensor has a standard IR-cut filter, this mode is inactive/hidden by default and only surfaces once the user
explicitly enables "Modified hardware — IR/full-spectrum" in Settings, which unlocks: IR-appropriate false-color LUT,
manual WB presets tuned for IR-heavy scenes, exposure-compensation presets, and a mandatory on-screen safety note that
autofocus/manual-focus markings shift under IR and must be re-calibrated per lens/filter combination.

## 12. Low-light cinema mode

High-ISO test profile, noise-profile mapping tool (`tools/` — captures a series of dark-frame stills at each ISO step
and records per-ISO luma-noise stddev to a CSV for the user's own reference), temporal-NR toggle (`NOISE_REDUCTION_MODE`
where supported — see `docs/CAMERA_PIPELINE.md` §7), sharpening forced off/low, long-exposure still mode (bounded by
`CameraCharacteristics.SENSOR_INFO_MAX_FRAME_DURATION`).

## 13. Thermal long-take mode

See `docs/THERMAL_POWER.md` for the 7 thermal modes and graceful-stop state machine. Cinema-relevant UI: predicted
thermal-runway countdown, one-tap "Long Take Safe" profile switch, explicit warning text (never "something went
wrong") when approaching a forced-stop threshold.
