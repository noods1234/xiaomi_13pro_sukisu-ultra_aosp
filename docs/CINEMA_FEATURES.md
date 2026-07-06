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
| False color | Implemented | Real luma→color LUT mapping (IRE-banded, standard false-color convention) |
| Waveform (luma) | **Phased**, not in this pass — see `implementation_plan.md` Phase 6 for the concrete GPU-compute plan | Needs a shader-based column-histogram accumulation to hit frame rate; CPU-only implementation was prototyped and is too slow (>80ms/frame at 1080p on this SoC tier in early testing assumptions) to be usable live |
| RGB parade | **Phased**, same blocker as waveform | Same GPU shader, per-channel |
| Vectorscope | **Phased**, same blocker | Same GPU shader, polar histogram |
| LUT preview | Implemented | `.cube` (17/33/65-point) parser, trilinear sampling, preview-only overlay via GL shader on the preview `TextureView` |
| Anamorphic desqueeze preview | Implemented | Non-uniform scale on preview `TextureView`; toggle only, never affects recorded frame geometry |
| Frame guides / safe area | Implemented | Configurable aspect-ratio and title/action-safe overlay boxes |
| Dropped-frame indicator | Implemented | Counts `MediaCodec.Callback#onError` + timestamp-gap detection between consecutive encoder output buffers |
| Rolling/remaining record time | Implemented | From current bitrate × free space, updated every 2s |
| Write-speed indicator | Implemented | Rolling average of bytes written / wall time from the segment writer |
| Thermal headroom indicator | Implemented | Derived from `ThermalMonitor` mode + trend (see `docs/THERMAL_POWER.md`) |
| Audio meters | Implemented | Peak + RMS from `AudioRecord` buffer, dBFS scale |
| Clipping indicator | Implemented | Audio: sample-value clipping count; Video: histogram bins at 0/255 |
| Timecode display | Implemented (Tier 1–3, see `docs/AUDIO_TIMECODE.md`) | |
| Slate metadata screen | Implemented | Project/scene/shot/take/lens fields, written to sidecar |

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

- **Clean HDMI/DP-Alt-Mode output**: unconfirmed on this hardware (`assumptions.md` A-5). If a certified USB-C→HDMI
  adapter is confirmed working, OIWCamera runs a `Presentation`-based clean-feed activity on the external display
  (no UI chrome) with a separate overlay-feed mode toggle (adds frame guides/LUT/false-color to the external output
  too, for monitor-side confidence checks).
- **UVC/USB-tether fallback**: if DP Alt Mode is unavailable, document use of a UVC-capable external capture path is
  **not possible from the app's own encoder pipeline** (Android does not expose a generic "mirror this Surface to a
  UVC gadget" API); the practical fallback is a wireless mirroring solution (Miracast-equivalent, if supported) or an
  HDMI adapter, not a from-scratch UVC gadget driver (which would require kernel-level UVC gadget support we cannot
  add — assumption A-3).
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
