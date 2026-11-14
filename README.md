# Universal Game Bot v0.7.1c (local proof of concept)

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

## v0.4.1 verification & stats
- Verification now watches every new frame for up to `verifyTimeoutMs` after a gesture (handles animation delay) and classifies failures: MISSED_TARGET, TIMEOUT, STALE_FRAME, DETECTION_ERROR (plus GESTURE_FAILED / REJECTED).
- Root cause of the 8-19% false failures in v0.4: a new target often spawned within the old target's tolerance radius. Fixed by a small `samePlacePx` and by spawning new targets away from the old position.
- Frame drops are split into intentional throttling vs. superseded frames (backpressure); average FPS, per-profile success rate and latency are kept after Stop (Debug panel, "Copy report", `sessions.log`).
- "Stop at 100" chip stops the bot after exactly 100 actions to check the goal (>= 95% verified per profile).
- `SimulationTest` replays all three games with animation delay, jitter and flicker.

## v0.4.2
- Swipe Ball: new ball is now placed away from where the previous ball *started* (was compared with the dragged end position), removing ~9% false MISSED_TARGET (ground truth showed score +100, misses 0).
- A normal Stop no longer logs "MediaProjection stopped by system" as an error.

## v0.5: football training with a virtual joystick (continuous control)
- New profile **Football Training**: 2D pitch (player, ball, goal, moving keeper) controlled by a virtual joystick + SHOOT/PASS buttons. Only the in-app simulator is controlled.
- **Continuous control**: the joystick finger is one long stroke that is continued across gestures (`StrokeDescription.continueStroke`), so the direction changes without lifting the finger. SHOOT/PASS are tapped inside the same gesture while the stick is held (multi-touch).
- **Vision tracking**: 7 color detectors (player, ball, keeper, goal, joystick, shoot, pass); velocities are estimated from consecutive frames and used to predict the keeper.
- **Decision**: `FootballBrain` chases the ball, dribbles toward the corner farthest from the predicted keeper, then shoots while the stick is held. `RealtimeEngine` judges every shot from the screen (not from the game score), releases the stick when frames get stale and on emergency stop.
- Auto-stop chip: Tap/Swipe profiles stop at 100 actions, football stops at 10 goals in a row (or 100 shots).
- **Test button** in the football profile replays: stick right -> up without lifting -> shoot with stick -> pass with stick -> release, and the report shows what the game actually received (`pad touches:`).
- Unit tests simulate the whole loop on the JVM (world -> pixels -> vision -> brain -> engine -> simulated Accessibility with latency).

## v0.5.1
- Report from Build 13: the system cancelled 20% of continuous gestures (about 4 of 5 gestures that include the SHOOT press), which lifted the joystick finger. Root cause unknown; v0.5.1 adds a self-tuning tap delay (0/35/70 ms), a short back-off after a cancel, and a `Gest. mix` line in the report that breaks cancellations down by gesture kind.
- Bot fixes found by simulating cancellations: never shoot in a command that has just put the finger down (the stick is still centered, so the game kicks in the old facing direction); a cancelled gesture no longer resets the goal streak; goals seen by vision now use the same rule as the game; keeper speed is learned (median) instead of regressed across bounces.
- Less hesitation: shorter aim/follow-through waits and an immediate retry after a cancelled shot. Field redraw capped at ~60 fps (CPU was 78% of a core). Per-shot log (aim, keeper position, prediction, result) in the report. Capture shutdown race fixed.

## v0.5.2
- New button on the profile screen: **Turn off Accessibility**. Banking/finance apps (e.g. Dime) refuse to run while a sideloaded app's accessibility service is enabled. The button stops the bot and disables the service; re-enable it in Settings before the next test.

## v0.5.3
- The Accessibility controls moved into a status bar at the top of the profile screen: green = on, red = off. Tapping it expands **Turn on (open Settings)** and **Turn off**. Android does not let an app enable its own accessibility service, so "Turn on" only opens the Settings page.

