# OIW-ROM Security and Recovery

## 1. SELinux policy plan

OIW-ROM does **not** ship or require any SELinux policy changes:

- `OIWCamera`/`OIWLauncher` are ordinary third-party apps running in the standard `untrusted_app` domain — they use
  only public APIs (Camera2, MediaCodec, AudioRecord, Storage Access Framework) plus root (`su`) shell-outs for
  reading `/sys/class/thermal/*` (read-only, root-gated, already permitted by the existing SukiSU Ultra `su` policy).
- No new init service, no new system domain, no `file_contexts`/`service_contexts` changes are introduced.
- **Production posture is enforcing.** Nothing in this project asks for or benefits from setting SELinux to
  permissive; the base SukiSU Ultra project's own root-hiding (SUSFS) is explicitly designed to work under
  enforcing SELinux, and OIW-ROM inherits that posture unchanged.
- If a future Path-A effort ever compiles a real device/vendor tree, new domains (`oiw_camera_app`, `oiw_launcher`,
  `oiw_service`, `oiw_storage_monitor`, `oiw_thermal_monitor`, `oiw_external_control`) would need to be defined then
  — sepolicy scaffolding for that scenario lives in `device/oiw/nuwa/sepolicy/README.md`, clearly marked
  aspirational/blocked.

## 2. Verified boot posture

- Flashing the SukiSU Ultra-patched boot/init_boot image **will** fail verified boot's signature check against the
  stock public key unless the bootloader is unlocked (assumption A-4). This is the base project's existing,
  accepted trade-off — OIW-ROM does not add to it.
- OIW-ROM introduces **no additional** dm-verity or AVB changes. `system`/`vendor`/`product` partitions are never
  touched, so their AVB chain is unaffected.
- Production-hardening path if a user later wants to return to a fully stock, verified-boot-compliant state: reflash
  the original stock `boot.img`/`init_boot.img` (kept per `docs/DEVICE_BRINGUP.md` §2) and, if desired, re-lock the
  bootloader (irreversible without OEM unlock cooldown again — warn the user explicitly before this step).

## 3. Recovery path

- Stock recovery + `fastboot` only (no custom recovery — see `docs/ARCHITECTURE.md` §5 non-goals).
- Documented, tested recovery actions:
  1. Boot loop / bad kernel flash → `fastboot flash boot_a <stock boot.img>` (and `_b` slot) → reboot.
  2. App misbehavior (e.g., OIWLauncher fails to load) → `adb shell cmd package set-home-activity
     <stock-launcher-component>` or `adb uninstall com.oiw.launcher` from a host machine (no on-device access
     needed) → device returns to previous default launcher.
  3. Full unbrick (bootloader-level corruption) → EDL/download-mode recovery **is out of scope for this document**:
     Qualcomm EDL firehose recovery requires signed, device-specific programmer files that are not something we can
     verify, provide, or safely instruct without device-specific authorization data; users needing EDL recovery
     should consult official/regional Xiaomi service channels. We do not provide EDL bypass or unsigned-firehose
     guidance.

## 4. Backup procedure

Before any flash: `fastboot getvar all` (log), `adb shell dd if=/dev/block/by-name/boot_a of=/sdcard/boot_a_stock.img`
(then pull to host and store off-device), repeat for `init_boot_a` if applicable to this Android generation, and for
both A/B slots. Store backups outside the phone (host machine + a second location) — a backup that only exists on
the device you might brick is not a backup.

## 5. Unbrick assumptions

Covered in §3 item 3. We explicitly do **not** provide: EDL firehose files, MSM download tool bypass instructions,
or any procedure that requires disabling secure-boot chain-of-trust checks. This is a hard line per the project's
legal/safety constraints, not a gap we intend to fill later.

## 6. OTA design

There is no OTA mechanism for `OIWCamera`/`OIWLauncher` — they are sideloaded APKs. Update mechanism: user re-runs
`adb install -r` with a new build. A future in-app "check GitHub releases for a new APK" feature is documented as a
stretch goal in `docs/API_PLUGIN_FRAMEWORK.md` but is **not** implemented in this pass (would require handling
`REQUEST_INSTALL_PACKAGES` permission flow and a trusted release-signing verification step — nontrivial security
surface that deserves its own dedicated design pass, not a rushed addition here).

Kernel-side OTA (a new SukiSU Ultra kernel build) follows the base project's existing flashable-zip process
(AnyKernel3, per `Kernel/configs/nuwa-13.config.json`) — unchanged.

## 7. Signing keys

- App release keystore: user-generated, user-held, never committed (`docs/BUILD_SYSTEM.md` §6).
- Kernel signing: inherited from the base SukiSU Ultra/AnyKernel3 flow — this project does not introduce a new
  kernel-signing keypair.

## 8. Risk register

| Risk | Severity | Likelihood | Detection | Mitigation | Fallback |
|---|---|---|---|---|---|
| Re-locking bootloader after patched boot image is flashed | Critical (brick) | Low (requires deliberate user action against documented warning) | N/A — preventable | Explicit warning in `docs/DEVICE_BRINGUP.md` §2 | Stock boot image reflash only works while unlocked; if locked with a patched image, standard OEM unlock-again flow required, subject to platform cooldown |
| Losing the stock boot image backup | High | Medium (user error) | Preflight checklist requires backup before first flash | `docs/DEVICE_BRINGUP.md` backup step is mandatory, called out in README warnings | None if truly lost — this is why the backup step is mandatory, not optional |
| Writing to `persist` partition | Critical (camera calibration loss) | Low (nothing in this project touches it) | N/A | Explicitly never targeted by any OIW tool/script | Camera calibration recovery may require OEM service center |
| App requesting `MANAGE_EXTERNAL_STORAGE` unnecessarily broadly | Medium (privacy/security posture) | Low | Manifest review | Scope storage access to `/sdcard/OIW_MEDIA` project tree via SAF where possible; only fall back to broad storage permission if segmented recording proves SAF too slow (measured, not assumed) | Revoke permission via system settings |
| Local external-control socket exposed without pairing | Medium | Low (disabled by default) | Code review of `docs/API_PLUGIN_FRAMEWORK.md` implementation | Off by default, loopback/local-network only, pairing token required | Disable feature in Settings |
