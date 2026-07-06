# OIW-ROM Red Team Audit

Performed against the architecture (`docs/ARCHITECTURE.md`) and the implementation plan
(`implementation_plan.md`). Severity: Critical/High/Medium/Low. Likelihood: Low/Medium/High given the design as
built (Path D app-first + existing rooted kernel, not a full custom ROM).

## 1. Brick risk

| Failure mode | Severity | Likelihood | Detection | Mitigation | Test case |
|---|---|---|---|---|---|
| Re-locking bootloader with a patched boot image installed | Critical | Low (requires ignoring explicit warning) | N/A, preventable | Warning in `docs/DEVICE_BRINGUP.md`/`SECURITY_AND_RECOVERY.md`; app never prompts for this | Manual review of docs before flashing |
| Flashing a bad kernel build to the wrong slot | High | Low | `fastboot getvar current-slot` check before flash | Documented backup-first procedure | `docs/DEVICE_BRINGUP.md` §2/§4 |
| Writing to `persist` partition | Critical | Very low (nothing in this project touches it) | Code review — no tool references `persist` | Never targeted | N/A |
| EDL/unsigned firehose usage | Critical | N/A — not provided by this project | N/A | We deliberately do not document this path | N/A |

## 2. Footage corruption risk

| Failure mode | Severity | Likelihood | Detection | Mitigation | Test case |
|---|---|---|---|---|---|
| App killed mid-`MediaMuxer` write | High | Medium (OOM-killer, crash, forced-stop) | Crash-recovery scan on next launch | Segmented files bound loss to one segment; atomic sidecar write | Recording Tests §5 force-kill case |
| External SSD unplugged mid-write | High | Medium (field conditions) | Storage-volume-lost callback | Immediate safe-stop attempt + recovery scan next launch | Recording Tests §5 unsafe-unplug case |
| Storage fills up mid-recording | Medium | Medium | `StatFs` free-space poll | Preflight refusal + graceful stop on `ENOSPC` | Recording Tests §5 low-storage case |
| Corrupted container from a codec error not surfaced to the writer thread | High | Low | `MediaCodec.Callback#onError` wired to writer state machine | Explicit error surfaced, safe-stop triggered, never continues writing silently | Camera Tests, Storage Tests |

## 3. Dropped-frame risk

| Failure mode | Severity | Likelihood | Detection | Mitigation | Test case |
|---|---|---|---|---|---|
| Storage write throughput below bitrate | Medium | Medium (unverified drives/units) | Preflight benchmark + bounded writer queue | Refuse profile / optional auto-downshift (explicit, logged) | Recording Tests, Storage Tests |
| CPU/GPU thermal throttling mid-take reduces effective encode throughput | Medium | Medium on sustained 4K/high-bitrate takes | Dropped-frame counter + thermal trend | Thermal modes bias toward lower sustained load | Thermal Tests |
| Background app/service contention (stock HyperOS background tasks not disableable without root-level tweak) | Medium | Medium — Path D can't fully silence OEM background services without root overlays, which are only partially implemented in this pass | Dropped-frame indicator | "Airplane Cinema Mode" + foreground-only enforcement reduces but doesn't guarantee zero background contention | Recording Tests |

## 4. Camera HAL instability

| Failure mode | Severity | Likelihood | Detection | Mitigation | Test case |
|---|---|---|---|---|---|
| `cameraserver` crash/restart during a session | High | Low-medium (vendor HAL bugs happen) | `CameraDevice.StateCallback#onError`/`onDisconnected` | Explicit error message + automatic reconnect attempt, footage up to that point preserved by segmentation | Camera Tests crash-recovery case |
| Requesting an unsupported stream combination | Medium | Medium if profile authoring is careless | `isSessionConfigurationSupported` pre-check | Defensive check before every session build (already implemented) | Camera Tests |
| Vendor extension session silently changing exposure/color | Medium | Low (extensions excluded from cinema profiles by design) | N/A — architectural exclusion | Tier 3 never wired into cinema profiles | `docs/CAMERA_PIPELINE.md` §6 |

## 5. Thermal runaway

