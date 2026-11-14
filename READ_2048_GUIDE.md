# 2048 test game — reader and explicit single step

GameBot reads the supplied **2048 Test (GameBot)** Android app. Han confirmed repeated manual swipes were read correctly and approved the next single-step milestone. **Start reading** stays read-only; **Move once and open game** authorizes at most one swipe in this fixed test package. The existing internal-game ArenaGate and executors are unchanged. User-selected bounded continuous runs of 10/30/100 moves are available from v0.8.4. Unlimited autoplay remains unimplemented.

## Owner phone steps / ขั้นตอนเทสต์มือถือ

1. Install both APKs from the same Release: update UniversalGameBot over the old app, and install `GameBot-2048-Test.apk` as a separate game. Open the test game once, dismiss its help if shown, then return to GameBot. No Google login is needed.
2. In GameBot's Profiles page choose **2048 Test** under **เกมทดสอบภายนอก**. Enable Accessibility if needed, then press **เริ่มอ่านและเปิดเกมทดสอบ**. Approve screen capture; if Android asks to share a single app, select **2048 Test (GameBot)**. The supplied fixture's package is `com.example.gamebot2048test`; other 2048 apps are intentionally unsupported.
3. Wait at least two seconds for a still board. Optionally swipe the game manually a few times and wait again. Return to GameBot using Recents (or tap the reader notification) to compare the last confirmed 4x4 grid. A dot means an empty cell. Returning stops capture; the result is labelled as an older confirmed reading with its age.
4. Compare all 16 cells, then use **คัดลอกรายงานรวม** and send the report. If any value differs, send a manually taken screenshot of the still game board as well. There are no automatic screenshot files or uploads in this prototype.
5. Check that leaving the target game stops reading. The notification also has a Stop action. Sessions stop after two minutes; no target foreground within ten seconds or no fresh frames also ends the session. If stopped before a board is confirmed, restart a session rather than treating an old grid as current.

## Reading and boundaries

- Reads screen pixels only. No preferences, game state, IPC, JavaScript bridge, or accessibility text is used to obtain tile values. Accessibility is only used to confirm the active package, including rechecking before publishing a result.
- Recognizes the pinned fixture's default palette and regular 4x4 geometry, through tile 2048. Unsupported higher tiles, unknown colors, partial grids, menus/overlays and transitions may return **uncertain**. This is not a universal OCR reader.
- A small off-grid fixture indicator changes at 5 Hz to provide fresh frames on still boards; it transmits no game state. Frame freshness uses `Image.timestamp` against the monotonic source clock, rejecting old/duplicate/pre-entry buffers.
- Requires three identical valid frames over at least 400 ms. Duplicate timestamps, a changed frame, a failed read or a long gap invalidate stabilization. Prior confirmed numeric values remain visible but are explicitly labelled as historical, never substituted as a fresh guess.
- Target package is hardcoded. Leaving it or losing foreground verification after entry permanently ends that session; it does not resume on another app. The capture service and Activity are not exported. Read-only sessions grant no gesture permission. A single-step session uses a separate consumable permission for one short swipe inside the visually detected board; no overlay permission is requested.
- Sampled images exist only in memory. Clipboard export is user initiated and contains numeric readings/status. Both APKs must have no Internet permission; CI checks this and their signing certificates.

## Test fixture provenance

`fixture2048/` vendors the native Android source from https://github.com/tpcstld/2048 under MIT. The pinned commit, original notices, attribution, and local changes are in `fixture2048/UPSTREAM.md`, `LICENSE.md`, and `UPSTREAM-README.md`. Play Games/cloud snapshots and SDK dependencies are removed, and the bundled external font is replaced with Android's system bold font. The fixture does not communicate game state to GameBot.

## Verification scope

JVM tests exercise palette/grid reading, numeral holes, board translation, unsupported cells, obscuration, stabilization, stale/duplicate frames, target matching and session deadlines. These synthetic raster tests do not establish accuracy on real Android screenshots. Android compilation and package checks are separate from the required Xiaomi test above. No emulator/physical device run is claimed without actual evidence.

## UI refresh (0.8.1)

The reader uses system bar/cutout insets, a square 4×4 grid and a persistent Start/Stop action. Instructions and frame counts are collapsed under expandable rows. Unknown cells use a dash; confirmed empty cells use a dot. Capture/session behavior is unchanged. Check the header, scrolling and buttons on the Xiaomi with the normal font size and a larger font size; local builds do not replace that phone check.

## Single-step phone test (0.8.2) / ทดสอบเดิน 1 ครั้ง

