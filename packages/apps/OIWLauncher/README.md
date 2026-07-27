# OIWLauncher

Camera-first HOME/DEFAULT launcher (docs/UI_UX.md #1). Standalone Android app, same build/setup process as
`packages/apps/OIWCamera` (see that module's README for the Gradle-wrapper bootstrap step).

## Behavior summary

- Registers as both `HOME`/`DEFAULT` and `LAUNCHER` — the user must explicitly set it as the default home app
  (system prompt), it does not silently hijack the home role.
- Single large "ENTER CAMERA" tile launches `com.oiw.camera` via `getLaunchIntentForPackage` — package visibility is
  scoped to exactly that one package via a `<queries>` manifest entry, not the broad `QUERY_ALL_PACKAGES` permission.
- Optional, off-by-default boot-to-camera (`BootToCameraReceiver`) with an explicit notification fallback if the
  direct activity launch is blocked by background-start restrictions on a given HyperOS version.
- Status strip reads a small status file OIWCamera writes to `/sdcard/OIW_MEDIA/.status.json` — deliberately
  decoupled from OIWCamera's process lifecycle so the launcher never blocks on or crashes from a bound-service
  connection to an app that isn't running.
- "Distraction-free mode" (`LauncherPreferences`) hides the secondary shortcuts row (files/logs/settings), leaving
  only the record tile and status strip.

## Not implemented in this pass

- Quick-settings tiles (Camera Mode, Airplane Cinema Mode, etc.) are implemented in `OIWCamera`
  (`docs/UI_UX.md` #3), not here — `TileService` tiles are process-independent of which launcher is active.
- Full kiosk-mode lock-task API integration is a stretch goal (`docs/CINEMA_FEATURES.md` §6/§13), not wired up yet.
