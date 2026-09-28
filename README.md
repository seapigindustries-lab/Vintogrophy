# Vintogrophy

**A vintage-toned photo editing and filtered camera app for Android.**

Vintogrophy turns your phone into a film-inspired camera: live preview, one-tap color filters (Mono, Sepia, Vintage, Cool, Warm), and instant saves — all in a native Kotlin + Jetpack Compose UI.

## Features

- 📷 CameraX live camera preview (back camera)
- 🎞 One-tap photo filters: Original, Mono, Sepia, Vintage, Cool, Warm
- 🖼 Captures are filtered and saved as JPEG to the app's external files directory (`Android/data/com.vintogrophy.app/files/Vintogrophy/`)
- 🎨 Material 3 theming with dark/light support

## Tech Stack

| Layer | Choice |
|---|---|
| Language | Kotlin 2.0 |
| UI | Jetpack Compose + Material 3 |
| Camera | CameraX 1.4 |
| Build | Gradle 8.9 / AGP 8.7 |
| Min SDK | Android 8.0 (API 26) |

## Getting Started

### Prerequisites

- Android Studio (Koala or newer) with Android SDK 35
- JDK 17+ (Android Studio's bundled JBR works)

### Build & Run

```bash
./gradlew assembleDebug        # build APK
./gradlew installDebug         # install on a connected device/emulator
```

Or open the project in Android Studio and press **Run**.

The debug APK lands at `app/build/outputs/apk/debug/app-debug.apk`.

## Project Structure

```
app/src/main/java/com/vintogrophy/app/
├── MainActivity.kt        # Entry point, Compose host
├── camera/CameraScreen.kt # CameraX preview, capture, permissions
├── filter/PhotoFilter.kt  # ColorMatrix filter definitions
└── ui/theme/              # Material 3 theme (vintage palette)
```

## License

TBD
