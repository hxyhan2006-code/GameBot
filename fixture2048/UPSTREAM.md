# 2048 offline test fixture

Source: https://github.com/tpcstld/2048
Commit: 2863bbe3c96f9fc34f4bf3258b983e53185b86af
License: MIT; see LICENSE.md (retained copyright notice).

Changes: separate test application ID; remove Play Games/cloud snapshot integration and SDKs; use Android's system bold typeface instead of redistributing ClearSans; Android build settings and app label. Add a 5 Hz redraw heartbeat with a tiny off-grid indicator for capture of still screens. Game rules and swipe handling remain upstream. No network permission or bot communication endpoint. GameBot must read pixels, never game state. The unused upstream instant/installed modules, cloud snapshot sources, font, and build artifacts are not included.
