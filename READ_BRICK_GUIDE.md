# Brick Breaker — read-only milestone

GameBot's second external test game uses the Apache-2.0 `fadden/android-breakout` source pinned in `fixturebrick/UPSTREAM.md`. GameBot reads only screen pixels. It never controls the paddle in this milestone; the game's own physics moves the ball. Shared code now covers sampled pixel observations, frame freshness and adjacent-frame motion, with fixed read-only package/session scope.

## Phone steps / ขั้นตอนบนมือถือ

1. Update GameBot over the existing app, and install **GameBot-Brick-Test.apk** separately. The existing 2048 Test APK does not need updating.
2. Open **Brick Test (GameBot)** once. On the menu, choose Easy/ง่าย and press New Game/เริ่มเกมใหม่. Drag left/right to move the paddle and check the game renders. Leave Never lose ball unchecked. Return to GameBot.
3. In Profiles, choose **Brick Breaker Test** under external test games. Enable Accessibility if disconnected. Press **เริ่มอ่านและเปิด Brick Test** and approve full-display capture.
4. The fixed exported game Activity opens directly after consent; its public entrypoint is navigation only, with no game-state bridge. Play by dragging the paddle for about 10–15 seconds, then return to GameBot. Leaving the fixed target stops capture.
5. Inspect the schematic, which is the last numeric observation (white ball, cyan paddle, gray detected bricks), not a saved screenshot or guaranteed current board. Press **คัดลอกรายงาน Brick** once and send the report. If the game is blank or the report has no arena/ball, report that outcome; do not treat compilation as proof of OpenGL capture on the Xiaomi.

## Interpretation

`frames` counts processed fresh source frames. `confirmedFrames` means both unique ball and paddle candidates were found, not independent ground truth or identical-board stabilization. `bricksDetected` is a visual estimate and can be incomplete when occluded. Coordinates are full-display screen pixels; velocity is an adjacent-source-frame estimate (right/down positive), reset on gaps, ambiguity, geometry changes and large jumps. `lastMotionEstimate` has its own source timestamp and is explicitly historical after stopping. No next-ball-position or paddle-control prediction is claimed yet.

## Boundaries

- Fixed target: `com.example.gamebotbricktest`; no caller-supplied package or arbitrary app support. Activity/service are unexported. Existing 2048 control gates and internal ArenaGate remain in place.
- Read-only reader and 2048 capture/bot sessions cannot start concurrently. Window-change events latch target loss; frame processing also checks package before reading and before publishing.
- Initial target/arena wait is at most 10 seconds, session at most two minutes; no fresh frames for three seconds, loss of Accessibility, or display geometry change stops it. Source clock rejects duplicate/stale/pre-entry frames. Last results are memory-only and clearly historical. Generation IDs discard late results/stops from prior workers.
- No screenshots, cloud calls, Internet permission, overlay permission, accessibility text extraction or internal game-state bridge. There is no gesture executor in the new read-only pipeline. The fixture has no bot hooks or extra game-state signals.
- Source palette/shape rules target only the supplied game. Unknown scenes, merged/occluded ball/paddle, text or multiple candidates remain uncertain. This is not universal OCR or a guarantee of detecting every brick.

## Verification

Synthetic raster tests cover the upstream arena/palette, 96 gradient bricks, ball/paddle geometry, ambiguity, occlusion, motion timing, target loss and stale worker publication. Robolectric checks safe insets and pinned controls at narrow width. Compilation, signatures and no-Internet checks are separate from the required physical OpenGL rendering/capture and manual-touch test. No emulator or Xiaomi run is claimed.

The upstream game and notices are kept in a separate fixture module. Long original GameState code is not GameBot's observation or decision logic. The local XML/menu and bounded renderer-pause changes are documented and marked in the modified files. No win-rate or control performance conclusion follows from this read-only stage.