| Failure mode | Severity | Likelihood | Detection | Mitigation | Test case |
|---|---|---|---|---|---|
| Device hits `THERMAL_STATUS_SEVERE`/`CRITICAL` mid-take | High (footage loss risk + hardware safety) | Medium on long 4K takes | `PowerManager` thermal listener + sysfs trend | Graceful-stop state machine finalizes file before OS forces a shutdown | Thermal Tests |
| OS-level emergency thermal shutdown faster than app can react | High | Low-medium | N/A — outside app control at that point | Segmentation minimizes loss window; `long_take_safe` mode biases conservative thresholds well before OS-forced shutdown | Thermal Tests |

## 6. Battery/charging risk

| Failure mode | Severity | Likelihood | Detection | Mitigation | Test case |
|---|---|---|---|---|---|
| Charging + recording compounding heat | Medium | Medium (common field workaround: keep plugged in) | Charging-state + thermal-status combined check | Explicit warning, no silent continuation | Thermal Tests charging case |
| Claims about "battery bypass" safety | N/A | N/A | N/A | We make **no software claims** about battery bypass — hardware-mod-only, documented as such | `docs/THERMAL_POWER.md` §5 |

## 7. External SSD failure

| Failure mode | Severity | Likelihood | Detection | Mitigation | Test case |
|---|---|---|---|---|---|
| Drive disconnects mid-take | High | Medium | Volume-lost callback | Safe-stop + recovery scan | Recording Tests |
| Drive is slower than advertised | Medium | Medium (cheap/counterfeit media exists) | Real benchmark, not spec-sheet trust | Preflight refusal based on measured speed | Storage Tests |
| Wrong filesystem (NTFS, unsupported) | Low-Medium | Low-medium | Mount-detection failure | Explicit "filesystem not recognized" message with recommended fix | Storage Tests |

## 8. Audio sync failure

