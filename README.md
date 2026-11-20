# Lumore AI

Lumore AI is a Kotlin + Jetpack Compose Android app featuring an AI Chatbot and AI Image Studio powered by **Firebase AI Logic** (`gemini-3.8-flash`, `gemini-3.5-flash-lite`, and `gemini-nano-banana-2.1`), **Cloud Firestore**, **Firebase Auth (Google Sign-In)**, and **Firebase App Check**.

## Requirements

- **Android Studio** (Ladybug or newer) or **JDK 21**
- **Android SDK** (`compileSdk 36`, `minSdk 24`, `targetSdk 36`)
- **Gradle Wrapper** (included via `./gradlew`)

## Building from GitHub

1. Clone the repository:
   ```bash
   git clone <your-repo-url>
   cd <repo-folder>
   ```

2. Build the debug APK (the root Gradle script automatically restores `debug.keystore` from `debug.keystore.base64` on first sync/build):
   ```bash
   ./gradlew :app:assembleDebug
   ```
   Output APK: `app/build/outputs/apk/debug/app-debug.apk`

3. Run local unit & Robolectric tests:
   ```bash
   ./gradlew :app:testDebugUnitTest
   ```

4. Or open the folder directly in **Android Studio** and click **Run**.

## GitHub Actions CI

A workflow is included at `.github/workflows/android.yml` to run unit tests and build/upload the debug APK automatically on pushes and pull requests to `main` / `master`.

## Firebase & Google Sign-In Notes

- The repository includes the matching `debug.keystore.base64` whose SHA-1 fingerprint is registered in `app/google-services.json`, so debug builds work with Google Sign-In out of the box.
- If you fork this project and link your own Firebase project or signing keystore, replace `app/google-services.json`, update `app/src/main/res/values/firebase_applet_config.xml`, and register your keystore's SHA-1/SHA-256 fingerprints in the Firebase Console.
- Debug builds use `DebugAppCheckProviderFactory`; for production Play Store releases, configure `release` signing environment variables (`KEYSTORE_PATH`, `STORE_PASSWORD`, `KEY_PASSWORD`) and your App Check reCAPTCHA Enterprise configuration.
