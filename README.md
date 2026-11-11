# System Design Metro (Android)

Native Android shell (Java, no libraries) around the bundled app UI in `app/src/main/assets/`.

## Get the APK (no Android Studio needed)
1. Create a new GitHub repository and upload everything in this folder to the `main` branch
   (include the hidden `.github` folder).
2. Open the repository's **Actions** tab. The "Build APK" run starts on every push to `main`.
3. When it finishes, download **sd-metro-debug-apk** from the run's Artifacts and install `app-debug.apk`.

The committed `debug.keystore` keeps the signature stable, so new builds install over old ones.

## What is stored where
- Accounts (name, email, salted PBKDF2 password hash), progress and the optional Anthropic API key
  are kept in the app's local storage on the phone. There is no server.
- Ride, Pager and Teach call the Anthropic API with the key entered under Profile.
  Model names are in `MODELS` near the top of the script in `assets/index.html`.

## Versions
Android Gradle Plugin 8.5.2, Gradle 8.7, JDK 17, compileSdk/targetSdk 34, minSdk 26 (Android 8.0).
