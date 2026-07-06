# OIW-ROM Thermal and Power

All thermal logic is implemented **in-app** (no system service injection point exists — assumption B-3). Root
(already present via SukiSU Ultra) is used only to **read** `/sys/class/thermal/thermal_zone*/temp` and
`/sys/class/thermal/thermal_zone*/type`; OIW-ROM does not write to kernel thermal-governor sysfs nodes directly in
this pass (see §6 for the documented, not-yet-applied kernel config fragment path).

## 1. Thermal zones (to be enumerated per-unit, not hardcoded)

`tools/thermal_monitor.sh` enumerates every `thermal_zone*` and logs `type` + `temp` at a configurable interval
(default 5s) to CSV. Expected zone families on an SM8550 device (verify against real output, do not trust blindly):
CPU (per-cluster), GPU, modem/RF, skin/case, battery, camera/ISP if separately instrumented by the vendor kernel.

## 2. CPU/GPU/NPU governor strategy

OIW-ROM does **not** change kernel governors directly (no writable interface exposed safely without risking
instability, and no kernel source checked out here to add a custom governor). Instead, the app-level `ThermalMonitor`
uses Android's public `PowerManager.getCurrentThermalStatus()`/`addThermalStatusListener` (device-reported thermal
status, standard since Android 10) as the primary signal, cross-checked against the raw sysfs zone readings for
finer-grained trend detection (rate of temperature rise, not just discrete status buckets).

## 3. ISP heat

Not separately instrumentable without vendor debug hooks; treated as embedded in overall SoC package thermal trend.

## 4. Charging while recording

USB-C PD charging while recording is allowed but flagged: the app reads `BatteryManager.EXTRA_STATUS`/charging
current if exposed, and when charging + recording + thermal status ≥ `THERMAL_STATUS_MODERATE` occurs together,
shows an explicit warning ("Charging while recording is adding heat; consider unplugging or reducing bitrate") rather
than silently continuing.

## 5. USB-C PD behavior / battery bypass

"Battery bypass" (direct PD pass-through without cycling the battery) is a **hardware-mod-dependent** feature not
controllable from software on stock hardware — documented here as a hardware-mod note only (`docs/RED_TEAM_AUDIT.md`
covers the unsafe-charging risk class); no software claims are made about enabling it.

## 6. Cooling mod support

External cooling (clip-on fan/heatsink) is not electronically detectable without a hardware-specific sensor; the app
instead treats sustained-low temperature *trend* (rate of rise) as an implicit signal that cooling is effective and
surfaces it as "Thermal trend: stable" vs "rising" rather than trying to detect a specific accessory.

## 7. OIW thermal modes

| Mode | CPU/GPU intent | Display | Background services | Radios | ISP/camera priority | Charging | Warning threshold | Forced-stop threshold | Logging |
|---|---|---|---|---|---|---|---|---|---|
| `max_quality_short_take` | No app-level cap (rely on OS thermal management) | Full brightness allowed | Foreground-only enforced | User's choice | Highest (foreground service, `FOREGROUND_SERVICE_TYPE_CAMERA`) | Warn if plugged (§4) | `THERMAL_STATUS_MODERATE` | `THERMAL_STATUS_SEVERE` | Verbose |
| `balanced_field` | — | Auto-brightness capped at 70% | Foreground-only | Wi-Fi/BT off unless needed | High | Warn if plugged | `THERMAL_STATUS_LIGHT` | `THERMAL_STATUS_SEVERE` | Info |
| `long_take_safe` | — | Brightness capped at 40%, dims further after 5min | Foreground-only, airplane-mode suggested | Forced off unless external control enabled | High | Discouraged, strong warning | `THERMAL_STATUS_LIGHT` | `THERMAL_STATUS_MODERATE` (earlier, conservative) | Info |
| `external_cooling` | No app-level cap (assumes effective cooling per §6 trend detection) | Full brightness allowed | Foreground-only | User's choice | Highest | Allowed | `THERMAL_STATUS_SEVERE` | `THERMAL_STATUS_CRITICAL` | Verbose |
| `charging_safe` | — | Capped at 50% | Foreground-only | Off | Medium-high | Expected/allowed by definition | `THERMAL_STATUS_MODERATE` | `THERMAL_STATUS_SEVERE` | Info |
| `stealth_low_heat` | — | Minimum usable brightness, dim UI theme | Foreground-only, radios off | Off | Medium | Discouraged | `THERMAL_STATUS_LIGHT` | `THERMAL_STATUS_MODERATE` | Warn only |
| `debug_unrestricted` | No caps at all | No caps | No restrictions | User's choice | Highest | Allowed | None (monitoring only) | `THERMAL_STATUS_EMERGENCY` (device self-protection only) | Verbose + raw sysfs dump |

## 8. Graceful stop state machine

```
RECORDING ──thermal status crosses warning threshold──> WARNING (on-screen banner, countdown estimate shown)
WARNING ──status improves──> RECORDING
WARNING ──status crosses forced-stop threshold──> FINALIZING (stop encoder, flush muxer, write sidecar,
                                                    mark clip "thermal-stopped" in metadata) ──> STOPPED
```
The state machine always finalizes the current file before stopping — footage safety takes priority over chasing
maximum runtime, per the root brief's explicit acceptance criterion.

## 9. Long-take stability profile

`cinema_long_take_safe.json` (see `configs/capture_profiles/`) pins `long_take_safe` thermal mode, caps bitrate to a
conservative value pending real-device storage-benchmark results, and defaults to H.264 (typically lower encode
power draw than HEVC on mid-tier hardware encoders, verify per-device with `tools/thermal_monitor.sh` during a
back-to-back A/B recording test).
