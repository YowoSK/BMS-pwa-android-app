# KMBE-BMS

KMBE-BMS is an Android client for a Node-RED dashboard. It embeds the dashboard in a WebView and remembers the configured BMS server between launches.

## Requirements

- Android Studio with Android SDK Platform 37
- JDK 11
- A reachable Node-RED instance exposing its dashboard at `/ui/`
- An Android device or emulator running Android 7.0 (API 24) or newer

The Android Gradle Plugin and Kotlin versions are managed in `gradle/libs.versions.toml`. Use the checked-in Gradle wrapper rather than a system Gradle installation.

## Build and test

From the repository root:

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat test
.\gradlew.bat connectedAndroidTest
```

Install the debug build on a connected device with:

```powershell
.\gradlew.bat installDebug
```

The generated APK is written under `app/build/outputs/apk/`.

## Configure a server

On first launch, enter the server host and optional port, for example:

```text
192.168.1.50:1880
```

The app normalizes a host without a scheme to `http://`, appends the Node-RED dashboard path `/ui/`, and persists the resulting URL locally. Use the connection header in the app to change the server later.

The manifest currently permits cleartext traffic because local Node-RED deployments commonly use HTTP. For production deployments, prefer HTTPS and consider restricting cleartext traffic to the required host.

## Project structure

```text
app/src/main/java/com/example/kmbe_bms/  Activity and WebView integration
app/src/main/res/                         Android resources and theme
gradle/libs.versions.toml                 Dependency and plugin versions
```

## Development notes

Developed by Filip Šulík. Contributions are NOT accepted.

- The app allows navigation only within the configured server URL.
- JavaScript and DOM storage are enabled for the Node-RED dashboard.
- The saved server URL is stored in app-private `SharedPreferences`; no credentials are stored by the app.
- Keep generated output, local SDK paths, and IDE state out of commits. The repository `.gitignore` covers these files.
