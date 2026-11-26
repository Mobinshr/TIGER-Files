# TIGER Files

A native Android file manager built with Kotlin and Jetpack Compose.

## Current setup

- Application ID: `com.mobin.tigerfiles`
- Minimum Android version: **Android 10 (API 29)**
- Target SDK: 35
- UI: Persian, right-to-left, with system light/dark theme support
- File access: Android Storage Access Framework (user-selected folders)

## Build

The GitHub Actions workflow builds a debug APK and publishes the latest successful build as the `latest` release.

Before the first build, add the repository Actions secret:

- Name: `ANDROID_DEBUG_KEYSTORE_BASE64`
- Value: the same Base64 keystore used by the HTML Editor and Scanner projects

The workflow checks the signing certificate before producing the APK so future updates can be installed over the previous build.

## Current functionality

- Select a folder using Android's system folder picker
- Browse folders and files inside the selected location
- Search and filter the current folder
- Open files with compatible apps on the device
- View a basic internal-storage usage summary
