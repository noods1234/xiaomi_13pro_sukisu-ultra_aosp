# configs/

Hand-editable JSON data consumed by OIWCamera (docs/API_PLUGIN_FRAMEWORK.md #1 — declarative plugins,
not executable code).

| Directory | Contents |
|---|---|
| `capture_profiles/` | 12 example capture profiles (root brief §8.2) — bundled into `packages/apps/OIWCamera/app/src/main/assets/capture_profiles/` at build time |
| `lut/` | `index.json` catalog + example `.cube` LUTs (real, parseable 17-point files) |
| `button_mappings/` | `default.json` — see `docs/CINEMA_FEATURES.md` #3 |
| `thermal_profiles/` | The 7 OIW thermal modes — see `docs/THERMAL_POWER.md` #7 |
| `storage_profiles/` | Internal/external-SSD storage target definitions — see `docs/STORAGE_MEDIA.md` |
| `debug_profiles/` | Logging/debug-panel verbosity presets |
| `lens_profiles/` | Empty by default — user-authored per-lens metadata-default packs go here |
| `exporters/` | Empty by default — user-authored metadata exporter templates go here |
| `schema/` | JSON Schema (draft-07) for every file format above, used by `tools/metadata_validate.py` and referenced by the app's `ProfileRepository`/`MetadataWriter` |

Every file here is data, not code — this is a deliberate security boundary (docs/API_PLUGIN_FRAMEWORK.md #1).
