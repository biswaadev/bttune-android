<p align="center">
  <img src="assets/bttune_logo.svg" width="120" height="120" alt="BTTUNE Logo" />
</p>

<h1 align="center">BTTUNE</h1>

<p align="center">
  <b>High-Performance, Ad-Free YouTube Music Streaming Client for Android</b>
</p>

<p align="center">
  <a href="https://github.com/biswaadev/bttune-android/releases/latest"><img src="https://img.shields.io/github/v/release/biswaadev/bttune-android?style=flat-square&color=2563EB&label=Release&logo=android" alt="Latest Release"></a>
  <a href="https://github.com/biswaadev/bttune-android/releases"><img src="https://img.shields.io/github/downloads/biswaadev/bttune-android/total?style=flat-square&color=4F46E5&label=Downloads" alt="Downloads"></a>
  <a href="https://kotlinlang.org"><img src="https://img.shields.io/badge/Language-Kotlin-7C3AED?style=flat-square&logo=kotlin&logoColor=white" alt="Kotlin"></a>
  <a href="https://developer.android.com/jetpack/compose"><img src="https://img.shields.io/badge/UI-Jetpack%20Compose-059669?style=flat-square&logo=jetpackcompose&logoColor=white" alt="Jetpack Compose"></a>
  <img src="https://img.shields.io/badge/License-GPL%203.0-374151?style=flat-square" alt="License">
</p>

---

## ◈ Overview

BTTUNE is an open-source, subscription-free music streaming application engineered for Android. Built entirely with **Jetpack Compose**, **Kotlin Coroutines**, and an optimized low-latency audio pipeline, it interfaces with YouTube Music to deliver seamless playback, synchronized lyrics, spatial DSP audio processing, and local caching without telemetry or advertisements.

---

## ◈ Architecture & Highlights

| Category | Specification |
| :--- | :--- |
| **Platform** | Native Android (API 26+) |
| **Language** | Kotlin 2.0+ with Coroutines & StateFlow |
| **User Interface** | 100% Declarative Jetpack Compose & Material 3 |
| **Audio Engine** | ExoPlayer / Media3 with 8D & Spatial DSP Processors |
| **Lyrics Provider** | Real-time syllable sync via LRCLIB, Musixmatch & BetterLyrics |
| **Privacy** | Zero telemetry, zero external trackers, encrypted local storage |

---

## ◈ Capabilities

### ◆ Streaming & Playback Pipeline
- **Ad-Free Engine** — Stream millions of tracks, albums, and playlists without interruption.
- **Background Playback** — Native foreground service providing continuous playback during screen-off and multitasking.
- **Dynamic Autoplay** — Machine-learning recommendations that automatically queue related tracks.
- **Smart Audio Cache** — Streamed audio blocks are cached locally to reduce network data usage on replay.

### ◆ Interface & Material 3 Theming
- **Dynamic Color Harmony** — Adaptive Material You color extraction from system wallpaper and artwork.
- **Custom Player Canvases** — Switch between Pure AMOLED Black, Liquid Glass, Neon Glow, and Vinyl turntable views.
- **System-Level Integration** — Rich MediaStyle notification actions, lock screen controls, and Always-On Display (AOD).

### ◆ Lyrics & Vocal Synchronization
- **Syllable-Level Tracking** — Time-accurate word-by-word karaoke highlighting.
- **Multi-Source Fallback** — Automatic aggregation across global lyric repositories.
- **AI Translation & Romanization** — In-line pronunciation guides and multi-language translation.
- **Lyric Canvas Export** — Generate shareable typography cards for social platforms.

### ◆ Studio DSP & Acoustic Processing
- **8D & Spatial Soundstage** — Virtual surround sound mapping tailored for stereo headphones.
- **Parametric Equalizer** — Multi-band frequency adjustment with custom acoustic presets and bass boost.
- **Gapless DJ Automix** — Configurable crossfade handoff curves for continuous song transitions.
- **USB Bit-Perfect Bypass** — Direct driver bypass engineered for external audiophile USB DAC hardware.

### ◆ Storage & Media Tools
- **Offline Storage** — High-speed direct downloading of tracks and albums for offline listening.
- **Integrated Ringtone Trimmer** — Waveform audio cutter to produce custom ringtones directly from downloads.
- **Local Media Indexing** — Scan, organize, and play audio files already stored on device storage.

### ◆ Utilities & Discovery
- **Acoustic Music Recognition** — Real-time audio fingerprinting to identify ambient music.
- **Listen Together** — Low-latency synchronized group streaming rooms.
- **Playback Analytics** — Personal listening stats, top artists, and streaming trends.
- **Encrypted Backup** — Complete library and preferences export via local storage or cloud sync.

---

## ◈ Installation

Download the signed production build directly from GitHub:

1. Navigate to the **[Releases](https://github.com/biswaadev/bttune-android/releases/latest)** portal.
2. Download the latest `BTTUNE-release.apk`.
3. Install on any device running **Android 8.0 (API 26)** or higher.

---

## ◈ Developers & Contributors

### Core Developers
- **Biswajit Tanti** ([@biswaadev](https://github.com/biswaadev)) — Lead Developer & Architect  
  *Website:* [biswadev.vercel.app](https://biswadev.vercel.app/) &nbsp;|&nbsp; *WhatsApp:* [+919365015437](https://wa.me/919365015437)
- **[bitzzdev](https://github.com/bitzzdev)** — Developer

### Contributors
- ** Trishant tanti** — Contributor

---

## ◈ Support & Feedback

- **Telegram:** [@freek311](https://t.me/freek311)
- **Bug Reports & Issues:** [GitHub Issue Tracker](https://github.com/biswaadev/bttune-android/issues)

---

<p align="center">
  <sub>BTTUNE Open Source Project • Licensed under GPL 3.0</sub>
</p>
