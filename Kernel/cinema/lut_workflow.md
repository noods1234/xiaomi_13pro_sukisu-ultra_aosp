# Color Grading Workflow — Xiaomi 13 Ultra Cinema

## Overview

The Xiaomi 13 Ultra captures in two main formats useful for cinema:
1. **14-bit RAW DNG** — full sensor data via ProCam X / Open Camera
2. **FiLMiC LogV3** — proprietary log curve in 10-bit SDR via FiLMiC Pro

Both formats require color grading in post. This guide covers the full workflow
from capture to DCI-P3 / Rec.709 deliverable.

---

## Format Comparison

| Format | Bit Depth | Dynamic Range | File Size | App |
|--------|-----------|--------------|-----------|-----|
| 14-bit RAW DNG | 14-bit | ~13-14 stops | ~25MB/frame | ProCam X |
| FiLMiC LogV3 10-bit | 10-bit | ~12 stops | ~400MB/min @4K | FiLMiC Pro |
| HEVC 8-bit (stock) | 8-bit | ~10 stops | ~150MB/min @4K | Stock Camera |

---

## Workflow A: RAW DNG (ProCam X)

### 1. Capture Settings (ProCam X)

```
Format:       RAW + JPEG (for reference)
Bit depth:    14-bit DNG
ISO:          50–800 (IMX989 base ISO: ~50)
Shutter:      180° rule: 1/(2×fps)
White balance: Manual (set on location, do not change mid-shot)
Lens:         Select based on focal length needed
              Wide: f/1.9 equivalent
              75mm: portrait, subject isolation
              120mm: telephoto compression
```

### 2. Ingest to DaVinci Resolve

```
1. Import DNG files to Media Pool
2. Right-click clip → Clip Attributes → Camera RAW
3. Select "Override" mode
4. Set Color Space: Xiaomi RAW (if available) or "Camera Native"
5. Alternatively: use Adobe DNG SDK — Resolve supports embedded
   lens profile and color correction metadata from IMX989 DNG
```

### 3. Apply Xiaomi/IMX989 Input Transform

The IMX989 DNG files contain Adobe-calibrated color matrices.
Resolve will auto-apply these if you use "Auto Color Science".

For manual control:
```
ACES workflow:
  Input Transform: Camera Native to ACEScct
  Output Transform: ACEScct to P3-D65 (DCI) or Rec.709

Non-ACES (Color Managed):
  Input Color Space: Camera Native / P3-D65
  Output Color Space: Rec.709 or DCI-P3
```

### 4. Grade

- Use Lift/Gamma/Gain wheels for broad strokes
- Use curves for fine highlight and shadow rolloff
- Emulate film: slight green/blue in shadows, warm highlights
- IMX989 shadows are very clean — push exposure 1-2 stops in post safely

---

## Workflow B: FiLMiC LogV3 (FiLMiC Pro)

### 1. Capture Settings (FiLMiC Pro)

```
Resolution:   4K UHD (3840x2160)
Frame rate:   24fps (cinema), 25fps (PAL broadcast), or 30fps
Codec:        H.265 HEVC (higher quality per bit)
Bitrate:      200+ Mbps (FiLMiC Pro max on 13 Ultra)
Color:        FiLMiC LogV3 (enable in Color Profile settings)
Audio:        48kHz / 24-bit, USB or 3.5mm input
```

### 2. FiLMiC LogV3 LUT

FiLMiC provides official LogV3 LUTs for DaVinci Resolve:

Download from: https://www.filmicpro.com/logv3-luts/

Files:
- `FiLMiCLogV3_to_Rec709.cube` — standard delivery
- `FiLMiCLogV3_to_P3-D65.cube` — DCI-P3 cinema
- `FiLMiCLogV3_to_ACEScct.cube` — ACES pipeline input

### 3. Apply in DaVinci Resolve

```
Method 1 — Node-based LUT:
  1. Import .cube file to Media Pool / LUTs folder
  2. In Color page, right-click first node
  3. LUTs → select FiLMiCLogV3_to_Rec709.cube
  4. Grade on subsequent nodes (creative grade goes AFTER LUT)

Method 2 — Color Managed:
  1. Project Settings → Color Management
  2. Input Color Space: FiLMiC LogV3 (add as custom space with LUT)
  3. Timeline Color Space: DaVinci Wide Gamut Intermediate
  4. Output Color Space: Rec.709 or P3-D65
```

### 4. Creative Grade

After applying the technical LUT:
- S-curve for contrast (don't clip highlights/shadows)
- Selective color: skin tones, sky, foliage
- Film grain: add 10-20% grain for organic texture
- Vignette: subtle 10-15% edge darkening

---

## Delivery Specs

### Cinema / Festival DCP
```
Color space: DCI-P3 / XYZ
Resolution: 4096x2160 (4K DCI) or 2048x1080 (2K DCI)
Frame rate: 24fps
Bit depth: 12-bit
Container: MXF (J2K codec)
Audio: 48kHz / 24-bit, 5.1 or 7.1
```

### Streaming (YouTube / Netflix)
```
Color space: Rec.709 (SDR) or Rec.2020/PQ (HDR)
Resolution: 3840x2160 (4K UHD)
Frame rate: 24/25/30fps
Bit depth: 8-bit (SDR) or 10-bit (HDR)
Codec: H.264 or H.265
Bitrate: 50-100 Mbps for 4K
```

### Broadcast
```
Color space: Rec.709
Resolution: 1920x1080 or 3840x2160
Audio: 48kHz / 24-bit, stereo or 5.1
Container: MXF or QuickTime .mov
```

---

## Recommended LUT Packs for Xiaomi 13 Ultra

| Pack | Style | Format |
|------|-------|--------|
| FiLMiC Pro official | Technical (Log conversion) | .cube |
| Leica Looks (HyperOS built-in) | Vivid / Vibrant / Smooth | Built-in |
| Ground Control LUTs | Cinematic film emulation | .cube |
| IWLTBAP Cinematic | Natural film tones | .cube |
| Koji Advance | Fuji/Kodak film emulation | .cube |

All .cube LUTs import directly into DaVinci Resolve, Premiere Pro, and Final Cut Pro X.

---

## On-Set Monitor Calibration

For accurate exposure monitoring on external monitors:

1. Connect monitor via USB-C → HDMI (DisplayPort alt mode enabled by this kernel)
2. Set phone display output to DCI-P3 in HyperOS display settings
3. Calibrate external monitor to Rec.709 or DCI-P3 depending on delivery
4. Use monitor's false color to expose IMX989 to 1-2 stops above "proper" exposure
   (IMX989 handles highlights well — slightly overexpose for cleaner shadows)
5. Set zebras at 90% IRE to protect highlights

---

## Timecode & Multi-Camera Sync

For multi-camera shoots with multiple 13 Ultra units or mixed camera setups:

1. **Audio slate**: Record a sharp clap at start of each take
2. **Timecode app**: Timecode Systems "Sync-E" app (iOS/Android) for LTC timecode over 3.5mm
3. **Plural Eyes**: Auto-sync in post using audio waveforms
4. **Manual sync**: Use FiLMiC Pro's frame-accurate timecode overlay

The `CONFIG_HZ_1000` and `CONFIG_HIGH_RES_TIMERS` in this kernel reduce audio
timing jitter, improving automatic sync accuracy in Plural Eyes.
