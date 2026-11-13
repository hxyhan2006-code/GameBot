# Universal Game Bot v0.1 (local proof of concept)

[![Build APK](https://github.com/hxyhan2006-code/GameBot/actions/workflows/build-apk.yml/badge.svg)](https://github.com/hxyhan2006-code/GameBot/actions/workflows/build-apk.yml)

**Quick start:** Actions tab > Build APK > Run workflow > download `app-debug-apk` artifact.

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

## Limitations
- Local color detector, not a trained AI model. This is the deterministic on-device part of the proposed Hybrid system. Online AI inference is deliberately **not implemented** in v0.1.
- Controls **only its own test game**, by checking the foreground package name. No third-party game automation is enabled.
- MediaProjection may be stopped by Android; grant permission again to restart. Android 14+ requires new consent for each projection session.
- Capture dimensions and gesture coordinate mapping can vary with orientation, display scaling, navigation bars, and HyperOS. Test in portrait mode with normal display scaling first.
- Accessibility and capture permission are sensitive. No screenshots are saved or uploaded.
- Background gameplay is not implemented. Android may pause games or block interaction when hidden.

## Next steps
Add a `GameProfile` abstraction, a foreground-only calibrated coordinate mapper, an on-device object detector (e.g., TFLite), optional cloud vision with explicit opt-in and screenshot redaction, and a debug overlay. Avoid automating online games against their terms of service.
