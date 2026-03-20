# KPM Modules Guide — Xiaomi 13 Ultra Cinema

KPM (Kernel Patch Manager) is a SukiSU Ultra feature that allows loading custom
kernel modules at runtime without modifying the kernel binary. This guide covers
cinema-relevant KPM use cases for the Xiaomi 13 Ultra (ishtar).

---

## What KPM Can Do for Cinema

| Use Case | KPM Module Approach |
|----------|-------------------|
| Unlock hidden camera modes | Patch `camera.provider@2.7` HAL in memory |
| Force Camera2 Level 3 | Patch `libcameraservice.so` capability flags |
| Enable log/flat color profile | Intercept ISP tuning parameters |
| Unlock higher video bitrates | Patch MediaCodec bitrate caps |
| Force 10-bit output | Patch display composer flags |
| Disable camera watermarks | Hook watermark injection code |
| Override max ISO | Patch CameraCharacteristics metadata |

---

## KPM Module Structure

A KPM module is a kernel ELF object loaded via SukiSU Manager.

### Minimal module skeleton

```c
// my_cinema_module.c
#include <kpm/kpm.h>

KPM_NAME("cinema-patch");
KPM_VERSION("1.0.0");
KPM_LICENSE("GPL");
KPM_DESCRIPTION("Cinema patches for Xiaomi 13 Ultra");

// Called when module loads
static int cinema_init(const char *args, const char *event, void *reserved) {
    pr_info("[cinema-kpm] loaded with args: %s\n", args ? args : "none");
    // Your patches here
    return 0;
}

// Called when module unloads
static void cinema_exit(void *reserved) {
    pr_info("[cinema-kpm] unloaded\n");
}

KPM_INIT(cinema_init);
KPM_EXIT(cinema_exit);
```

---

## Key KPM APIs for Camera Patches

### 1. `kfunc_hook_func` — Hook a userspace function

Hooks a function in a shared library loaded into a target process.

```c
// Hook camera HAL function to override video bitrate cap
static int (*orig_get_max_bitrate)(void *cam, int32_t *bitrate);

static int hooked_get_max_bitrate(void *cam, int32_t *bitrate) {
    int ret = orig_get_max_bitrate(cam, bitrate);
    // Remove 100Mbps cap — allow up to 800Mbps
    if (*bitrate < 800000000) {
        *bitrate = 800000000;
    }
    return ret;
}

// In init:
kfunc_hook_func(
    "camera.qcom",              // process name
    "libcamera_client.so",      // target library
    "getMaxVideoBitrate",       // function name
    (void *)hooked_get_max_bitrate,
    (void **)&orig_get_max_bitrate
);
```

### 2. `kallsyms_lookup_name` — Find kernel symbols

```c
// Find a kernel function by name
void *addr = kallsyms_lookup_name("v4l2_subdev_call");
```

### 3. `kpm_map_region` — Read/write process memory

```c
// Read a value from camera HAL memory
uint32_t flags;
kpm_map_region(pid, camera_hal_addr, &flags, sizeof(flags), KPM_READ);

// Patch camera flags
flags |= CAMERA2_LEVEL3_FLAG;
kpm_map_region(pid, camera_hal_addr, &flags, sizeof(flags), KPM_WRITE);
```

---

## Cinema-Relevant Patch Ideas

### Patch 1: Unlock 10-bit video recording

Xiaomi 13 Ultra supports 10-bit HEVC but it may be locked in some ROM versions.

```
Target: /system/lib64/libstagefright.so
Symbol: MediaCodec::configure
Patch: Override profile/level check to allow HEVC Main10 Profile
```

### Patch 2: Increase video bitrate ceiling

Stock may cap H.265 recording at 100-200 Mbps.

```
Target: /system/lib64/libcamera2ndk.so or camera HAL
Symbol: bitrate validation function
Patch: Remove upper clamp, allow 400-800 Mbps for RAW-equivalent quality
```

### Patch 3: Remove camera watermark (Leica branding)

Some HyperOS builds inject Leica watermark into recorded video.

```
Target: /vendor/lib64/libmicamera_filter.so (varies by ROM)
Patch: NOP the watermark injection call
```

### Patch 4: Expose all ISO values to Camera2 API

The stock HAL may limit Camera2 ISO range vs what the sensor supports.

```
Target: camera characteristics metadata in HAL
Symbol: CameraMetadata::update for ANDROID_SENSOR_INFO_SENSITIVITY_RANGE
Patch: Override min/max to [50, 12800]
```

---

## Building a KPM Module

### Prerequisites

```bash
# Clone SukiSU-Ultra for KPM headers
git clone https://github.com/SukiSU-Ultra/SukiSU-Ultra.git
cd SukiSU-Ultra/tools/kpm
```

### Build

```bash
# Cross-compile for arm64
export CROSS_COMPILE=aarch64-linux-gnu-
make ARCH=arm64 -C /path/to/kernel M=/path/to/your/module modules
```

Output: `my_cinema_module.ko`

### Load via SukiSU Manager

1. Open SukiSU Manager
2. Go to KPM tab
3. Tap "+" and select your `.ko` file
4. Optionally provide `args` string (passed to `cinema_init`)
5. Module loads immediately

---

## Pre-built Community KPM Modules

Search these repos for ready-to-use cinema patches:

- https://github.com/SukiSU-Ultra/SukiSU-Ultra/tree/main/tools/kpm
- XDA Developers — search "KPM module camera ishtar"
- Telegram: @SukiSU_Ultra_Community

---

## Important Notes

- KPM modules run in kernel space — a bad module can kernel panic
- Test on a secondary ROM partition if possible
- SUSFS root hiding still applies — modules are hidden from integrity checks
- Module state is lost on reboot unless you set "auto-load" in SukiSU Manager
- The SukiSU KPM ABI may change between SukiSU versions — rebuild when updating kernel
