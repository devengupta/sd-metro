# System Design Metro (Android)

A native Android app (Java, no libraries) that teaches system design as a metro map:
25 lines, 463 stops, and Ride / Pager / Teach on every stop. Everything runs on the phone.

## Get the APK (no Android Studio needed)
1. Push to the `main` branch. The "Build APK" run under the repository's **Actions** tab
   runs the unit tests and then builds the app.
2. When it finishes, download **sd-metro-debug-apk** from the run's Artifacts and install `app-debug.apk`.

The committed `debug.keystore` keeps the signature stable, so new builds install over old ones.

## Build it yourself
With JDK 17 or newer, the Android SDK (platform 34) and Gradle 8.7:

    gradle testDebugUnitTest assembleDebug

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Where things are
- `app/src/main/assets/roadmap.txt` is the map: lines, stops and the final goals.
- `app/src/main/assets/content.json` is the built-in lesson for every stop.
- `app/src/main/java/com/sdmetro/app/`
  - `Roadmap`, `Content`, `Progress`, `Arcade`, `Accounts`, `Crypto`, `Gen`, `Claude`: the logic.
    Plain Java, covered by the unit tests in `app/src/test`.
  - `MainActivity`, `Screens`, `StopScreen`: the screens and navigation.
  - `Ui`, `W`: colours, fonts and the custom-drawn pieces (rails, stations, pips, boxes).
  - `App`, `UiState`: the signed-in session and what is on screen.
- Fonts (Chakra Petch, Manrope, IBM Plex Mono) are bundled in `app/src/main/res/font/`.

## What is stored where
- Accounts (name, email, salted PBKDF2 password hash), progress and the optional Anthropic API key
  are kept in the app's private storage on the phone. There is no server.
- Ride, Pager and Teach use built-in content for every stop and work offline.
  An Anthropic API key under Profile is optional and adds freshly generated versions.
  The key belongs to one account and is sent only to api.anthropic.com.
  Model names are `MODEL_QUICK` and `MODEL_DEFAULT` in `Gen.java`.
- Version 1.x of this app was a web page inside the app. Accounts and progress from 1.x are
  not carried over to 2.0; create the account again.

## Versions
Android Gradle Plugin 8.5.2, Gradle 8.7, JDK 17, compileSdk/targetSdk 34, minSdk 26 (Android 8.0).
