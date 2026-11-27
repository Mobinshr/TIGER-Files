# TIGER Files

A native Android file manager built with Kotlin and Jetpack Compose.

## App details

- Application ID: `com.mobin.tigerfiles`
- Version: **0.7.5**
- Minimum Android: **Android 10 (API 29)**
- Target SDK: 35
- Persian RTL interface with automatic system light/dark theme
- A redesigned dashboard with ambient gradients, translucent glass panels, storage analytics, category counts and floating navigation
- File access through Android Storage Access Framework; the user selects the folder the app can manage

## Features

- Home dashboard with internal shared-storage analytics, file counts and category shortcuts; the storage card clearly notes that removable SD storage is not measured separately
- Persian-first interface with automatic system light/dark theme
- Optimized release APK with R8 code shrinking and Android resource shrinking
- Developer-only Compose preview/tooling libraries excluded from the installed app
- Browse folders and files, with grid and list layouts
- Recursive search across the selected folder tree (up to 15,000 entries and depth 32; the app shows a notice if the safety limit is reached)
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
- Folder enumeration, filtering and sorting are performed off the UI thread; cached metadata avoids repeated storage-provider queries
- Create new folders and empty text/code files
- Select all visible items and navigate through clickable folder breadcrumbs
- Safer recursive folder copy with self/descendant-copy protection

## Build and signing

GitHub Actions builds the optimized signed release APK and publishes it as the `latest` release. The workflow verifies the stable signing certificate and reads the version directly from Gradle configuration.


## Memory and scan limits (0.7.5)

- A new root scan drops the previous result list and metadata cache before scanning, preventing stale cross-folder search results and reducing overlapping memory use.
- The recursive global index is capped at 15,000 entries (depth 32); browsing directly into folders remains available beyond the index limit.
- The metadata cache is bounded to 20,000 entries and can be repopulated from Storage Access Framework metadata when needed.
