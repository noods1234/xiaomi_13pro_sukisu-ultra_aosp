# OIW-ROM Plugin / API Framework

## 1. Modular extension system

All extensible surfaces are **file-based plugins** loaded from `/sdcard/OIW_MEDIA/plugins/<kind>/`, parsed via the
JSON schemas in `configs/schema/`. This deliberately avoids dynamic code loading (`DexClassLoader`/plugin APKs),
which would be a real security liability on a device also running root — every "plugin" is declarative data
(profiles, LUTs, button maps, exporter templates), not executable code, until/unless a future dedicated design pass
decides that trade-off is worth it.

| Plugin kind | Format | Loaded from | Schema |
|---|---|---|---|
| Capture profile | JSON | `configs/capture_profiles/` (bundled) + `/sdcard/OIW_MEDIA/plugins/profiles/` (user) | `configs/schema/capture_profile.schema.json` |
| LUT pack | `.cube` + index entry | `configs/lut/` (bundled) + `/sdcard/OIW_MEDIA/LUTs/` (user) | `configs/schema/lut_index.schema.json` |
| Monitoring overlay | Built-in only in this pass (histogram/zebra/peaking/false-color/LUT-preview/guides) | N/A | N/A — a true overlay plugin API (loading a user-supplied shader) is a stretch goal, not implemented, since it reopens the "user-supplied code" security question above |
| Metadata exporter | JSON template (field mapping to CSV/XML) | `configs/exporters/` | `configs/schema/exporter_template.schema.json` |
| External controller protocol | See §4 | N/A | `configs/schema/control_message.schema.json` |
| Button mapping | JSON | `configs/button_mappings/` | `configs/schema/button_mapping.schema.json` |
| Per-lens/per-sensor calibration pack | JSON (metadata defaults only, not image-processing coefficients — those would require Tier 5/6 HAL access, blocked) | `configs/lens_profiles/` | `configs/schema/lens_profile.schema.json` |

## 2. Custom camera controls

Capture profiles may define additional `CaptureRequest` key/value overrides beyond the built-in manual-control set,
**gated at load time** by checking each key against `CameraCharacteristics#getAvailableCaptureRequestKeys()` for the
active camera ID — an unsupported key in a profile is reported to the user ("Profile requests
CONTROL_AE_LOCK behavior not supported here") and skipped, never silently ignored without a trace in the debug log.

## 3. LUT packs / capture profiles / metadata exporters

Covered above; see `docs/CINEMA_FEATURES.md` §LUT workflow and `docs/STORAGE_MEDIA.md` §Metadata for the consuming
behavior.

## 4. External controller protocol

**Disabled by default.** When enabled in Settings:

- Transport: a local TCP socket bound to `127.0.0.1` plus the device's local Wi-Fi-network address **only** (never
  `0.0.0.0`-equivalent open-to-internet exposure), on a configurable port.
- Auth: a random pairing token is generated on first enable, shown as a QR code in-app; a client must present the
  token on connect or the socket closes the connection immediately.
- Message format: newline-delimited JSON (`configs/schema/control_message.schema.json`), e.g.
  `{"cmd":"record_start"}`, `{"cmd":"set_iso","value":800}`, `{"cmd":"status_query"}`.
- Supported commands (v1): `record_start`, `record_stop`, `profile_switch`, `set_exposure`, `set_iso`,
  `set_white_balance`, `set_focus`, `slate_metadata`, `status_query`.
- USB HID input (keyboard-class remotes/gimbal triggers) is handled via standard `KeyEvent` dispatch, mapped through
  the same `configs/button_mappings/` files as the physical volume buttons — no separate protocol needed for HID.
- BLE control is **not implemented** in this pass (would need a full GATT service definition and pairing UX distinct
  from the TCP pairing flow above) — documented as a stretch goal, not faked.

## 5. Security posture summary

Local-only by default, opt-in, pairing-token gated, no open unauthenticated remote control — matching the root
brief's explicit security requirement in §8.6.
