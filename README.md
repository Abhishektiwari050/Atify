<div align="center">

  <h1>🌊 Atify</h1>

  <p align="center">
    <strong>Your music. Your atmosphere. Multi-source Android music player with lossless FLAC, Spotify sync, and Android Auto.</strong>
    <br />
    <em>Connect your Spotify account. Stream Bit-Perfect FLAC & 320kbps AAC, YouTube audio matching, offline downloads, DJ crossfade, and native Android Auto car dashboard support.</em>
  </p>

  <p align="center">
    <a href="#-features"><b>Features</b></a> •
    <a href="#-download"><b>Download</b></a> •
    <a href="#-android-auto-setup"><b>Android Auto</b></a> •
    <a href="#-earthy-palette"><b>Design</b></a> •
    <a href="#-build-from-source"><b>Build</b></a> •
    <a href="#-license"><b>License</b></a>
  </p>

  <div align="center">
    <a href="https://github.com/Abhishektiwari050/Atify/releases/latest">
      <img src="https://img.shields.io/github/v/release/Abhishektiwari050/Atify?style=for-the-badge&color=6F8067&labelColor=18251F&logo=github" alt="Latest Release" />
    </a>
    <img src="https://img.shields.io/badge/Architecture-MVVM-6F8067?style=for-the-badge&labelColor=18251F&logo=kotlin" alt="MVVM Architecture" />
    <img src="https://img.shields.io/badge/Toolkit-Jetpack_Compose-6F8067?style=for-the-badge&logo=jetpack-compose&labelColor=18251F" alt="Jetpack Compose" />
    <img src="https://img.shields.io/badge/Android_Auto-Supported-6F8067?style=for-the-badge&logo=android-auto&labelColor=18251F" alt="Android Auto" />
    <img src="https://img.shields.io/badge/Android-8.0%2B-6F8067?style=for-the-badge&logo=android&labelColor=18251F" alt="Android 8.0+" />
    <img src="https://img.shields.io/badge/License-GPLv3-6F8067?style=for-the-badge&labelColor=18251F" alt="License: GPLv3" />
  </div>

</div>

<hr />

## 🌟 Highlights

**Atify** combines full Spotify library synchronization with lossless FLAC audio backends and YouTube audio fallback into a unified, 100% ad-free experience. 

* 🚗 **Native Android Auto:** Browse your Liked Songs, Playlists, and Downloads directly from your car touchscreen with Google Assistant voice control.
* 🎧 **Bit-Perfect Lossless FLAC:** True 16-bit / 44.1kHz FLAC audio streaming via multi-tier backends.
* 🔒 **100% Ad-Free & $0 Subscriptions:** On-demand playback and unlimited skips with zero subscriptions required.
* 🌿 **Earthy Palette & Spotify UI:** Authentic Spotify-style dark theme with curated natural tones (`#18251F` Dark Forest, `#6F8067` Sage, `#D8C7A8` Sand).
* 📥 **Offline Downloads & Local Export:** Cache albums and playlists to storage (`Music/Atify/`) for data-free driving.
* 🎛️ **DJ-Style Crossfade & Synced Lyrics:** Seamlessly blend tracks with zero silence and follow live karaoke-style lyrics.

---

## 📲 Download

Grab the latest APK directly from GitHub Releases:

<div align="center">

| Architecture | Package | Direct Download |
|---|---|---|
| **ARM64 (Recommended)** | `Atify-arm64-v8a.apk` | [**Download ARM64**](https://github.com/Abhishektiwari050/Atify/releases/download/v1.0.0/Atify-arm64-v8a.apk) |
| **Universal (All Phones)** | `Atify-universal.apk` | [**Download Universal**](https://github.com/Abhishektiwari050/Atify/releases/download/v1.0.0/Atify-universal.apk) |

</div>

---

## 🚗 Android Auto Setup

To display Atify on your vehicle's Android Auto dashboard:

1. **Install Atify** on your Android device.
2. Open your phone's **Settings** $\rightarrow$ Search for **Android Auto**.
3. Scroll to the bottom and tap **Version** **10 times** to unlock Developer Mode.
4. Tap the **3 dots** (top right) $\rightarrow$ **Developer settings** $\rightarrow$ Turn on **Unknown sources**.
5. Connect your phone to your car via USB or Wireless Android Auto — **Atify** will appear on your car display with full browsing and transport controls.

---

## 🎨 Earthy Palette Design

Atify features an organic, atmospheric color scheme designed for easy viewing during both day and night driving:

* **Dark Forest (`#18251F`):** Deep background container.
* **Sage Green (`#6F8067`):** Primary interactive accent and indicators.
* **Terracotta (`#B86F52`):** Warm secondary highlights.
* **Warm Sand (`#D8C7A8`):** Progress sliders and scrubbers.
* **Cream (`#F5F1E8`):** High-contrast typography and vector symbols.

---

## 🛠️ Build from Source

### Prerequisites
* **JDK 21 LTS**
* **Android SDK 37**

### Clone & Build
```bash
# Clone the repository
git clone https://github.com/Abhishektiwari050/Atify.git
cd Atify

# Build Debug APK
./gradlew assembleDebug

# Build Release APK
./gradlew :app:assembleRelease --no-daemon
```

Output APKs will be located at `app/build/outputs/apk/debug/`.

---

## 📜 License

Atify is licensed under the [GNU General Public License v3.0](LICENSE).
