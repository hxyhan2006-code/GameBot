# Second-game source check — Brick Breaker

Status: v0.10.0 implements the separate test fixture and a fixed-package read-only reader. It is not yet tested on the phone. No gesture capability is granted to Brick Breaker. See READ_BRICK_GUIDE.md.

Candidate: [fadden/android-breakout](https://github.com/fadden/android-breakout), commit `429506bcf956b048c0b369e61d4888d273cef321` (GitHub API checked 2026-11-14). The repository's COPYING is Apache License 2.0; its manifest retains Google Inc. copyright/license notices. Preserve upstream notices and attribution if vendored.

Read-only findings:

- Existing manifest package is `com.faddensoft.breakout`; it requires OpenGL ES 2.0 and declares no permissions, including no Internet permission.
- It targets Android 16 **SDK level**, meaning Jelly Bean / API 16, not Android OS version 16. Current minSdk is 8. This old APK cannot be treated as install-ready on the user's Android 16 device.
- It is an Eclipse/Ant-era project and explicitly excludes small/normal screens in its manifest, with a comment that it was intended for tablets. A modern build and phone-layout adaptation need verification before selecting it as the fixture.
- Source separates GameState, Ball, GameSurfaceView and GameSurfaceRenderer. Rendering/capture compatibility has not yet been established; manifest inspection is not a vision test.

The next game milestone is a separate, clearly identified test APK and **read-only** ball/paddle/brick observations. Capture must be limited to that fixed package, stop on focus loss and never read game internals as observations. The current 2048 executor and ArenaGate must not be broadened to drive an arbitrary Breakout app. A live prototype, phone capture accuracy, tracking/prediction and any later control are separate checkpoints.

Current handoff: the owner supplied two played victories in Build32 plus the earlier played victory in Build31 (three observed played wins total, not three fresh games proved by the Build32 report alone). A zero-move old win screen and a pre-start scope stop were excluded. This is sufficient for the next read-only-game milestone, not a measured universal win rate. The second-game source is now vendored and compiled; real-device rendering/capture remains pending.
