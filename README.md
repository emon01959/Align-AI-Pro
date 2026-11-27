# Align AI Pro

Premium Android visual-alignment assistant.

## Project identity
- Application ID: `com.emon.alignaipro`
- Version: 1.0.0 (versionCode 1)
- Minimum Android: API 26
- UI: Kotlin + Jetpack Compose, dark neon cyan/violet theme

## Current scope
- Dashboard UI and persistent Auto-BUILD master switch (OFF by default)
- Horizontal Position, Rotation, and Predictive Landing engine interfaces
- Local settings and event-history foundation
- Screen-capture permission flow foundation

Auto-BUILD is user-controlled and remains disabled by default. Enabling it does not bypass Android permission prompts or the safety/validation gate. The current starter does not yet implement live target recognition or automated touch/build actions.

## Updating without uninstall
Keep the same applicationId and sign every update with the same private signing key. Increase versionCode for every release. Do not commit signing keys or passwords; configure them as GitHub Actions secrets. Losing the original signing key prevents seamless update for APKs signed with it.

## Build
Open with Android Studio or run `./gradlew assembleDebug`. GitHub Actions builds a debug APK on pushes and a release APK when a version tag is pushed.
