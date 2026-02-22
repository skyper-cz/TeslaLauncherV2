# 🚗 TeslaLauncher V2

[![Android](https://img.shields.io/badge/Android-10.0%2B-3DDC84?logo=android)](#)
[![Version](https://img.shields.io/badge/Version-1.3.6-blue)](#)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

A modern, clean, and blazing-fast Car Launcher for Android head units and tablets. Inspired by the minimalist and intuitive design of Tesla infotainment systems. This project seamlessly integrates navigation, live OBD2 telemetry, and media controls into a single, distraction-free interface.

*(Insert a photo of the app running in your car or a tablet screenshot here)*
![Screenshot App](docs/screenshot1.jpg) 

---

## 🚀 Latest Update: v1.3.6 (The Athena Update)

This update transforms the launcher into a fully functional Dashcam. We've introduced a true Background Camera Engine, smart USB role assignment, and an all-new swipeable dashboard.

## 📹 Dashcam & Background Engine
* **Background Camera Engine:** The USB camera now runs continuously and invisibly beneath the map layer. It seamlessly jumps to the foreground only when reverse (R) is engaged.
* **Loop Recording (Dashcam):** Added continuous background video recording (H.264, MP4). It automatically manages storage with a 1GB rolling loop, deleting the oldest files to prevent memory overflow.
* **Smart Role Assignment:** Upon plugging in a new USB camera, a system dialog instantly prompts you to assign it a permanent role (`FRONT` for Dashcam, `REAR` for Reverse).

## 🎛️ UI & UX Improvements
* **Swipeable Dashboard:** The main instrument cluster is now a paginated view. Swipe left/right to reveal a dedicated, animated Dashcam Control Panel.
* **Experimental Menu:** Added a new "Experimental Features" section in Settings with a Master Switch to safely toggle heavy features like background recording and prevent accidental battery drain.

## ✨ Key Features

* 🗺️ **Dual Map Engine (Mapbox & Google Maps):** * Seamlessly switch between Google Maps (for standard online routing) and Mapbox (hybrid view with 3D buildings).
  * **Smart Offline Maps:** Download a ~100 km radius around your current GPS location directly to local storage with a single tap.
* 🏎️ **Live OBD2 Telemetry:** * Real-time reading of speed, RPM, and coolant temperature via Bluetooth.
  * Built-in *Auto-Reconnect* watchdog automatically restores the connection if the adapter drops out.
* 🎵 **Smart Media Dock:** * Displays the current track title, artist, and album art (supports Spotify, Apple Music, YouTube Music, etc.).
  * Oversized touch controls designed for safe operation while driving with continuous marquee scrolling for long titles.
* 🌙 **Night Panel:** A minimalist mode for nighttime highway driving. Dims the maps and displays only a giant speedometer and critical engine warnings.
* 📱 **Full-Featured Launcher:** Use it as a standalone app or set it as your default Android home screen (includes a custom *App Drawer* for all installed apps).
* 📷 **Reverse Camera Support:** Ready for UVC USB camera integration automatically triggered when shifting into Reverse (R).
* 📷 **Dascam mode Support:** Ready for UVC USB camera integration automatically recording while driving.
* **Note** That in current version the Dashcam mode and Reverse camera mode cannot work at the same time.

---

## 🛠 Recommended Hardware

The application works on most Android devices, but for the best experience, we recommend:
1. **Device:** Android tablet or car head unit (Android 8.0+ / API 26+).
2. **OBD2 Adapter:** Standard Bluetooth adapter (a reliable ELM327 v1.5 chip is highly recommended).
3. **Parking Camera:** Any standard USB webcam (UVC standard compatible).

---

## 📥 Installation (For regular users)

Don't want to compile the code? Just download the pre-built application:
1. Go to the [Releases](../../releases) section and download the latest `TeslaLauncherV2.apk` file.
2. Transfer the APK via a USB drive or download it directly using your head unit's web browser.
3. Open the file and select "Install".
4. On the first launch, grant the necessary permissions for Location (for the speedometer and maps) and Notification Access (for the media player to read track data).
5. Open Settings inside the app and enter your Bluetooth OBD2 adapter's MAC address.

---

## 💻 Building from Source (For developers)

This project is built entirely in **Kotlin** using modern **Jetpack Compose** architecture. 

### How to run the project:
1. Clone this repository:
   ```bash
   git clone https://github.com/YourUsername/TeslaLauncherV2.git
2. Open the project in Android Studio

3. ⚠️ IMPORTANT: Missing API Keys (local.properties)
For security reasons, API keys are not committed to the repository. To make the map engines compile and work, you must create a local.properties file in the root directory of the project and add your own Mapbox and Google Maps keys:

Properties:
* MAPBOX_DOWNLOADS_TOKEN=sk.eyJ1I... (Your secret Mapbox token)
* MAPBOX_ACCESS_TOKEN=pk.eyJ1I... (Your public Mapbox token)
* MAPS_API_KEY=AIzaSy... (Your Google Maps API Key)
4. Sync the Gradle files, build, and run the project!
