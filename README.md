# TIGER Files

A native Android file manager built with Kotlin and Jetpack Compose.

## App details

- Application ID: `com.mobin.tigerfiles`
- Version: **0.7.0**
- Minimum Android: **Android 10 (API 29)**
- Target SDK: 35
- Persian RTL interface with automatic system light/dark theme
- A redesigned dashboard with ambient gradients, translucent glass panels, storage analytics, category counts and floating navigation
- File access through Android Storage Access Framework; the user selects the folder the app can manage

## Features

- Home dashboard with storage analytics, file counts and category shortcuts
- Persian-first interface with automatic system light/dark theme
- Browse folders and files, with grid and list layouts
- Recursive search across the selected folder tree
- Filter images, videos, audio, documents, APKs, archives and folders
- Sort by name, size or modified date
- Multi-select files and folders
- Copy, cut, paste, rename and delete
- Share one or multiple files with other apps
- Favorites and recently opened files
- File details and hidden-file visibility setting
- Built-in editor for text and code files up to 1 MB
- Create ZIP archives from one or multiple selected files and folders
- Extract ZIP archives with path-traversal protection and extraction size limits
- Sort by file type and inspect the five largest files on the dashboard
- Persistent access to the selected folder
- Folder enumeration is performed on a background dispatcher to keep navigation responsive
- Create new folders and empty text/code files
- Select all visible items and navigate through clickable folder breadcrumbs
- Safer recursive folder copy with self/descendant-copy protection

## Build and signing

GitHub Actions builds the debug APK and publishes the latest successful build as the `latest` release. The workflow verifies the stable signing certificate and reads the version directly from Gradle configuration.