| Failure mode | Severity | Likelihood | Detection | Mitigation | Test case |
|---|---|---|---|---|---|
| Bluetooth-sourced audio for capture | Medium | N/A — excluded by design | N/A | Not offered as a capture source | `assumptions.md` C-4 |
| Clock drift on very long takes | Low-Medium | Low | Monotonic-vs-wall-clock delta logged | Sidecar exposes drift-detection data for post; LTC (Tier 4) would eliminate this but isn't built yet | Audio Tests long-take-drift case |
| No timecode device available | Low | High (most users won't have one) | N/A | Tiers 1–3 always available as a floor | Audio Tests |

## 9. Color pipeline inaccuracy

| Failure mode | Severity | Likelihood | Detection | Mitigation | Test case |
|---|---|---|---|---|---|
| "Flat/log" profile is actually just a software tonemap curve, not true sensor log | Medium (creative/workflow expectation mismatch) | High until vendor log tag confirmed absent/present | Explicit UI labeling ("Flat approximation" vs "Log") | Never labeled as true log unless a vendor log tag is confirmed | `docs/CAMERA_PIPELINE.md` §8 |
| LUT accidentally baked into a delivered clip without the operator noticing | Medium | Low (off by default, always logged) | Sidecar always records LUT-baked state | Explicit opt-in + always-logged | `docs/CINEMA_FEATURES.md` §6 |
| WB-by-Kelvin approximation inaccurate vs. true illuminant | Low-Medium | Medium (approximation, not measured per-unit) | Gray-card workflow as a sanity check | Documented as an approximation, not a calibrated instrument | `docs/CINEMA_FEATURES.md` §6 |

## 10. UI confusion under pressure

| Failure mode | Severity | Likelihood | Detection | Mitigation | Test case |
|---|---|---|---|---|---|
| Operator can't tell why recording won't start | Medium | Medium if error UX regresses | Manual UX review | "No vague errors" rule (§Error UX), unit-tested per message | UI Tests |
| Power button accidentally stops a recording | High (footage loss) | Low (explicitly designed against) | N/A | Power button never mapped to stop-recording | UI Tests, Cinema Features §3 |

## 11. Recovery failure

| Failure mode | Severity | Likelihood | Detection | Mitigation | Test case |
|---|---|---|---|---|---|
| No stock boot image backup taken before first flash | Critical | Medium (easy to skip a checklist step) | N/A — preventable only | Mandatory backup step called out in README/bring-up doc | `docs/DEVICE_BRINGUP.md` §4 |
| User needs EDL-level recovery | Critical | Low | N/A | Out of scope by design; directed to OEM channels | `docs/SECURITY_AND_RECOVERY.md` §3 |

## 12. SELinux/security regression

| Failure mode | Severity | Likelihood | Detection | Mitigation | Test case |
|---|---|---|---|---|---|
| Temptation to set SELinux permissive to "fix" an app issue | High (security regression) | Low if this doc is followed | Code/config review | Explicitly never required or recommended anywhere in this project | `docs/SECURITY_AND_RECOVERY.md` §1 |
| Broad storage permission requested unnecessarily | Medium | Low-medium | Manifest review | SAF-first approach, broad permission only if measured necessary | `docs/SECURITY_AND_RECOVERY.md` §8 risk register |

## 13. Hidden auto-processing

| Failure mode | Severity | Likelihood | Detection | Mitigation | Test case |
|---|---|---|---|---|---|
| Vendor "beauty"/scene-detection processing active by default | Medium | Medium (varies by vendor camera defaults even through Camera2) | Explicit `NOISE_REDUCTION_MODE`/`EDGE_MODE`/face-detection-off requests in cinema profiles | Requested off wherever the capability list allows it; flagged in UI if the vendor HAL doesn't honor the off-request (detectable by comparing requested vs. result mode) | Camera Tests |
| Auto-bitrate/quality downshift without operator awareness | Medium | Low (opt-in only, always logged) | Sidecar log | Opt-in + always-visible + always-logged | Storage Tests |

## 14. Vendor blob dependency

| Failure mode | Severity | Likelihood | Detection | Mitigation | Test case |
|---|---|---|---|---|---|
| Redistributing extracted vendor blobs | High (legal) | N/A — not done | Code/repo review | `vendor/oiw/nuwa/` only documents an extraction *script*, never ships extracted binaries | Repo audit |
| Feature ceiling silently assumed to be higher than stock vendor HAL allows | Medium | Medium if capability dump is skipped | Capability dump requirement before trusting any profile | `assumptions.md` explicitly gates every camera claim on the dump | `docs/CAMERA_PIPELINE.md` |

## 15. Unsupported hardware claim

| Failure mode | Severity | Likelihood | Detection | Mitigation | Test case |
|---|---|---|---|---|---|
| Claiming RAW video/CinemaDNG works when it doesn't | Medium (credibility/workflow risk) | N/A — explicitly marked blocked | N/A | `assumptions.md` C-3, `docs/CAMERA_PIPELINE.md` §3 | N/A |
| Claiming ProRes support without a licensed encoder | Medium (legal/credibility) | N/A — explicitly not implemented | N/A | Documented external-recorder-only workflow | N/A |
| Claiming DP Alt Mode monitor output works without testing | Medium | Medium if untested | Manual hardware test required | Marked "unconfirmed" until tested (`assumptions.md` A-5) | UI Tests external-monitor case |

## 16. Excessive scope creep

| Failure mode | Severity | Likelihood | Detection | Mitigation | Test case |
|---|---|---|---|---|---|
| Chasing Path A (full custom ROM) without a device/vendor tree | Medium (wasted effort) | Low given this audit | This document itself | Path A explicitly gated behind assumption A-3 becoming false | `docs/ARCHITECTURE.md` §2 |
| Building a dynamic-code plugin system that reopens security review | Low-Medium | Low (declarative-only plugin design chosen) | Architecture review | `docs/API_PLUGIN_FRAMEWORK.md` §1 explicit rationale | N/A |

## 17. Long-term maintainability

| Failure mode | Severity | Likelihood | Detection | Mitigation | Test case |
|---|---|---|---|---|---|
| HyperOS update changes Camera2 behavior/vendor tags | Medium | Medium over time | Regression matrix re-run per `docs/TEST_PLAN.md` §10 | Capability dump re-run after every OS update | Regression matrix |
| Upstream `crdroidandroid/android_kernel_xiaomi_sm8550` branch changes/disappears | Medium | Low-medium (community-maintained) | CI failure | Config fragment kept as a portable patch, not a fork-and-forget | `docs/BUILD_SYSTEM.md` §1 |
