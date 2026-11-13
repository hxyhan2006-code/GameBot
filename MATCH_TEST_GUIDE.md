# Match diagnostics after the 13–14 November phone tests

This change is based on `claude/gamebot-v0.7-match`, not the training-only `main`.

## What changed

- `MatchConfig.kt` contains the experiment controls: `restrictShotY=true`, screen-space `shotMinY=1250`, `shotMaxY=1310`. The bot dribbles the ball into that band before aiming. Disable the restriction for a baseline run. These are **absolute screen pixels from the Xiaomi report**, not dp or arena-local coordinates; recalibrate on a different layout/device.
- `Game defense` gives the simulator's actual conceded goals, turnovers and time-weighted possession, for report comparison only. The bot never reads these values; its source guard checks that it cannot access `DebugMonitor`. Pauses and goal celebrations are excluded; loose-ball time is separate.
- Visual defense estimate: visual goals conceded, turnovers, and time-weighted team possession. Possession is home/(home+away); loose-ball and unknown times are reported separately. A turnover requires home and away ownership each confirmed for 200 ms, including at most 1.5 s of loose ball. Brief ownership flicker is ignored. Capture gaps over 500 ms and kickoff/full-time reset ownership history. This is an estimate from vision, not the world's possession state. It can still overcount (27 vs actual 14 turnovers in one simulation); use `Game defense` for exact simulator counts.
- Successful shoot commands count inside/outside attempts, with goals for each group. Cancelled commands count separately. Shot conversion is no longer described as command verification. Match ignores the 100-action stop and 95% pass criterion. Normal's current goal is <=3 conceded per completed match; hard has no win target.
- Pass lanes are checked from the **ball** to the teammate, with player+ball radii, configurable margin, and a predicted opponent position; the opposing keeper also blocks pass lanes. If the lane is blocked, the bot continues dribbling.
- Shot probes retain the first processed frame at or after +300/+600 ms relative to gesture dispatch, including actual frame time/sequence and ball/keeper/nearest defender positions. A frame over 150 ms late is explicitly unknown. Keeper/defender proximity is a contact **candidate**, not proof; overlapping/occluded evidence stays unknown.
- Sampled PNGs contain only the in-app arena. They remain on the phone in `Android/data/com.example.universalgamebot/files/match-probes/` (last 120 frames). Filenames encode dispatch timestamp, requested offset, actual offset, and frame number. Copy report lists the directory and newest filenames; it does not embed images. No upload or network permission is added. Sampling resolution matches Vision, not full screenshot resolution.
- Gesture callbacks return to the Vision thread. Stop/release is marked busy, closing executors reject new commands, and timed-out commands stop the engine rather than permitting overlapping gestures. Tap delay starts at 35 ms and can switch to 50/70 ms; 0 ms is removed. This removes the deterministic 0 ms trial, but does not establish that 0 ms caused the phone cancellations.
- Capture initializes its running state/counters before the first listener callback. Existing queued-frame recovery and the 4 s no-frame stop remain. Phone verification is required.

## Simple phone test (เจ้าของทำเอง)

1. Install the new APK over the current app (same signing key). Enable Accessibility and allow screen capture when requested.
2. Select **Match / Normal**. Start the bot and let the match finish. Repeat 3 times; do not stop at 100 actions.
3. After **each** match, use **Copy report** and save/send that report before starting another round.
4. Check `vision score` against the displayed score; record whether conceded goals are <=3. Do not infer success from a single match.
5. Check `Game defense` (actual) and visual `defense` (estimate): turnovers, possession%, home/away/loose/unknown time. Check `shot band`: outside should be 0 with this experiment enabled.
6. Check `shot probes`: both +300/+600 samples and their actual offsets. Save the report even if the reason is UNKNOWN. PNGs can be copied through USB if Android permits browsing the app's external files directory.
7. Check `Gest. mix`: there should be no 0 ms trial. Compare stick cancellations and kicks cancelled with prior reports. Start/stop 3 times to check the first frame arrives; keep any no-frame error report.
8. Optionally run 1–2 Hard matches for diagnostics only. There is no Hard win target.

The new diagnostics and restricted shooting experiment do not themselves prove that Normal will concede <=3; that remains a phone-test goal. The defensive pressing policy has not been redesigned without evidence.

## Validation performed locally

- Android SDK 35, JDK 17, Gradle 8.10.2: full JVM suite **102 passed**, followed by affected control/match checks **31 passed**, and the final game-defense/world/match suite **30 passed** after adding actual game metrics. No failing tests were disabled.
- `assembleDebug` passed; APK v0.7.2-match-diagnostics, version code 25, verified against the repository's original signing certificate.
- Restricted-band integration match completed with inside shots=1, outside=0, both probe offsets present. This sample did not establish improved shooting or Normal defense.
- No physical Android device was available. Accessibility dispatch, PNG storage on Android 16, startup capture, and match performance still require the phone steps above.
- Local build logs and XML results are retained under `build/verification/` (ignored build artifacts). This is a local test APK, not a published GitHub Release.
