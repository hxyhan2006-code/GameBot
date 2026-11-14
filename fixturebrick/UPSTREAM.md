# Upstream

Source: https://github.com/fadden/android-breakout
Commit: 429506bcf956b048c0b369e61d4888d273cef321
License: Apache-2.0, original COPYING and per-file copyright retained; COPYING also packaged as an APK asset.

Local adaptation: Gradle/SDK35 build, minSdk29, fixed test-only applicationId com.example.gamebotbricktest, portrait phone launcher into the upstream menu (new scrollable phone layout, original New/Resume handlers and preferences; owner selects Easy for the reader test; never-lose remains unchecked by default), modern no-action-bar theme, no tablet-only manifest exclusion, explicit exported flags, no permissions. Renderer-pause wait is bounded to2000ms to avoid indefinite UI blocking if the GL thread cannot acknowledge pause. XML trailing whitespace normalized; Thai menu labels/instruction added. Rules, physics, input, colors and game state remain upstream. No state bridge, screenshot files or networking is added. GameBot reads pixels in a separate app.

OpenGL capture, physical input and phone layout remain to be validated on Xiaomi Android16. Compilation is not proof of rendered behavior.

The long upstream GameState.java (more than 600 lines) is intentionally kept as vendored game code, outside GameBot observation/decision code; no structural rewrite or physics change is claimed. Modified source/XML files carry local-change notices.

GameActivity is explicitly exported solely as the fixed UI entrypoint opened by GameBot after consent, avoiding a menu transition during capture. It accepts no state/configuration bridge or bot input. GameBot reuses this activity with CLEAR_TOP/SINGLE_TOP. The launcher remains the phone menu for manual New/Resume/configuration.

Cold direct GameActivity entry uses upstream static defaults (easy, sounds off, never-lose off) if the fixture process was killed; it does not restore menu preferences itself. The read-only protocol selects Easy explicitly and does not claim persistence of other settings through that entrypoint.
