# OIW-ROM UI/UX Specification

## 1. Camera-first launcher (OIWLauncher)

- Registers as `HOME`/`DEFAULT` category launcher (`AndroidManifest.xml` intent-filter). On first launch after being
  set default, shows a one-time explainer + "Set as default" system prompt (standard `RoleManager`/Settings intent —
  no framework patch).
- Home screen: full-bleed record shortcut (largest tile), profile shortcuts row, storage/thermal/battery status
  strip, logs/file-browser/settings/recovery-notes shortcuts in a secondary row. No widgets, no app drawer clutter —
  a "Distraction-free mode" setting hides everything except the record shortcut and status strip.
- Boot-to-camera: `OIWLauncher` can optionally auto-launch `OIWCamera` on cold boot (`ACTION_BOOT_COMPLETED` receiver
  → start activity, permitted for the default launcher/HOME app without extra background-start restrictions since
  Android 10+ still allows the current default launcher to start activities from receivers in most OEM
  implementations — **verify on this HyperOS version**, since OEM background-start restrictions vary; if blocked,
  fall back to "show a persistent 'Tap to enter camera' notification" instead of a silent failure).

## 2. Lock screen behavior

- Direct camera launch from lock screen via a standard `CameraManager`/keyguard-camera-shortcut style affordance
  (public API pattern used by stock camera apps; exact keyguard-quick-affordance API depends on Android version —
  gate behind a version check, fall back to "unlock then tap OIWLauncher" if unavailable).
- Two-finger-tap screen lock **during an active recording** keeps recording running in the background service and
  shows a persistent notification with elapsed time and a big "STOP" action — recording must never be silently
  paused by a lock event.
- Emergency-stop gesture (three-finger tap, §Cinema Features) works even from the lock-screen-recording notification
  view.

## 3. Quick settings tiles

Camera Mode · Airplane Cinema Mode · External SSD · Thermal Profile · LUT Preview · False Color · Zebra · Focus
Peaking · Screen Lock · External Monitor · Debug Logs — implemented as standard `TileService` quick-settings tiles
(public API, no framework patch) so they're reachable from anywhere in the OS, not just inside OIWCamera.

## 4. Camera screen layout

```
┌───────────────────────────────────────────────────────────────────┐
│ 4K·24p  HEVC10  180Mbps  Flat  ▸SSD  01:04:12 left  🔋78%  🌡Mod  │  status bar
├───────┬───────────────────────────────────────────────┬───────────┤
│ ISO   │                                               │  ⏺ REC   │
│ 800   │                                               │  📷 STILL │
│ 1/48  │                 live preview                  │  ▤ profile│
│ Shut  │            (overlays composited here)         │  📊 tools │
│ 5600K │                                               │  ⚙ settings│
│ WB    │                                               │           │
│ MF    │                                               │           │
│ Focus │                                               │           │
├───────┴───────────────────────────────────────────────┴───────────┤
│ 🎙▮▮▮▯▯ -12dB   TC 01:23:45:12   drops:0   LUT: Rec709→Flat  EIS:off│  bottom bar
└───────────────────────────────────────────────────────────────────┘
```

Touch targets: cinema controls sized 64dp minimum (larger than Android's 48dp minimum, for glove/rig use per root
brief §13). Left/right rails collapse to icon-only in "Distraction-free" mode, tap to expand.

## 5. Error UX

Rule: **every error message states the specific cause and, where possible, the corrective action** — never a bare
"something went wrong." Examples implemented in `OIWCamera`'s error-presentation layer:

- "Measured write speed 18.4 MB/s is below the 27.0 MB/s required (with safety margin) by this profile. Lower
  bitrate or use faster storage."
  (27.0 MB/s = the 180 Mbps 4K profile's 22.5 MB/s plus the 20% margin. The earlier example here quoted
  "115 MB/s required", which was arithmetic from the 8x unit bug in `StorageManager.preflight` — see
  `docs/AUDIT_FINDINGS.md` finding T. Corrected rather than quietly deleted.)
- "Camera HAL rejected fixed frame duration (requested 1/48s, min supported 1/30s at this resolution). Falling back
  to 1/30s."
- "Thermal headroom low (status: SEVERE). Estimated safe recording time: under 8 minutes at current settings."
- "RAW capability not reported for camera ID 0 on this device. RAW still capture is unavailable."
- "File finalization failed for CLIP_0042.mp4 (I/O error, code -28: ENOSPC). Attempting recovery — check free space."
- "Vendor camera service crashed (CameraDevice.StateCallback#onError, error 3). Restarting camera session."

Each message is sourced from an actual thrown exception/callback error code where possible, not templated fake text
— the debug panel additionally shows the raw exception/error code for field diagnosis.

## 6. Touch targets / physical control interaction

See §Cinema Features button mapping. All primary actions (record, still, profile switch) are reachable both via
touch and via the mapped hardware buttons, so gloved/rig operation never requires touching the screen.
