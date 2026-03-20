# Xiaomi 13 Ultra (Ishtar) — Cinema Kernel

**SukiSU Ultra + KernelSU Next | Cinema Build | SM8550 / GKI 5.15 | HyperOS**

This repo builds a custom kernel for the **Xiaomi 13 Ultra (codename: ishtar)** targeting professional filmmaking and cinema phone use. Built on the SM8550 GKI 5.15 base with SukiSU Ultra (or KernelSU Next), SUSFS root hiding, KPM patching, and a full suite of cinema-specific kernel features.

> [!WARNING]
> Only tested on HyperOS ROMs for ishtar. Not compatible with other SM8550 devices (nuwa, marble, etc.). Proceed at your own risk.

---

## Hardware: Why Xiaomi 13 Ultra for Cinema

| Feature | Spec |
|---------|------|
| Main sensor | Sony IMX989 — 1-inch, 50MP |
| Lens system | Xiaomi x Leica Summicron |
| Variable aperture | f/1.9 – f/4.0 (2-stop physical iris) |
| All cameras | 4x 50MP (wide, UW, 75mm tele, 120mm tele) |
| Camera2 API | Level 3 (full professional feature set) |
| RAW capture | 14-bit DNG (Adobe DNG with calibration metadata) |
| Video | 4K/60fps, 8K/24fps, 4K/120fps (slow-mo) |
| HDR | Dolby Vision, HDR10+, HLG |
| Display | 6.73" AMOLED, 3200x1440, LTPO 1-120Hz, DCI-P3 |
| USB-C | USB 3.2 Gen 2, DisplayPort alt mode |
| SoC | Snapdragon 8 Gen 2 (SM8550) |

---

## Cinema Kernel Features

### Unlocked by this Kernel Build

| Feature | Kernel Config | What It Enables |
|---------|--------------|-----------------|
| USB-C external monitor | `CONFIG_TYPEC_DP_ALTMODE` | DP 1.4 output to SmallHD, Portkeys, etc. |
| USB-C alt mode framework | `CONFIG_USB_TYPEC_ALTMODE` | Generic USB-C alt mode support |
| External connector events | `CONFIG_EXTCON` | Hot-plug detection for monitors |
| USB audio class (UAC2) | `CONFIG_SND_USB_AUDIO` | Rode AI-Micro, Zoom AM7, XLR interfaces |
| USB audio gadget | `CONFIG_USB_AUDIO` | Phone as USB audio source |
| Raw MIDI | `CONFIG_SND_RAWMIDI` | MIDI-capable audio devices |
| Fast USB SSD (UAS) | `CONFIG_USB_UAS` | Full-speed Samsung T9 / SanDisk Pro |
| USB 3.x host | `CONFIG_USB_XHCI_HCD` | USB 3.2 speeds for external storage |
| V4L2 media framework | `CONFIG_VIDEO_V4L2` | Camera input pipeline, external capture |
| V4L2 subdev API | `CONFIG_VIDEO_V4L2_SUBDEV_API` | External camera modules |
| Video buffer (DMA) | `CONFIG_VIDEOBUF2_DMA_CONTIG` | Zero-copy video pipeline |
| 1000Hz timer | `CONFIG_HZ_1000` | Low-latency audio/video frame scheduling |
| High-res timers | `CONFIG_HIGH_RES_TIMERS` | Sub-millisecond timer precision |
| UVC gadget | `CONFIG_USB_CONFIGFS_F_UVC` | Use phone as USB webcam/monitor output |
| UAC2 gadget | `CONFIG_USB_CONFIGFS_F_UAC2` | Phone as professional USB audio interface |

### From Base Build (SukiSU / SUSFS / KPM)

