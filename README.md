# Universal Game Bot v0.4 (local proof of concept)

[![Build APK](https://github.com/hxyhan2006-code/GameBot/actions/workflows/build-apk.yml/badge.svg)](https://github.com/hxyhan2006-code/GameBot/actions/workflows/build-apk.yml)

**Quick start:** open the **Releases** page, download the newest `UniversalGameBot-buildN.apk` and install it.

An Android Kotlin project for a single Xiaomi 15T Pro or another Android 10+ phone. This is **not a compiled APK**. It demonstrates a complete local observe → decide → tap loop in a built-in green-target game. No API key, internet, root, or PC is needed to **run** the resulting APK; a build environment is needed to **compile** it.

## Build (Android Studio)
1. Install Android Studio on a computer, open this folder, and allow Gradle sync. Use JDK 17 and Android SDK 35.
2. Build > Build Bundle(s) / APK(s) > Build APK(s).
3. Transfer `app/build/outputs/apk/debug/app-debug.apk` to your phone and install it. Android may request permission to install from your file manager.
4. Open the app. Tap **Enable Accessibility**, find **Game Bot Touch Controller** and enable it. On HyperOS, you may need to allow restricted settings for the app first (only for this locally built APK that you trust).
5. Return to the app and tap **Start screen capture + bot**, grant full-screen capture. Keep the app in the foreground. The green dot should be tapped and move, increasing Score.
6. Tap **STOP BOT** or the Stop notification action to stop capture. Disable the Accessibility service after testing.

## Build APK without a PC (GitHub Actions)
1. Push this project to a GitHub repo (include the `.github` folder).
2. Open the **Actions** tab > **Build APK** > **Run workflow** (it also runs on every push to `main`).
3. When it turns green, open the run and download the **app-debug-apk** artifact (contains `app-debug.apk`).
4. Install on the phone, then follow steps 4-6 of the Android Studio section above.

## Install & update
- Every push to `main` (or Actions > Build APK > Run workflow) builds an APK and publishes it on the **Releases** page. No unzip needed.
- Updating: just install the newer APK over the old one. All builds use the same signing key (`keystore/debug.keystore`, **do not change or delete it**) and the build number (`versionCode`) goes up every run, so Android treats it as an update.
- First time only: if you installed an earlier APK (from the first test build), uninstall it once, because it was signed with a different key.
- The app has a TH/EN language button at the top right.

## Limitations
- Local color detector, not a trained AI model. This is the deterministic on-device part of the proposed Hybrid system. Online AI inference is deliberately **not implemented** in v0.1.
- Controls **only its own test game**, by checking the foreground package name. No third-party game automation is enabled.
- MediaProjection may be stopped by Android; grant permission again to restart. Android 14+ requires new consent for each projection session.
- Capture dimensions and gesture coordinate mapping can vary with orientation, display scaling, navigation bars, and HyperOS. Test in portrait mode with normal display scaling first.
- Accessibility and capture permission are sensitive. No screenshots are saved or uploaded.
- Background gameplay is not implemented. Android may pause games or block interaction when hidden.

## Next steps
Add a `GameProfile` abstraction, a foreground-only calibrated coordinate mapper, an on-device object detector (e.g., TFLite), optional cloud vision with explicit opt-in and screenshot redaction, and a debug overlay. Avoid automating online games against their terms of service.

## v0.3 debugging notes
- Root cause of "bot never taps" in v0.2: the Accessibility config lacked `canRetrieveWindowContent`, so `rootInActiveWindow` was always null and every frame was skipped by the package check. Fixed, and the safety check no longer depends on it (tap only while this app's game screen is in the foreground and only inside the arena rectangle).
- Built-in **Debug Monitor** panel shows capture state, frames, green pixels, target coordinates, gesture result, last error and skip reasons. Logcat tag: `GameBot`.
- **Test gesture** button taps the target via Accessibility without vision: if Score rises, Accessibility works; the remaining problem would be vision.

## v0.4 architecture
Pipeline: **Screen Capture → Vision Engine → Decision Engine (state machine) → Gesture executor**, all game-specific logic lives in a `GameProfile`.
- **Game profiles** (`GameProfile.kt`, `Profile*.kt`): Green Dot (color + tap, the original test game), Bullseye Match (template matching vs. decoys + tap), Swipe Ball (two color detectors + swipe). Pick one on the first screen.
- **Vision** (`Detector.kt`, `ColorDetector.kt`, `TemplateDetector.kt`): color blobs with bounding boxes, SAD template matching, confidence per object, `ModelDetector` interface reserved for TFLite. Boxes are drawn over the arena (toggle "Boxes").
- **Decision** (`DecisionEngine.kt`, `Strategy.kt`): SCANNING → DECIDING → ACTING → VERIFYING → COOLDOWN. Actions: Tap, Swipe, Wait, Stop. Every action is verified from a new frame; one action in flight; duplicate-tap suppression; auto-stop after repeated failures or 2000 actions.
- **Safety**: only the in-app test game is controlled (`ArenaGate` + `GestureExecutor`); big red **EMERGENCY STOP** button, volume-down key, and the notification Stop action.
- **Debug & performance**: FPS, confidence, state/decision, action history, CPU/RAM/battery, error log file (`files/errors.log`), "Copy report" button.
- **Hybrid AI**: `CloudVisionClient` interface only. There is no INTERNET permission in the manifest, so no screenshot can leave the phone.
- Unit tests (JVM, no device): `gradle testDebugUnitTest`.