1. Update only GameBot; keep the installed 2048 Test game. After this update, turn Accessibility off and on once so window-occlusion metadata is available. Accessibility must be connected. Open the 2048 page and press **เดิน 1 ครั้งและเปิดเกม** (Move once and open game).
2. Approve **full-display** capture for this mode. It needs display coordinates for the swipe; Android 14+ uses a default-display capture request. The test game opens automatically. Do not swipe manually or open menus during the step.
3. Wait about 2–4 seconds. One local heuristic selects a supported legal direction from fresh stable screen pixels. The bot swipes once, waits for new stable frames, verifies the slide/merge plus exactly one random 2 or 4, then stops. It does not promise the best move or a win.
4. Return to GameBot. Check **ยืนยันผลแล้ว** and compare all 16 cells. Copy the report: `direction`, `requests`, `commandCompleted`, `step`, `before`, `expectedBeforeSpawn`, and `after` distinguish command completion from result verification. `requests=1` is a move request, not proof Android dispatched it.
5. Repeat by pressing the button again; each press starts a new capture permission/session. From v0.8.3, complete several steps and copy the combined report once after the last step; it includes up to 20 completed single-step sessions in chronological order, including failures/stops. Also try leaving the game immediately or cancelling capture: it must not continue/swipe again. Unknown frames, lost focus, changed geometry, stale frames, rejected/cancelled gestures, or timeout stop without retry.

The dedicated dispatcher rechecks exact foreground package, unlocked display, idle internal bot, session authorization, the latest stable board, original display dimensions, focused application-window metadata, absence of higher windows covering the board, board/window bounds and source-frame age on the Accessibility main thread immediately before dispatch. A one-use permit expires after 750 ms. Boards smaller than 384 physical pixels are rejected; the path spans 84% of a board dimension, exceeding the fixture's 250 px input threshold while keeping endpoints inside the board. The swipe lasts 180 ms; Android may cancel it, and an already-dispatched gesture cannot be recalled by the app. No additional gesture is sent on stop. Verification uses source frames strictly newer than the completion callback and new stabilization. A board above tile 2048 or a move merging beyond the supported palette is not attempted.

JVM tests cover moves/merges, random-spawn verification, single-use/stale/revoked permits, focus/geometry rejection, callback/source barriers, cancellation and deadlines. Robolectric checks the explicit button, narrow layout and large Thai text. These checks do not replace Xiaomi testing of actual gestures and capture coordinates.

## Combined report / รายงานหลายรอบ (0.8.3)

Install the GameBot update, perform three one-step sessions as above, then press **คัดลอกรายงานรวม** once. The report begins with `2048 RUN HISTORY` and includes `run #...` sections with each run's command/result evidence. `retained`, `total`, `dropped`, and `verifiedInRetained` distinguish retained runs from successful ones. The screen shows how many completed runs are retained. A running session or a read-only session is labelled separately and does not count as a completed single-step run.

History is numeric/status data in memory only, capped at 20 runs, and resets when Android kills the app process or the app is force-closed/restarted. Merely leaving the reader page to open the test game does not clear it. Earlier Build 28 runs cannot be recovered. No new capture/gesture permission is required by this reporting change; the existing capture consent for each step still applies.

## Bounded continuous run / เดินต่อเนื่อง (0.8.4)

1. Update GameBot over the old app. Open the 2048 page; scroll to **เดินต่อเนื่อง**, choose **10**, and press **เริ่มเดินตามจำนวนที่เลือก**.
2. Approve full-display capture once. The fixture opens. Do not swipe or reset the board manually while the run is active.
3. The bot verifies each move before another is allowed. Watch the game or the notification for verified progress. Stop using the notification action, or return to GameBot (leaving the fixture stops the run). No overlay permission is needed.
4. After completion, return and copy the combined report once. Expect `batch=COMPLETE limit=10 verified=10 requests=10` if all ten were confirmed; an earlier stop/error is reported with partial progress and a reason. Try 30/100 only after the first report is checked.

Each turn reuses single-step rules, callback/source-frame barriers, fresh board stabilization, exact-package/window/occlusion checks and a new short-lived one-use permit. The next turn requires a source frame newer than the previous verification and the same verified board/geometry. A changed/uncertain board between turns stops the batch; transient animation during verification waits only up to the existing bounded timeout. There are no retries, including after cancelled commands. The only accepted batch limits are 10/30/100. Deadlines are finite (10:70s, 30:190s, 100:600s), with the existing initial-target and fresh-frame checks; read-only/single-step sessions retain their original deadline. Unsupported tiles/moves or no legal supported direction stop rather than extending the run.

A batch is one history entry, with verified count, request count, terminal status and reason. Full per-turn evidence is included for the latest archived run and the current run; older batches are summarized to keep clipboard text bounded. History remains capped at 20 runs in process memory. This does not establish playing strength or a win rate. Test real capture, input and Stop behavior on the Xiaomi.