- **SukiSU Ultra** — kernel-mode root, KPM module loader
- **SUSFS** — Magic Mount, SUS Path, SUS Mount, SUS kstat, syscall hooks, uname spoofing
- **KPM** — Kernel Patch Manager for runtime camera HAL and system patches
- **lz4k / lz4kd** — improved ZRAM compression (better memory headroom for video buffers)
- **Wireguard** — VPN for secure on-set data transfer
- **Manual hooks** — improved root hiding vs detection

---

## Cinema Workflow Stack

```
Hardware Layer
  Xiaomi 13 Ultra (ishtar)
    IMX989 1" sensor + Leica lenses
    Variable aperture f/1.9-f/4.0
    USB 3.2 Gen 2 / DisplayPort alt mode

Kernel Layer (this repo)
  SukiSU Ultra + KPM + SUSFS
  Cinema features: DP out, USB audio, UAS, V4L2, 1000Hz

Root Modules (userspace)
  SukiSU Manager + SUSFS module
  ZygiskNext + LSPosed (for camera patches)
  PlayIntegrityFix

Apps — Capture
  FiLMiC Pro     LogV3 10-bit, manual iris/focus/ISO/WB
  ProCam X       14-bit RAW DNG, CinemaDNG
  MCPro+         Multi-cam, anamorphic de-squeeze
  Open Camera    Free, full Camera2 API access

Apps — Monitoring
  Mavis          False color, zebras, waveform monitor
  NiMovies       Cinema look with real-time LUTs

Post-Production
  DaVinci Resolve  Color grade RAW DNG / LogV3 footage
  LUT workflow     See Kernel/cinema/lut_workflow.md
```

---

## Recommended Hardware Accessories

### External Monitors / Recorders
| Device | Connection | Notes |
|--------|-----------|-------|
| Portkeys BM5 III | USB-C DP | 5" HDR touchscreen monitor |
| SmallHD Focus 5 | USB-C DP | 5" daylight-readable |
| Atomos Ninja V | HDMI | Via USB-C to HDMI 2.0 adapter |
| Accsoon CineView HE | HDMI | Wireless HD transmission |

### Audio
| Device | Connection | Notes |
|--------|-----------|-------|
| Rode AI-Micro | USB-C (UAC2) | 2x TRS/TRRS inputs, 48V phantom |
| Zoom AM7 | USB-C (UAC2) | Ambisonic spatial audio |
| Sennheiser MKE 200 | 3.5mm TRRS | Direct mount, no adapter |
| DJI Mic 2 | 3.5mm TRRS | Wireless TX/RX system |

### Cages & Rigs
| Product | Notes |
|---------|-------|
| Tilta Sidewinder for 13 Ultra | Dedicated cage, 15mm rail support |
| SmallRig 4391 | Universal cage with cold shoe mounts |
| Ulanzi MA37 | Magnetic quick-attach system |

### Storage (USB UAS)
| Device | Speed | Notes |
|--------|-------|-------|
| Samsung T9 SSD | 2000 MB/s | USB 3.2 Gen 2x2 |
| SanDisk Pro-G40 SSD | 2000 MB/s | Rugged, USB 3.2 Gen 2x2 |
| WD My Passport SSD | 1050 MB/s | Compact USB 3.2 Gen 2 |

---

## Required KSU Modules

Install via SukiSU Ultra Manager after flashing this kernel:

| Module | Purpose | Link |
|--------|---------|------|
| SUSFS module | Root hiding (mandatory) | https://github.com/sidex15/ksu_module_susfs |
| ZygiskNext | Zygisk framework | https://github.com/Dr-TSNG/ZygiskNext |
| Zygisk-Assistant | Zygisk utilities | https://github.com/snake-4/Zygisk-Assistant |
| PlayIntegrityFix | Pass Play Integrity | https://github.com/chiteroman/PlayIntegrityFix |
| liboemcryptodisabler | DRM L1 (streaming) | https://github.com/hzy132/liboemcryptodisabler |
| magisk-module-wifi7 | WiFi 7 support | https://github.com/AndroPlus-org/magisk-module-wifi7 |

