# OIW-ROM Camera Pipeline

## 1. Sensor pipeline (assumed, to be confirmed by capability dump)

Main sensor: Sony IMX989, 1"-type, ~50MP quad-bayer, likely exposed as Camera ID `0` with a logical/physical camera
split for the ultrawide/telephoto array. **Do not trust this section's numbers until `tools/camera_capability_dump.py`
has been run against the real unit** — see `assumptions.md` A-1/A-7.

Required first output (`camera_dump.json`) must capture, per camera ID: active array size, pixel array size,
available stream configurations, exposure/ISO/frame-duration ranges, `availableCapabilities`, hardware level, vendor
tag namespace list, dynamic range profiles (if `ANDROID_REQUEST_AVAILABLE_DYNAMIC_RANGE_PROFILES` present on this API
level), lens facing, OIS/EIS availability, and black level pattern if reported.

## 2. Camera HAL strategy — access tiers

| Tier | Description | Feature ceiling | Risk | Effort | Fallback |
|---|---|---|---|---|---|
| 1 | Standard Camera2 only | Manual exposure/ISO/WB/focus, YUV recording, stills | Low | Low | N/A (baseline) |
| 2 | Camera2 + published vendor tags (`android.hardware.camera2.CameraCharacteristics#getAvailableCaptureRequestKeys` including `com.xiaomi.*`/`com.qti.*` namespaces) | Possible vendor log/flat profile, extra noise-reduction/EIS controls | Low | Low-medium | Falls back to Tier 1 controls |
| 3 | Vendor camera **extensions** (`CameraExtensionCharacteristics`, e.g. Night/Bokeh vendor sessions) | Vendor-tuned modes, but usually **less** manual control, not more — extensions trade control for automation | Medium (extensions can silently apply non-deterministic processing, which conflicts with our determinism requirement) | Medium | Avoid for cinema profiles; only use for reference/comparison captures |
| 4 | HAL shim/wrapper (interpose on the camera provider service) | Theoretically highest short of kernel access | **High** — can destabilize `cameraserver`, breaks on every HyperOS update, edges into HAL tamper territory | Very high | Documented as research-only in `docs/ARCHITECTURE.md` §2, not implemented |
| 5 | Kernel/V4L2 direct sensor access | Raw sensor register-level control | **Blocked** — no `nuwa` kernel source with camera sensor driver available to us (assumption A-3); stock camera driver is proprietary and loaded as a vendor kernel module we cannot rebuild | N/A | N/A |
| 6 | Experimental direct RAW pipeline | True sensor RAW/CinemaDNG | **Blocked** for same reason as Tier 5, plus C-3 | N/A | N/A |

**Implemented in OIWCamera: Tiers 1 and 2.** Tier 3 is exposed as an explicit, clearly-labeled "vendor mode
(non-deterministic)" toggle, off by default, and never used inside a capture profile meant for graded footage.

## 3. RAW stream support

`OIWCamera`'s `CameraController` requests `CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES` and checks for
`REQUEST_AVAILABLE_CAPABILITIES_RAW`. If present: expose a "RAW DNG still" capture mode using
`ImageFormat.RAW_SENSOR` + Android's built-in `DngCreator` (collects black level, white level, color transform,
calibration illuminant, and forward matrix automatically from `CameraCharacteristics`/`CaptureResult`, so we do not
hand-roll DNG metadata math). If absent: the RAW still button is **hidden**, not grayed-out-and-lying — the UI must
never offer a control for a capability the hardware doesn't report (see `docs/UI_UX.md` §Error UX).

RAW **video** (a live RAW_SENSOR stream at full frame rate to storage) is not supported by any known stock Xiaomi
Camera2 implementation at this device tier — even where RAW_SENSOR is reported, it is typically limited to
still-capture stream configurations, not sustained high-fps video streaming, due to ISP/memory bandwidth. This is
investigated, not assumed impossible: `docs/TEST_PLAN.md` §Camera Tests includes an explicit RAW-video streaming
attempt; if it fails (expected), the failure reason (rejected stream configuration) is logged verbatim in the app's
debug panel per the "no vague errors" UX rule.

## 4. YUV stream support

Primary recording path: `ImageFormat.YUV_420_888` (via `ImageReader`, used for overlay analysis — histogram/zebra/
focus peaking) run in parallel with a `Surface`-backed encoder input (`MediaCodec.createInputSurface()`) for the
actual recorded stream, using a `MultiResolutionImageReader`-free dual-target `CaptureRequest` (preview + encoder
surface + analysis surface, validated against `isSessionConfigurationSupported` first — never assume a 3-target
config is legal, always confirm before building a session).

## 5. High-speed stream support

Query `CameraCharacteristics.CONTROL_AVAILABLE_HIGH_SPEED_VIDEO_CONFIGURATIONS`. If populated, expose 120/240fps
slow-motion capture profiles using `CameraConstrainedHighSpeedCaptureSession`. High-speed sessions have a fixed,
narrow exposure/ISO range mandated by the platform — the manual control panel must clamp to the reported range for
that specific high-speed configuration rather than showing the full normal-mode range (a common source of rejected
capture requests).