## v0.6.0 (UI redesign)
- Home screen rebuilt: bot status pill at the top (tap = Accessibility bottom sheet), bottom navigation (Profiles / Access / Report / Settings), profile cards with the latest saved result.
- Accessibility controls moved to a bottom sheet (status, turn off, open Settings, steps, banking-app warning).
- Game screen rebuilt: bigger arena, one main Start button; while the bot runs it becomes a large Emergency stop with FPS / gesture / verified numbers. Test gesture, Boxes, Debug and Copy report are an icon row (hidden while running).
- Language, auto-stop at 100 actions and reset stats moved to the Settings tab.
- No change to the bot, vision, decision or safety code. The arena size is now fixed across states.

## v0.7.0 (Football Match 3v3, play it yourself)
- New profile **Football Match 3v3**: two teams (2 field players + 1 keeper each), two goals, a 2 minute match against an AI opponent. Controls: joystick plus shoot, pass (snaps to a teammate within 25 degrees) and tackle buttons. The player you control switches automatically and is marked with a white dot.
- Opponent level (easy / normal / hard) is chosen in Settings. Your wins / draws / losses per level show on the profile card.
- No bot in this version. The color detectors, the shared object labels (`MatchLabels`) and the JVM tests that check players never overlap and are detected correctly are already in place for v0.7.1.

## v0.7.1 (the bot plays the match)
- **Start bot** now works in Football Match 3v3. The bot reads the screen image only (no game internals): it finds the ball, both teams, the goals and the white dot that marks the player it controls, then chases the ball, dribbles toward the far corner of the goal, passes when pressed or to advance the ball, and shoots while holding the joystick toward the aim point. It stops by itself when it sees the full-time bar. A new match starts when the bot starts.
- The defence is minimal: it only moves toward the opponent who has the ball. Tackling comes in v0.7.2.
- Play it yourself is still available (icon row). Bot matches are counted separately from yours; the profile card shows the bot win rate at the current level and marks PASSED at 60% or more over 30 matches (easy level).
- Debug panel and report: a `Strategy` line with decision counts, shot and pass outcomes, and the score the bot counted from the image next to the real game score.

## v0.7.1b (honest status when no screen image arrives)
- If no screen frame arrives within 4 seconds of starting capture, the bot now stops by itself and records diagnostics (callbacks, null images, reader size, display state) in the debug report. The top pill shows "Waiting for screen image" while it waits and "Screen capture failed" afterwards, instead of "Bot running".

## v0.7.1c (screen capture start-up race fix, to be confirmed on the device)
- The image listener is now registered before the virtual display is created, so early frames can no longer fill the 3-slot image queue unnoticed. If no frame callback has arrived after 1.5 s the service tries to drain a queued image once and logs whether it found one (`recovered: ...` or `no queued image to drain ...`). After 4 s without any frame it still stops with diagnostics.

## v0.7.2 match diagnostics (phone validation pending)

- Adds actual simulator conceded goals, turnovers and time-weighted possession to debug/session reports, alongside the bot's visual estimates. Actual metrics are report-only; the bot continues to read only Vision observations.
- Retains +300/+600 ms detected-frame evidence and local sampled arena PNGs after successful shoot gestures, including actual sample times. Contact labels are candidates; ambiguous cases remain unknown.
- Configurable experiment in `MatchConfig.kt`: shoot only at absolute screen Y 1250–1310; dribble there first. Reports shots/goals inside and outside the band. These coordinates require calibration on other devices/layouts.
- Pass lanes use the ball origin, collision margins and predicted defenders. Match reports shot conversion separately from command failures and ignores the 100-action/95% criterion.
- Serializes gesture results on the engine thread, guards stop/release, stops on unknown gesture timeout, and uses nonzero tap delays (35/50/70 ms). Capture initialization no longer resets counters after early callbacks.
- Mobile test steps and metric definitions: [MATCH_TEST_GUIDE.md](MATCH_TEST_GUIDE.md). Normal's target remains <=3 conceded per match; no Hard win target is claimed.

