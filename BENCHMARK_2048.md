# 2048 planner comparison — v0.9.0

Measured in this workspace on 2026-11-14 using `Board2048PlannerTest.pairedSeedBenchmark` (JVM, not Android input or the Xiaomi). No claim of a universal win rate.

Both strategies start with two random tiles, use the same rules and seeds 1101–1108, uniform empty-cell spawning with 90% 2 / 10% 4, and stop at 2048, no legal supported move, or 2,500 moves. Separate RNG instances receive identical seeds; different decisions naturally lead to different boards. The old strategy is the unchanged `Board2048Moves.choose`; the new strategy enumerates future spawns with expectimax and row/column evaluation. Optimized bitboard transitions are checked against the independent array rules on 1,000 random boards.

For reproducibility, quality comparison uses the production 24,000-expanded-node budget and disables only the wall-clock limit. Production additionally checks a soft 60 ms search budget every 128 expanded nodes, and retains the last fully completed search iteration. Search goes up to four player moves if budgets permit; depth is adaptive, not guaranteed. Actual device speed can reduce depth. Table/template warmup occurs on the capture worker before frame processing; all action freshness gates remain.

| Seed | Old max | New max | Old moves | New moves | Old merge score | New merge score |
|---|---:|---:|---:|---:|---:|---:|
|1101|512|2048|511|1028|6936|21012|
|1102|512|2048|666|1269|9776|25252|
|1103|256|2048|350|1054|4396|21964|
|1104|256|2048|310|1027|3752|21196|
|1105|256|2048|378|961|4576|20400|
|1106|1024|2048|862|1038|14604|21256|
|1107|512|2048|597|999|8704|20892|
|1108|256|2048|297|987|3452|20396|

First measured run: old/new mean maximum tile 448 / 2048; reached 512 in 4/8 / 8/8, 1024 in 1/8 / 8/8, and 2048 in 0/8 / 8/8. Mean merge score 7,024.5 / 21,546. Mean measured selection time approximately 0.022 / 4.65 ms on this workspace, including first-call setup in the enclosing benchmark timer. These eight fixed seeds are a small reproducible comparison, not independent mobile validation or a performance guarantee. Follow-up runs retain the same tile/score outcomes; timing can vary.

Command: `gradle :app:testDebugUnitTest --tests '*Board2048PlannerTest'`. Gradle's XML test result contains per-seed output. End-label tests separately render native Android glyphs and overlays, including every bundled translation, supported sizes, wrong labels and stale frames. Those synthetic images do not prove accuracy on real phone frames.