## 6. Vendor extension handling

See Tier 3 above. `CameraExtensionSession` (Night/HDR/Bokeh/Face-retouch vendor extensions) are enumerated for
diagnostic/debug purposes only (`docs/API_PLUGIN_FRAMEWORK.md` debug exporter) and never wired into a cinema capture
profile, because extension sessions do not guarantee deterministic, repeatable output frame-to-frame — which
violates the "no hidden automatic scene changes" acceptance criterion in the root prompt's §7.2.

## 7. ISP constraints

Everything downstream of the sensor (demosaic, lens shading correction, tone mapping, noise reduction, edge
enhancement) happens in the proprietary `camx`/ISP pipeline we cannot rebuild (Tier 5/6 blocked). The only levers
Camera2 exposes into that pipeline: `NOISE_REDUCTION_MODE`, `EDGE_MODE`, `TONEMAP_MODE` (+ curve, if
`TONEMAP_MODE_CONTRAST_CURVE` supported), `COLOR_CORRECTION_MODE`/`_TRANSFORM`/`_GAINS`,
`SHADING_MODE`, `HOT_PIXEL_MODE`. Cinema profiles set `NOISE_REDUCTION_MODE_OFF` or `_MINIMAL`,
`EDGE_MODE_OFF`, and `TONEMAP_MODE_CONTRAST_CURVE` with a flat/log-approximation curve where supported, falling back
to `TONEMAP_MODE_FAST` with the vendor default curve (labeled as such) where those modes are unavailable — check
`availableNoiseReductionModes`/`availableEdgeModes`/`availableToneMapModes` before requesting any of them; requesting
an unsupported mode is a common cause of `CaptureFailure`.

## 8. Log/flat profile strategy

Three-tier fallback, applied in this priority order and reported to the user which tier is active:

1. **Vendor log/gamma vendor tag**, if a `com.xiaomi.*`/`com.qti.*` vendor tag is discovered by the capability dump
   that plausibly maps to a log/flat capture curve (unconfirmed on this device — `assumptions.md` C-2).
2. **Software flat-profile approximation**: `TONEMAP_MODE_CONTRAST_CURVE` with a reduced-contrast curve computed at
   profile-load time, applied only as a *preview* LUT-equivalent — the underlying recorded YUV/encoded file still
   carries whatever tonemap the request applied (this is a real in-capture curve, not a preview-only fake, but it is
   explicitly labeled "flat approximation," never "log," in the UI).
3. **Standard vendor default curve** (`TONEMAP_MODE_FAST`) — used when neither of the above is available, clearly
   shown in the top status bar as "Color: Standard (no flat/log available)".

## 9. Color science

See `docs/CINEMA_FEATURES.md` §Color for the user-facing workflow. Pipeline-level facts collected per camera ID and
stored in `configs/schema/camera_profile.schema.json`-validated cache:
`CameraCharacteristics.SENSOR_COLOR_TRANSFORM1/2`, `SENSOR_CALIBRATION_TRANSFORM1/2`, `SENSOR_FORWARD_MATRIX1/2`,
`SENSOR_REFERENCE_ILLUMINANT1/2`, `SENSOR_BLACK_LEVEL_PATTERN`, `SENSOR_WHITE_LEVEL`. These feed both `DngCreator`
(for RAW stills, if available) and the app's manual white-balance-by-Kelvin UI (`COLOR_CORRECTION_GAINS` computed
from a Kelvin→RGB-gain approximation, then locked via `CONTROL_AWB_MODE_OFF`).

## 10. Metadata pipeline

Every capture request's `TotalCaptureResult` is sampled once per second during recording (not per-frame, to avoid
JNI/allocation overhead) and the last-sampled result plus every explicit user control change is folded into the JSON
sidecar (`docs/STORAGE_MEDIA.md` §Metadata, schema in `configs/schema/clip_metadata.schema.json`).

## 11. Debayer / DNG / CinemaDNG / ProRes / HEVC design

| Format | Status | Notes |
|---|---|---|
| DNG (single RAW still) | Implemented if `RAW_SENSOR` present (§3) | Via `DngCreator`, no hand-rolled debayer |
| CinemaDNG (RAW video sequence) | **Blocked**, see §3 | Tier 5/6 requirement |
| HEVC (H.265), 8-bit | Implemented | Primary recording codec, hardware encoder |
| HEVC 10-bit | Implemented **if** `MediaCodecInfo.CodecCapabilities` reports a 10-bit HEVC color format for the hardware encoder | Detected at runtime, not hardcoded — see `docs/BUILD_SYSTEM.md`/app `EncoderCapabilities` dumper |
| H.264 (AVC) | Implemented | Compatibility fallback codec |
| ProRes | **Not implemented, not planned in-app** | No legally licensed ProRes encoder available to bundle; documented external-recorder-only workflow (HDMI/UVC out → licensed external ProRes recorder) in `docs/CINEMA_FEATURES.md` |
| Custom debayer research | **Not implemented** | Requires Tier 5/6 RAW access, which is blocked |
