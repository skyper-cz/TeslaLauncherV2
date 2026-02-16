# 🚗 TeslaLauncher V2

[![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android)](#)
[![Version](https://img.shields.io/badge/Version-1.0.0-blue)](#)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

Moderní, čistý a bleskově rychlý Car Launcher pro Android autorádia a tablety. Inspirováno minimalismem a přehledností systémů ve vozech Tesla. Projekt spojuje navigaci, OBD2 telemetrii a ovládání hudby do jedné bezpečné obrazovky.

*(Zde vlož fotku aplikace běžící ve tvém autě nebo screenshot z tabletu)*
![Screenshot App](docs/screenshot1.jpg) 

---

## ✨ Hlavní funkce

* 🗺️ **Dvojitý Mapový Engine (Mapbox & Google Maps):** * Plynulé přepínání mezi Google Maps (pro online provoz) a Mapbox (hybridní zobrazení s 3D budovami).
  * **Smart Offline Mapy:** Možnost na jedno kliknutí stáhnout okruh ~100 km kolem vaší aktuální GPS polohy do paměti rádia.
* 🏎️ **Živá OBD2 Telemetrie:** * Čtení rychlosti, otáček (RPM) a teploty chladicí kapaliny přes Bluetooth. 
  * Vestavěný *Auto-Reconnect* hlídač, který při ztrátě spojení adaptér automaticky znovu připojí.
* 🎵 **Smart Media Dock:** * Zobrazuje název, interpreta a obal alba aktuálně hrající skladby (podporuje Spotify, Apple Music, YouTube Music atd.).
  * Obří dotyková tlačítka navržená pro bezpečné ovládání za jízdy.
* 🌙 **Night Panel:** Minimalistický režim pro noční jízdy na dálnici. Zhasne mapy a zobrazuje pouze obří rychloměr a důležitá varování z motoru.
* 📱 **Plnohodnotný Launcher:** Aplikaci lze používat jako běžnou aplikaci, nebo ji v Androidu nastavit jako výchozí domovskou obrazovku (obsahuje vlastní *App Drawer* s výpisem všech aplikací).
* 📷 **Podpora parkovací kamery:** Připraveno pro zobrazení UVC USB kamer při zařazení zpátečky (R).

---

## 🛠 Doporučený Hardware

Aplikace funguje na většině Android zařízení, ale pro plný zážitek doporučujeme:
1. **Zařízení:** Android tablet nebo autorádio (Android 8.0 a novější).
2. **OBD2 Adaptér:** Klasický Bluetooth adaptér (ideálně spolehlivý čip ELM327 v1.5).
3. **Parkovací kamera:** Jakákoliv běžná USB webkamera (UVC standard).

---

## 📥 Instalace (Pro běžné uživatele)

Nechcete nic programovat? Stačí si stáhnout hotovou aplikaci:
1. Přejděte do sekce [Releases](../../releases) a stáhněte si nejnovější soubor `TeslaLauncherV2.apk`.
2. Přesuňte APK na USB flashku nebo stáhněte přímo v prohlížeči vašeho autorádia.
3. Otevřete soubor a zvolte "Instalovat".
4. Při prvním spuštění aplikace vás systém požádá o oprávnění k poloze (pro rychloměr a mapy) a upozorněním (pro hudební přehrávač).
5. V menu (Settings) zadejte MAC adresu vašeho Bluetooth OBD2 adaptéru.

---

## 💻 Kompilace ze zdrojových kódů (Pro vývojáře)

Projekt je postaven kompletně v **Kotlinu** s využitím moderního **Jetpack Compose**. 

### Jak projekt rozběhnout:
1. Naklonujte si tento repozitář:
   ```bash
   git clone [https://github.com/VaseJmeno/TeslaLauncherV2.git](https://github.com/VaseJmeno/TeslaLauncherV2.git)
