# POX PROJECT - Begoker Torch (Quick Settings Tile & Control Center)

[![Build and Package APK](https://github.com/POX-project/begotorch/actions/workflows/build.yml/badge.svg)](https://github.com/POX-project/begotorch/actions/workflows/build.yml)
[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)
[![Latest Release](https://img.shields.io/github/v/release/POX-project/begotorch?color=brightgreen)](https://github.com/POX-project/begotorch/releases)

**Maintainer:** **`txorav`** • **Platform:** Android 8.0+ (AOSP / MIUI / HyperOS / LineageOS)

A native Android system extension providing an **AOSP Quick Settings Tile** and an **in-shade non-collapsing intensity slider** for devices running the **Begoker Kernel** (Redmi Note 8 Pro / MT6785 / MT6360).

---

## 🌟 Key Features

* **Native Quick Settings Tile (`BegotorchTileService`)**:
  * Tap: Toggles flashlight on/off with state memory.
  * Interactive In-Shade Popup: Opens the intensity slider dialog directly over the control center **without collapsing the notification shade** (matching Android 12+ Internet / Volume panels).
* **Non-Collapsing System Overlay (`FlashlightStrengthOverlay`)**:
  * Employs accessibility and alert window presentation so adjustments feel instant and fluid.
* **Apple iOS & Material 3 Dual UI Styles**:
  * Authentic capsule slider with interactive light beam dragging and haptics.
  * Material 3 tonal cards with real-time mA current telemetry.
* **Direct Hardware SysFS & Rootless Kernel Integration**:
  * Communicates directly with Begoker kernel rootless sysfs nodes (`0666`):
    * `/proc/perfmgr/torch_brightness`
    * `/proc/perfmgr/flashlight_brightness`
    * `/sys/kernel/torch_brightness`
    * `/sys/class/leds/flashlight/brightness`
  * Graceful fallback to `CameraManager.setTorchMode` on non-Begoker kernels.

---

## ⚡ Hardware Stops & Current Specifications

| Stop Level | Selector | Current per Channel | Dual LED Total | Mode Title | Description |
| :---: | :---: | :---: | :---: | :---: | :---: |
| **0** | 0 | 0 mA | 0 mA | **Off** | Flashlight turned off |
| **1** | 0 | 25 mA | 50 mA | **Bedside** | Dim reading glow |
| **2** | 2 | 50 mA | 100 mA | **Soft** | Soft indoor illumination |
| **3** | 4 | 75 mA | 150 mA | **Low** | Ambient lighting |
| **4** | 6 | 100 mA | 200 mA | **Stock Standard** | Xiaomi factory stock limit |
| **5** | 9 | 138 mA | 275 mA | **Medium** | Enhanced indoor beam |
| **6** | 12 | 175 mA | 350 mA | **Bright** | Night walking beam |
| **7** | 15 | 213 mA | 425 mA | **High** | High-power outdoor light |
| **8** | 18 | 250 mA | 500 mA | **Very High** | Spotlight power |
| **9** | 21 | 288 mA | 575 mA | **Ultra** | Ultra searchlight |
| **10** | 24 | 325 mA | 650 mA | **Max Overdrive** | Dual LED peak hardware overdrive |

---

## 🛠️ Building

To build the APK locally, run:

```bash
./gradlew assembleRelease
# Or for debug build:
./gradlew assembleDebug
```

Compiled APKs will be located under `app/build/outputs/apk/`.

---

## 📥 Installation

1. Download the latest `begotorch-*.apk` from [GitHub Releases](https://github.com/POX-project/begotorch/releases).
2. Install the APK on your device:
   ```bash
   adb install -r begotorch.apk
   ```
3. Pull down your Quick Settings shade, tap the **Edit / Pencil** icon, and drag the **Begoker Torch** tile into your active tiles.
4. (Optional for non-collapsing overlay) Grant display over other apps or accessibility permission if prompted.

---

## 🇵🇸 Solidarity
**FREE PALESTINE** — Support justice, end oppression, and spread awareness on all matters of shared humanity.
