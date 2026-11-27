# TIGER Files

A native Android file manager built with Kotlin and Jetpack Compose.

## App details

- Application ID: `com.mobin.tigerfiles`
- Version: **0.5.0**
- Minimum Android: **Android 10 (API 29)**
- Target SDK: 35
- Persian RTL interface with automatic system light/dark theme
- Glass-inspired translucent panels, a refined amber-and-navy palette, lighter surfaces and simplified navigation
- File access through Android Storage Access Framework; the user selects the folder the app can manage

## Features

- Home dashboard with storage overview and file-category shortcuts
- Browse folders and files, with grid and list layouts
- Recursive search across the selected folder tree
- Filter images, videos, audio, documents, APKs, archives and folders
- Sort by name, size or modified date
- Multi-select files and folders
- Copy, cut, paste, rename and delete
- Share one or multiple files with other apps
- Favorites and recently opened files
- File details and hidden-file visibility setting
- Persistent access to the selected folder
- Create new folders and empty text/code files
- Select all visible items and navigate through clickable folder breadcrumbs
- Safer recursive folder copy with self/descendant-copy protection

## Build and signing

GitHub Actions builds the debug APK and publishes the latest successful build as the `latest` release. The workflow verifies the stable signing certificate and reads the version directly from Gradle configuration.
