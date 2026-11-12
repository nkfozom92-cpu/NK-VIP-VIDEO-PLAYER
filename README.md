# NK VIP VIDEO PLAYER

A starter Android video player app using AndroidX Media3 ExoPlayer.

## Build APK with GitHub Actions (recommended from Android/Termux)

1. Create a GitHub repository named `NK-VIP-VIDEO-PLAYER`.
2. Upload all files and folders from this project to the repository root.
3. Open GitHub -> Actions -> `Build NK VIP Video Player APK`.
4. Press `Run workflow` (or push to `main`).
5. Open the completed workflow run -> Artifacts -> download `NK-VIP-VIDEO-PLAYER-debug`.
6. Extract the ZIP and install `app-debug.apk` on your Android phone.

## Local build

Requires JDK 17, Android SDK (platform 35), and Gradle 8.9+.
Run: `gradle assembleDebug`

## Included now

- Dark player screen
- Select a local video through Android's file picker
- Playback controls supplied by Media3
- Pause on app background and release player on exit

This is an original starter app, not a modified PLAYit app. It does not unlock or bypass PLAYit VIP.
