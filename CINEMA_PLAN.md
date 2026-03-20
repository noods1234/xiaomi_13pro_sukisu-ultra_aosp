# Xiaomi 13 Ultra Cinema Phone Conversion — Implementation Plan

## Reality Check / Ground Truth

| Fact | Status |
|------|--------|
| Repo codename | `nuwa` = Xiaomi 13 Pro (SM8550) |
| Target device | `ishtar` = Xiaomi 13 Ultra (SM8550) |
| Same SoC? | YES — Snapdragon 8 Gen 2 (SM8550) on both |
| Same GKI kernel? | YES — 5.15 GKI, crdroid SM8550 source covers both |
| Device mismatch in repo | CONFIRMED — must update configs, names, release notes |
| Cinema hardware strengths | IMX989 1" sensor, Leica lenses, Camera2 Level 3, 14-bit RAW, variable aperture f/1.9–f/4.0, 4K/120fps capable |
| Kernel-level cinema features missing | USB DisplayPort alt mode, USB audio class, V4L2 enhancements, UAS fast storage, high-res timers |

---

## What This Repo Is (and Isn't)

This is a **kernel-only CI/CD build repo**. It produces a flashable kernel ZIP.
It does NOT contain ROM, device tree, camera HAL, or media_codecs files.
Cinema improvements happen at two levels:
1. **Kernel level** (this repo) — unlock hardware interfaces
2. **Userspace level** (apps + Magisk/KSU modules) — documented in README

---

## Implementation Steps

### Step 1 — Retarget: nuwa → ishtar

**Files to change:**
- `Kernel/configs/nuwa-13.config.json` → rename to `nuwa-13.config.json.stop` (disable)
- Create `Kernel/configs/ishtar-cinema.config.json` (new primary config)
  - Same crdroid SM8550 kernel source (supports all SM8550 Xiaomi devices)
  - Device name: `ishtar`
  - Build name: `ishtar-cinema-kernel_xiaomi_sm8550`
  - All SukiSU/SUSFS/KPM features preserved

**Workflow release name updates (both YMLs):**
- `RELEASE_NAME`: "Xiaomi 13 Ultra (Ishtar) - Cinema / SukiSU Ultra / KPM / SUSFS"
- `tag_name`: `Ishtar-Cinema-SukiSU_Ultra-v...`

---

### Step 2 — Cinema Kernel Config Block

Add a new workflow step **"Setup Cinema Kernel Features"** in both YMLs, inserting after the SUSFS setup step. This appends cinema-specific `CONFIG_*` flags to the defconfig:

#### USB-C External Monitor / DisplayPort Output
```
CONFIG_TYPEC_DP_ALTMODE=y           # DisplayPort over USB-C
CONFIG_USB_TYPEC_ALTMODE=y          # USB-C alt mode framework
CONFIG_EXTCON=y                     # External connector events
CONFIG_EXTCON_USB_GPIO=y
```

#### Professional USB Audio (external mics, mixers, recorders)
```
CONFIG_SND_USB_AUDIO=y              # USB audio class driver
CONFIG_USB_AUDIO=y                  # USB audio gadget
CONFIG_SND_RAWMIDI=y                # Raw MIDI for audio devices
CONFIG_SND_SEQUENCER=y              # MIDI sequencer
```

#### Fast External Storage (SSD via USB-C for RAW/ProRes offload)
```
CONFIG_USB_XHCI_HCD=y               # USB 3.x host controller
CONFIG_USB_STORAGE=y                # USB mass storage
CONFIG_USB_UAS=y                    # USB Attached SCSI (fast SSDs)
CONFIG_SCSI_MOD=y
```

#### V4L2 / Media Framework (external camera input, monitoring)
```
CONFIG_MEDIA_SUPPORT=y
CONFIG_VIDEO_V4L2=y
CONFIG_VIDEO_V4L2_SUBDEV_API=y
CONFIG_VIDEOBUF2_CORE=y
CONFIG_VIDEOBUF2_V4L2=y
CONFIG_VIDEOBUF2_DMA_CONTIG=y
```

#### Real-time / Low-latency Scheduling (smoother video pipeline)
```
CONFIG_HZ_1000=y                    # 1000Hz timer for low audio/video latency
CONFIG_HIGH_RES_TIMERS=y
CONFIG_NO_HZ_FULL=y
CONFIG_RCU_NOCB_CPU=y
```