---

## Cinema Apps — Notes

### FiLMiC Pro
- Requires Camera2 API Level 3 (ishtar: confirmed Level 3)
- **LogV3**: 10-bit SDR log curve — use FiLMiC LogV3 LUT in DaVinci Resolve
- Manual controls: ISO, shutter angle, focus, white balance, zoom speed
- Cinematographer Kit add-on: histogram, false color, zebras, waveform
- Supports all 4 cameras with seamless switching

### ProCam X
- 14-bit DNG RAW capture from IMX989 directly
- DNG files contain Adobe lens profile for Leica optics
- Burst RAW for action sequences
- Manual focus peaking overlay

### MCPro+
- Multi-camera simultaneous recording
- Anamorphic mode with 1.33x/1.5x/2x de-squeeze
- Real-time LUT preview during recording

---

## Flashing Instructions

1. Flash a compatible HyperOS ROM for ishtar (Xiaomi 13 Ultra)
2. Boot to recovery, flash this AnyKernel3 zip
3. Install SukiSU Ultra Manager APK
4. Install SUSFS module in SukiSU Manager
5. Install ZygiskNext + PlayIntegrityFix
6. Reboot — verify with SukiSU Manager
7. Use **HorizonKernelFlasher** for future flashes: https://github.com/libxzr/HorizonKernelFlasher

> Note: Old Kernel Flasher app is broken with latest SUSFS. Use HorizonKernelFlasher.

---

## Build Info

| Component | Details |
|-----------|---------|
| Kernel source | MiCode/Xiaomi_Kernel_OpenSource (branch ishtar-t-oss) |
| Kernel version | 5.15.x GKI |
| Architecture | ARM64 |
| Compiler | LLVM Clang r536225, ThinLTO |
| Root option A | SukiSU Ultra (SukiSU_SUSFS.yml) |
| Root option B | KernelSU Next (KernelSU-Next.yml) |
| SUSFS branch | gki-android13-5.15 |
| Flash format | AnyKernel3 zip |
| CI/CD | GitHub Actions (ubuntu-22.04) |

Artifacts tagged: `Ishtar-Cinema-SukiSU_Ultra-v{DATE}.{RUN}` / `Ishtar-Cinema-KernelSU_Next-v{DATE}.{RUN}`

---

## See Also

- [`Kernel/cinema/cinema_configs_explained.txt`](Kernel/cinema/cinema_configs_explained.txt) — every cinema CONFIG flag explained
- [`Kernel/cinema/kpm_modules_guide.md`](Kernel/cinema/kpm_modules_guide.md) — KPM modules for camera HAL patches
- [`Kernel/cinema/lut_workflow.md`](Kernel/cinema/lut_workflow.md) — color grading: RAW/Log to DCI-P3

---

## Credits

- **SukiSU Ultra**: [udochina](https://github.com/SukiSU-Ultra/SukiSU-Ultra)
- **KernelSU**: [tiann](https://github.com/tiann/KernelSU)
- **KernelSU-Next**: [rifsxd](https://github.com/KernelSU-Next/KernelSU-Next)
- **Magic-KSU**: [5ec1cff](https://github.com/5ec1cff/KernelSU)
- **SUSFS**: [simonpunk](https://gitlab.com/simonpunk/susfs4ksu)
- **SUSFS Module**: [sidex15](https://github.com/sidex15)
- **SukiSU patch**: [ShirkNeko](https://github.com/ShirkNeko/SukiSU_patch)
- **AnyKernel3**: [WildPlusKernel](https://github.com/WildPlusKernel/AnyKernel3)
- **Xiaomi kernel source (ishtar-t-oss)**: [MiCode](https://github.com/MiCode/Xiaomi_Kernel_OpenSource/tree/ishtar-t-oss)
- **HorizonKernelFlasher**: [libxzr](https://github.com/libxzr/HorizonKernelFlasher)
