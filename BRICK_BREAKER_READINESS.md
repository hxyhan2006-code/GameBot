# Second-game source check — Brick Breaker

Status: source candidate inspected, not implemented or tested on the phone. No new package, capture target or gesture capability is enabled by this document.

Candidate: [fadden/android-breakout](https://github.com/fadden/android-breakout), commit `429506bcf956b048c0b369e61d4888d273cef321` (GitHub API checked 2026-11-14). The repository's COPYING is Apache License 2.0; its manifest retains Google Inc. copyright/license notices. Preserve upstream notices and attribution if vendored.

Read-only findings:

- Existing manifest package is `com.faddensoft.breakout`; it requires OpenGL ES 2.0 and declares no permissions, including no Internet permission.
- It targets Android 16 **SDK level**, meaning Jelly Bean / API 16, not Android OS version 16. Current minSdk is 8. This old APK cannot be treated as install-ready on the user's Android 16 device.
- It is an Eclipse/Ant-era project and explicitly excludes small/normal screens in its manifest, with a comment that it was intended for tablets. A modern build and phone-layout adaptation need verification before selecting it as the fixture.
- Source separates GameState, Ball, GameSurfaceView and GameSurfaceRenderer. Rendering/capture compatibility has not yet been established; manifest inspection is not a vision test.

The next game milestone is a separate, clearly identified test APK and **read-only** ball/paddle/brick observations. Capture must be limited to that fixed package, stop on focus loss and never read game internals as observations. The current 2048 executor and ArenaGate must not be broadened to drive an arbitrary Breakout app. A live prototype, phone capture accuracy, tracking/prediction and any later control are separate checkpoints.

Current handoff: the 2048 victory display/report is being closed out, and the owner should run three fresh 2048 games for consistency. This source check prepares the second-game choice without claiming an external Brick Breaker reader already exists.
