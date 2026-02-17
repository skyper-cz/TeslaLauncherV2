# 🚗 TeslaLauncher V2

[![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android)](#)
[![Version](https://img.shields.io/badge/Version-1.2.0-blue)](#)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

A modern, clean, and blazing-fast Car Launcher for Android head units and tablets. Inspired by the minimalist and intuitive design of Tesla infotainment systems. This project seamlessly integrates navigation, live OBD2 telemetry, and media controls into a single, distraction-free interface.

*(Insert a photo of the app running in your car or a tablet screenshot here)*
![Screenshot App](docs/screenshot1.jpg) 

---

## 🚀 Latest Update: v1.2.0 (The Odyssey Update)
This major release brings critical architectural improvements, flawless multi-step navigation routing, and pixel-perfect UI adaptation for all display types.

* **Massive Codebase Refactoring:** Split the monolithic main file into 4 logical modules (`MainActivity`, `Components`, `MapComponents`, `Overlays`) for drastically improved performance and maintainability.
* **Instruction Chaining:** The navigation engine now correctly parses the full route array from Mapbox/Google APIs. The system automatically transitions to the next maneuver once the vehicle is within 30 meters of a node.
* **Live Distance Countdown:** Distance to the next turn now counts down smoothly in real-time based on live GPS coordinates.
* **Responsive Layout Support:** The dashboard now perfectly adapts to Landscape and Portrait orientations, dynamically resizing the media player, shifting elements, and preventing UI overlaps.
* **Reverse Gear Lock:** Manual "R" shifts are now protected. The automated OBD logic will no longer override reverse back into "P" when speed drops below 4 km/h.
* **DTC Engine Diagnostics:** The ELM327 parser now polls for engine trouble codes (`03` command) every 10 seconds and displays active fault codes (e.g., P0171) directly on the status bar.

---

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
