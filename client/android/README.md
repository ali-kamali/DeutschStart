# DeutschStart Android Client

This is the Android client for DeutschStart, built with Kotlin, Jetpack Compose, and Hilt.

## Setup

1. **Open in Android Studio**: Open the `client/android` folder in Android Studio (Iguana or later recommended).
2. **Sync Gradle**: The project should automatically sync dependencies.
3. **Run**: Select the `app` configuration and run on an emulator or device.

## Architecture

- **MVVM**: Model-View-ViewModel pattern.
- **Hilt**: Dependency Injection.
- **Room**: Local database with SQLCipher encryption.
- **Compose**: UI toolkit.

## Database contents

The database is encrypted. The key is currently hardcoded in `DatabaseModule.kt` for development.
