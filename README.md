# File Manager

A native Android file manager built with Kotlin and Jetpack Compose.

## App details

- Application name: **File Manager**
- Android application ID: `com.mobin.tigerfiles` (kept stable so existing installations can receive updates)
- Kotlin namespace: `com.mobin.filemanager`
- Version: **0.9.0**
- Minimum Android: **Android 10 (API 29)**
- Target SDK: 35
- Persian RTL interface with automatic system light/dark theme
- Native Compose interface with a practical home dashboard and configurable settings

## Features

- Browse folders and files with grid and list layouts
- Home dashboard with selected-folder details, file/folder counts, storage usage and quick category access
- Configure which dashboard sections are visible
- Choose default grid/list mode and default sorting
- Recursive search across the selected folder tree (up to 15,000 entries and depth 32; a notice is shown if the safety limit is reached)
- Filter images, videos, audio, documents, APKs, archives and folders
- Sort by name, size, modified date or file type
- Multi-select files and folders
- Copy, cut, paste, rename and delete, with configurable delete confirmation
- Share one or multiple files with other apps
- Favorites and recently opened files, with optional recent-history recording
- File details and hidden-file visibility setting
- Built-in editor for text and code files up to 1 MB
- Create ZIP archives from one or multiple selected files and folders
- Extract ZIP archives with path-traversal protection and extraction size limits
- Persistent access to the selected folder
- In-app update checks against the official GitHub release
- APK download with live progress and SHA-256 integrity verification
- Installation through Android's system package installer; user confirmation is always required
- Folder enumeration, filtering and sorting are performed off the UI thread; cached metadata avoids repeated storage-provider queries
- Create new folders and empty text/code files
- Select all visible items and navigate through clickable folder breadcrumbs
- Safer recursive folder copy with self/descendant-copy protection

## Build and signing

GitHub Actions builds the optimized signed release APK and publishes it as the `latest` release. The workflow verifies the stable signing certificate and reads the version directly from Gradle configuration.

## Memory and scan limits

- The recursive global index is capped at 15,000 entries and depth 32; browsing directly into folders remains available beyond the index limit.
- The metadata cache is bounded to 20,000 entries and can be repopulated from Storage Access Framework metadata when needed.
- ZIP creation and extraction are streamed and protected by entry-count, depth and size limits.