#### USB Video Class (use phone as UVC webcam / monitor output)
```
CONFIG_USB_GADGET=y
CONFIG_USB_CONFIGFS=y
CONFIG_USB_CONFIGFS_F_UVC=y         # USB Video Class gadget
CONFIG_USB_CONFIGFS_F_UAC2=y        # USB Audio Class 2.0 gadget
```

---

### Step 3 — AnyKernel3 Device Name Update

In both YMLs, update the AnyKernel3 pack step to set the correct device:
- Artifact name: `AnyKernel3-ishtar-cinema-SukiSU_Ultra-{DATE}`
- The WildPlusKernel `gki-2.0` branch is device-agnostic (do.devicecheck=0), no change needed

---

### Step 4 — README Overhaul

Rewrite README.md to document the cinema phone conversion:

```
# Xiaomi 13 Ultra (Ishtar) — Cinema Kernel (SukiSU Ultra)

## Cinema Phone Stack
[Hardware → Kernel → Apps → Accessories diagram]

## Kernel Features Added
[List of all cinema CONFIG_* additions and what they enable]

## Required KSU Modules (userspace)
- SukiSU Ultra Manager
- SUSFS module
- ZygiskNext + LSPosed
- PlayIntegrityFix

## Recommended Cinema Apps
- FiLMiC Pro (LogV3 10-bit, manual controls)
- MCPro+ (multi-cam, manual)
- ProCam X (RAW DNG, manual)
- Open Camera (free, Camera2 full access)

## Recommended Hardware
- USB-C to HDMI 2.1 adapter (DisplayPort alt mode)
- External monitor: Portkeys BM5 III / SmallHD Focus 5
- USB-C audio interface: Rode AI-Micro, Zoom Am7
- Cage: Tilta Sidewinder / SmallRig for 13 Ultra
- USB-C SSD: Samsung T9 (UAS protocol)

## Color Science Notes
- IMX989 captures 14-bit RAW (DNG via ProCam X)
- FiLMiC LogV3 requires Camera2 Level 3 (✓ supported)
- Rec.709 / DCI-P3 display modes available via display settings
- Post workflow: DaVinci Resolve with Xiaomi log LUTs
```

---

### Step 5 — Kernel/cinema/ Documentation Directory

Create `Kernel/cinema/` with:
- `cinema_configs_explained.txt` — every CONFIG flag with why it matters for filmmaking
- `kpm_modules_guide.md` — how to write/use KPM modules for camera HAL tweaks
- `lut_workflow.md` — color grading workflow from Xiaomi RAW/log to DCI-P3

---

## File Change Summary

| File | Action |
|------|--------|
| `Kernel/configs/nuwa-13.config.json` | Rename to `.stop` (disable) |
| `Kernel/configs/ishtar-cinema.config.json` | CREATE (new primary config) |
| `.github/workflows/SukiSU_SUSFS.yml` | Add cinema config step + update names |
| `.github/workflows/KernelSU-Next.yml` | Add cinema config step + update names |
| `README.md` | Overhaul for cinema/ishtar |
| `Kernel/cinema/cinema_configs_explained.txt` | CREATE |
| `Kernel/cinema/kpm_modules_guide.md` | CREATE |
| `Kernel/cinema/lut_workflow.md` | CREATE |

---

## What This Kernel Unlocks vs What Needs Apps

| Cinema Feature | Layer | Method |
|----------------|-------|--------|
| DisplayPort external monitor | Kernel | `CONFIG_TYPEC_DP_ALTMODE` |
| USB-C mic/audio interface | Kernel | `CONFIG_SND_USB_AUDIO` |
| Fast USB-C SSD offload | Kernel | `CONFIG_USB_UAS` |
| UVC webcam output | Kernel | `CONFIG_USB_CONFIGFS_F_UVC` |
| Low-latency audio recording | Kernel | `CONFIG_HZ_1000` |
| 14-bit RAW DNG capture | App | ProCam X / Open Camera |
| LogV3 10-bit video | App | FiLMiC Pro |
| Manual iris/focus/ISO | App | FiLMiC Pro / MCPro+ |
| Color LUT injection | App | FiLMiC Pro |
| Camera HAL bypass | KPM module | SukiSU KPM |
| Root-based camera mod | KSU module | LSPosed + CamPatch |