## v0.8.0 — external 2048 reader prototype

- A separate offline MIT-licensed 2048 test APK is built from pinned tpcstld source in `fixture2048/`, with Google Play Games/cloud integration removed and original notices retained.
- GameBot reads its 16-cell board from pixels and displays stabilized values for manual comparison. This milestone never sends gestures to external apps. Internal ArenaGate and all existing game controls remain unchanged.
- Recognition is restricted to the test fixture/default palette through tile 2048; uncertain images remain uncertain. No cloud calls or automatic screenshot files.
- Install both APKs and follow [READ_2048_GUIDE.md](READ_2048_GUIDE.md). Phone validation is required before enabling any external swiping.

## v0.8.1 — 2048 reader UI

- Reader header respects status/navigation bars and cutouts. A square 4×4 grid distinguishes unknown cells from confirmed empty cells.
- External test game has its own section and matching card style; instructions and frame counts are expandable.
- Persistent Start/Stop and copy actions; no changes to capture boundaries, vision or gesture permissions. Still read-only.
- Phone check: open both pages, try a larger system font, read a board again, and compare all 16 cells. See [READ_2048_GUIDE.md](READ_2048_GUIDE.md).

## v0.8.2 — one requested 2048 step

- Adds **Move once and open game** alongside the existing read-only action, after Han confirmed manual-swipe readings on the phone.
- Selects a supported legal move locally, dispatches at most one swipe, checks fresh stable pixels against merge rules plus exactly one spawned 2/4, and stops without retry.
- Separate fixed-package, board-bounded, expiring one-use gesture permission; internal ArenaGate unchanged. No cloud, Internet permission, screenshots, or continuous autoplay.
- Follow the single-step section in [READ_2048_GUIDE.md](READ_2048_GUIDE.md); physical Xiaomi validation is pending for this new capability.

## v0.8.3 — combined 2048 test reports

- Keeps the latest 20 completed single-step runs in process memory, including failed/stopped runs, with ordered run numbers and an explicit dropped-run count.
- Copy the combined report once after several tests. Starting a reader/step session preserves prior history; repeated shutdown callbacks do not duplicate runs. No changes to moves, gestures or their scope.
- History begins in this version and resets when the app process exits; no screenshots or report files are saved.

## v0.8.4 — bounded continuous 2048 runs

- Choose 10, 30 or 100 moves and start once. Every move still requires fresh stable pixels, a one-use permit, a completed gesture and a verified legal slide plus one spawn before the next move.
- Stops on count, leaving the fixture, uncertain/changed between-move board, failed verification, unsupported/no move, or bounded deadline (at most 10 minutes). Read-only and single-step actions remain.
- Notification shows verified progress and Stop. Reports include a batch summary and every turn of the latest run; older batches retain summaries. No cloud or internal game-state reads.
- Phone test: start with 10 moves and copy the combined report after completion. Physical batch validation is pending.

## v0.9.0 — local planning and play to end

- Bounded expectimax enumerates future 2/4 spawns and searches several player turns; the old chooser remains for the paired benchmark. No cloud or API charges.
- Adds **Play to end** capped at 3,000 moves or 30 minutes, retaining all foreground/window/permit checks and per-move verification. Stops on screen win/game-over evidence, observed terminal board, failure, user stop, count or time.
- Shows highest confirmed tile, verified merge points this run, elapsed time and reason. A terminal label does not fabricate a verified final board or action. Reports export aggregate statistics for the entire run and detailed evidence for the latest 20 turns, with omitted counts.
- See [BENCHMARK_2048.md](BENCHMARK_2048.md): fixed paired simulation seeds reached 2048 in 0/8 old versus 8/8 new. This is not a measured Xiaomi win rate.
