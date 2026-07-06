# vendor/oiw/nuwa — blob inventory (reference only, Path A prerequisite)

## Licensing warning

**Do not commit extracted vendor blobs to this or any repository.** Everything under this directory is tooling to
extract blobs *locally, from a device you own*, for your own local Path A experimentation. Xiaomi/Qualcomm
proprietary blobs (camera HAL, ISP firmware, display/graphics, audio DSP images, sensor HAL, modem) are not licensed
for redistribution. `extract-files.sh` writes its output to a gitignored local directory only.

## Status

Not currently needed for Path D (the path this project actually implements) — OIWCamera/OIWLauncher run entirely on
public Android APIs against the stock, unmodified vendor partition. This directory exists for future Path A work
only (see `device/oiw/nuwa/README.md`).

## Blob classes (for future Path A reference)

| Class | Subsystem | Redistributable |
|---|---|---|
| `camera.*`, `libcamx*`, `libchi*` | Camera HAL (camx/chi-cdk) | No |
| ISP firmware images | Camera ISP | No |
| `libdisplay*`, `libgralloc*` | Display/graphics | No |
| ADSP/audio DSP firmware | Audio | No |
| Sensor HAL | Sensors | No |
| Thermal/power HAL | Thermal | No |
| USB gadget/host HAL | USB | No |
| Modem/radio images | Radio (optional, not needed for camera work) | No |

## Usage

```sh
./extract-files.sh --serial <adb-serial> --out /path/outside/this/repo/blobs
```
