# eFootball Training — สถานะต้นแบบ

เชื่อมต้นแบบ Free Training ตามคำสั่งเจ้าของแล้ว: Activity/Screen แนวนอน → MediaProjection → bundled local Latin OCR + pixel reader → perception → policy/session/controller → Android gestures → รายงานในเครื่อง. เผยแพร่ Build39 แก้จับเครื่องหมายผู้เล่นและเรดาร์แล้ว (source commit5259f36) รุ่นแก้ยังต้องยืนยันบน Xiaomi ไม่ใช่รุ่นแมตช์เต็ม

วิธีใช้และข้อจำกัด: [EFOOTBALL_TRAINING_GUIDE.md](EFOOTBALL_TRAINING_GUIDE.md)

## ขอบเขตจริง

เป้าหมายคงที่ `jp.konami.pesam`. ผู้ใช้เปิดสนาม Free Training เอง เลือกทิศบุก/สีจุดทีมในเรดาร์ เริ่มจาก GameBot แล้วอนุญาตจับภาพทั้งจอ. รอเมนู/สนามเริ่มต้น60วินาที รองรับ Back ครั้งเดียวเฉพาะเมนูฝึกหกช่องที่ยืนยันครบสองเฟรม จำกัดช่วงฝึก2นาทีเริ่มเมื่อพบสนาม. ออกจากเกม/unknown focus/หมุนจอ/พักเกม/เมนู/รีเพลย์จะหยุด ไม่ resume เอง. ไม่เปลี่ยน ArenaGate ของเกมทดสอบเดิม ไม่รองรับ PvP/ซื้อของ/เมนู

ไอคอน Home/Away อย่างเดียวแยก Training ไม่ได้: เพิ่ม veto สี scoreboard ปัจจุบันและ OCR นาฬิกา/คะแนนในมุมบนซ้าย. มีเทสต์ภาพแมตช์จริง แม้ใส่ตำแหน่งปุ่มแบบเดียวกับเจ้าของ. หลักฐาน4ภาพไม่ใช่การรับรองกับทุกหน้าตาเกม

## ความสามารถรุ่นแรก

- เลี้ยง ส่ง ยิงระยะใกล้ และคุมพื้นที่ด้วยตัวปัจจุบันตามภาพที่อ่านได้
- คง `playerCoverageConfirmed=false` ใน visual pilot และเปิดนโยบาย partial coverage เฉพาะ config ของ runtime นี้. ค่าเริ่มต้นของ policy/session ยังคงบล็อกเมื่อ coverage ไม่ครบ. ไม่อ้างว่าเลนว่างจากการไม่เห็นตัวบล็อก
- ไม่สลับตัวแบบสุ่ม ไม่ใช้ Advanced feint/tackle sequence/lูกตั้งเตะจนกว่าจะจับคู่ marker และจังหวะได้. Catalogue สกิลยังไม่มีสูตรเปิดใช้
- การอ่านสี/ความมั่นใจเป็น heuristic และตั้งแต่ Build36 การแปลงทิศใช้ preset Dynamic Wide แบบประมาณโดยไม่วัดเส้นสนามทุกเฟรม ไม่ใช่การสอบเทียบกล้องหรือโมเดลที่ฝึกจากโปรเพลเยอร์
- OCR model อยู่ใน APK ลบ INTERNET และ ACCESS_NETWORK_STATE จาก merged manifest. ไม่มี Cloud/API/การเขียนภาพหน้าจอ
- คำสั่งผูกกับ source frame และ controls/bearing, foreground ตรวจซ้ำตอนส่งภายใต้ lock. สัมผัสไม่ค้างข้ามคำสั่ง ช่วงละไม่เกิน180ms. Callback สัมผัสไม่ถูกนับเป็นผลการเล่น

## การตรวจและสิ่งที่พบจริง

แกนก่อนหน้า commit9cea141 ผ่าน41เทสต์และ CIแล้ว. รอบเชื่อม runtime นี้เพิ่ม pixel fixtures จริงและ Screen/HUD tests. ชุด Gradle testDebugUnitTest ทุกโมดูลผ่าน199กรณี ไม่มีล้ม/ข้าม และ assembleDebug ทั้ง3 APK ใน workspace ผ่านแล้ว (log /workspace/toolchains/efootball-final-all.log). ตรวจ diff อิสระซ้ำหลังแก้และยืนยัน positive fixture สร้าง plan ได้โดยไม่พึ่งจอย. ลายเซ็น APK ตรงชุดเดิมและไม่มี INTERNET/ACCESS_NETWORK_STATE ใน APK. ยังคงไม่ใช่ผลมือถือหรือผล OCR engine บน Xiaomi

Independent review พบและนำมาแก้: ไอคอนฝึกมีในแมตช์ด้วย, markerวงเดียวแตกหลายcomponentทำให้ไม่ออกคำสั่ง, การใช้จอย/ส่วนโค้งแทนเส้นสนาม, และ telemetryท้ายรอบหายเมื่อ callbackตามหลังภาพสุดท้าย. ผลรอบสุดท้ายจะรายงานแยกจากการทดสอบบนมือถือ

ไม่มีการอ้างว่าโหมดฝึกได้รับอนุญาตบอทภายนอกจาก Konami โดยอัตโนมัติ เงื่อนไขผู้ให้บริการที่เคยคุยยังเป็นข้อจำกัดแยกจากการไม่แก้ไฟล์เกม

## Build34 — ตรวจไฟล์เผยแพร่แล้ว

- GitHub Actions Build APK run43689586878 success: https://github.com/hxyhan2006-code/GameBot/actions/runs/43689586878
- Verify core run43689567927 success. เทสต์ทั้งโปรเจกต์ใน workspace199กรณี ไม่มี failed/error/skipped
- Release target65a55741202c4d6219adb1df97dedbbb9940f2d7; ไฟล์ UniversalGameBot-build34.apk ขนาด53,071,585bytes
- ดาวน์โหลด APK จาก Release กลับมาตรวจจริง: applicationId com.example.universalgamebot, versionCode34, versionName0.11.0-efootball-training
- Signer SHA256 22f12cc3a50a14c72e9570f8789f5ef47c2e51515d85ffc5d631c07b35044947 ตรงกุญแจเดิม; ไม่พบ INTERNET/ACCESS_NETWORK_STATE ใน permissions ของ APK
- APK SHA256 402907829bad4bb66ad77b4ec94f5d43395ad3d7cd855ea2ce73f2e3f326cfa6
- ยังไม่มีผล physical touch/ML Kit engine/การเล่นจริงบน Xiaomi จากเจ้าของ รุ่นนี้เป็นต้นแบบพื้นฐานตามข้อจำกัดในคู่มือ

## แก้ v0.11.1 — เผยแพร่ Build35

เจ้าของแจ้ง Build34 ไม่เล่น โดยภาพแสดง Training Pause ตอนกลับเข้าเกมและปุ่ม Clear แทน Shoot ในสนาม. แก้การอ่าน Clear/ความสามารถยิงแยกกัน, แยก scene สนามจาก controls ที่อ่านไม่ครบ, เก็บ OCR tokens ในรายงาน, invalidated queued decision ไม่ใช่ physical failure, startup60วินาทีและเริ่มงบเล่นเมื่อพบสนามจริง. รองรับ Back ครั้งเดียวก่อนเริ่มเล่นเฉพาะเมนูหกช่องที่ยืนยันสองเฟรมสดและตรวจ foreground/geometry/อายุภาพก่อน dispatch. Watchdog แยกช่วงพักเริ่มต้นจากช่วงเล่นและ rebase เมื่อเข้าจริง ห้ามอ่านภาพแอปอื่นและห้าม auto Back หลัง arm

ชุดทดสอบที่เกี่ยวข้องใน workspace ผ่าน59กรณี ไม่มีล้ม/ข้าม และ build ผ่าน (efootball-focus-final.log). Independent diff review ปิด blocker watchdog แล้วและไม่พบ blockerใหม่. เจ้าของส่งรายงานจริง3รอบ frames=0/target focus lost or unknown ยืนยันว่าหยุดก่อนอ่านภาพ จึงเพิ่ม focus-window metadata validation และ focus diagnostics; ไม่ถือว่ารายงานนี้พิสูจน์ OCR/บอล/กล้องถูกต้องแล้ว. CI และ Release ผ่านแล้ว ยังต้องยืนยันบนเครื่องรุ่นแก้

Build35: source a251919e04244264ff00dec90db8b25e137d0aa9, run43695450290 success, core verification43695436302 success. ดาวน์โหลด asset UniversalGameBot-build35.apk กลับมาตรวจจริง: versionCode35 / 0.11.1-training-startflow, ขนาด53,105,877bytes ลายเซ็นเดิม22f12cc3a50a14c72e9570f8789f5ef47c2e51515d85ffc5d631c07b35044947, ไม่มี INTERNET/ACCESS_NETWORK_STATE. APK SHA256 7aaa598d1972a2f16ebfa2e9952d5155cdbf8a0b50393cda5d3a4cb427418657

## v0.11.2 — เผยแพร่ Build36

แก้จากรายงาน Build35: transient live1ก่อนPauseทำให้armเร็วเกินไป และรอบที่กลับสนามสำเร็จ55เฟรมมี0commandsเพราะsource stale/camera axes gate. เพิ่มภาพวิเคราะห์1440กว้าง+native-coordinate mapping, Dynamic Wide preset approximationโดยไม่วัดเส้นทุกเฟรม, stable readiness2เฟรม/200ms, ป้าย passive GameBotแยกwaiting/reading/recent-sending/stoppedกับdiagnostic pipeline timing. ไม่เพิ่มfreshness500ms ไม่อ้างผลมือถือหรือผลยิง/การเคลื่อนไหวจริงจากAndroid callback. ชุดที่เกี่ยวข้องใน workspace ผ่าน66กรณี ไม่มีล้ม/ข้าม และ assemble ผ่าน (efootball-status-final.log). Independent review หลังแก้ lifecycle ป้ายและข้อความ preset ไม่พบ blocker. CI/release สำเร็จแล้ว ยังต้องยืนยัน latency/gesture/overlay บนโทรศัพท์จริง

- Build APK run43701660326 success: https://github.com/hxyhan2006-code/GameBot/actions/runs/43701660326
- Core verification run43701460407 success. Release source66a3a03f2946e29d2ad1a83e7c4fc5188297090e
- ดาวน์โหลด UniversalGameBot-build36.apk จาก Release กลับมาตรวจ: ขนาด53,124,377bytes, applicationId com.example.universalgamebot, versionCode36 / 0.11.2-training-status
- Signer SHA256 22f12cc3a50a14c72e9570f8789f5ef47c2e51515d85ffc5d631c07b35044947 ตรงกุญแจเดิม ไม่พบ INTERNET/ACCESS_NETWORK_STATE/SYSTEM_ALERT_WINDOW
- APK SHA256 233827a659a50b3d2fb664392e0b7d3ff130f5d63e3a2b7c7d94f167a20091e4

## v0.11.3 — เผยแพร่ Build38 จากผลมือถือ Build36

เจ้าของยืนยันยังไม่เล่น frames74/live69/commands0/armedfalse. Backสำเร็จและpipeline319ms (OCR262ms) จึงไม่ใช่ภาพช้าเกิน500msในรอบนี้. เฟรมสุดท้ายมี controlOCR=[4Through|Pass|Clear|Da] และหยุดscoreboard/time; ภาพเจ้าของเป็นสนามฝึก และปัดดูเวลา/แบต. โค้ดเก่ารับ clockเดี่ยวจากหัวจอเป็นMATCH จึงมีช่องให้แถบระบบถูกตีความเป็นแมตช์ แต่รายงานเก่าไม่ได้เก็บข้อความหัวจอจริง ห้ามอ้างว่ารู้คำ OCR สุดท้ายแน่นอน

รุ่นแก้แยก headerclockที่ไม่มีคะแนนเป็นUNKNOWN (ยังไม่ส่งคำสั่ง) แทนMATCH; pixel scoreboard และ clock+คะแนนยังปฏิเสธแมตช์. เปลี่ยน preprocessing เป็นสี่ช่องปุ่มStandardขยาย2xและยกcontrastก่อนML Kit โดย mappingกลับพิกัดต้นทาง. รองรับDa/DasเฉพาะมุมDashและต้องมีอีกสามปุ่มครบ. เพิ่มwaitingReasons, anchor diagnostics, ocrInput dimensions, ป้ายอ่านจอขณะเห็นสนามแต่ยังไม่พร้อม. ไม่เปลี่ยนfocus/freshness/startup Back/play deadline/stop latch และไม่มีการauto-resumeหลังเริ่มเล่น

ชุดที่เกี่ยวข้องใน workspace ผ่าน74กรณี ไม่มีล้ม/ข้าม และ assembleDebug ผ่าน (efootball-vision38-final.log). รวม regression เวลาโทรศัพท์/clock+score/score-only, reported OCR tokensบนpublic training pixels, tile mapping/contrastทั้งปุ่มจางและทึบ, waitingReasons และป้ายก่อนarm. Independent diff review ไม่พบblocker; ข้อสังเกต score-only ได้เพิ่มการบล็อกUNKNOWNและตรวจซ้ำแล้ว. ผลนี้ไม่ใช่การรันML Kit engineบนXiaomi ไม่ได้ทดสอบสัมผัสจริงหรือรับรองว่าปุ่ม20%จะอ่านได้ CI/release สำเร็จแล้ว

ก่อนเผยแพร่ตรวจ atlas จริงด้วย production ImageSampler/OcrInput จาก public training.jpg พบ absolute clipping ลบข้อความบนปุ่มทึบ จึงยกเลิก workflow Build37 run43708105666 ก่อนเผยแพร่ (ไม่มี Release37). เปลี่ยนเป็น local contrast จาก integral image เพิ่ม regressionทั้งจาง/ทึบและตรวจ diff อิสระซ้ำแล้ว. ตรวจภาพ atlas รอบแก้เห็น Through/Shoot/Pass/Dash ชัด เป็นการตรวจภาพอ้างอิงด้วยคน ไม่ใช่ผล OCR engine หรือภาพมือถือเจ้าของ

- Build APK run43708925343 success: https://github.com/hxyhan2006-code/GameBot/actions/runs/43708925343
- Core verification run43708901760 success. Release sourcee45e9508f03dc14db4a4899d868d72c3cb5fe36a
- ดาวน์โหลด UniversalGameBot-build38.apk กลับมาตรวจ: ขนาด53,140,757bytes, applicationId com.example.universalgamebot, versionCode38 / 0.11.3-training-vision
- Signer SHA256 22f12cc3a50a14c72e9570f8789f5ef47c2e51515d85ffc5d631c07b35044947 ตรงกุญแจเดิม ผ่าน apksigner สำหรับ SDK36, zipalign16KB และ ZIP CRC/ไม่มีชื่อไฟล์ซ้ำ. ไม่มี INTERNET/ACCESS_NETWORK_STATE/SYSTEM_ALERT_WINDOW
- APK SHA256 c3f760f8c94072d636e8c57ece66cbafb2eb6f8f9c695966d1e4fbb32c991187

## v0.11.4 — เผยแพร่ Build39 จากผลมือถือ Build38

เจ้าของตั้งButton Opacity100%แล้ว รายงานframes749/live743/armedtrue/commands11/AndroidCompleted11/failures0 จบtraining deadline. ภาพ182ms (OCR95ms) กับปุ่มครบยืนยันว่าเหตุรอหลักรอบนี้ไม่ใช่OCRหรือframe stale. waitingReasons649ครั้งคือsolid controlled-player marker not established; เฟรมสุดท้ายselectedfalse/radarSelectedtrue/ballfalse/hudtrue. AndroidCompletedไม่พิสูจน์ว่าตัวละครเคลื่อนหรือเล่นสำเร็จ และ649ไม่ใช่จำนวนเฟรมบอลหายเพราะรายงานเลือกเหตุแรก

ตรวจคลิปtrainingสาธารณะที่ใช้fixtureเดิมช่วง3–11วินาที16เฟรม (2fps) ด้วยproduction sampler/reader: ก่อนแก้field selected9/16, radar selected15/16, ball15/16. พบcyan advertisingเข้าเงื่อนไขสีพร้อมสามเหลี่ยมจริง ทำให้singleOrNullล้างselected และเศษgoldห่างจากบอลทำให้mergeทั้งเรดาร์ล้ม. รุ่นแก้แยก connected components พร้อมboundsจริง, ตรวจsolid downward triangle+grass, จัดกลุ่มglyphเฉพาะจุด และอ่านradar nativeจากcapture imageเดียวกันก่อนOCR. หลังแก้field/radar/ballครบ16/16ในชุดนี้ ไม่ใช่ผลมือถือและไม่รับรองทุกสนาม/ธีม/สถานการณ์

เพิ่มpublic fixturesสองเฟรมสำหรับโฆษณาและเศษสีในเรดาร์ พร้อมการตรวจลำดับหลายเฟรม→controller/backendจำลอง, การหาย/กำกวมของmarker, ไม่มีบอลแม้พบตัวผู้เล่น, native sampling/row padding. ภาพอ้างอิงอยู่เฉพาะtest resources ไม่อยู่ในAPK. Scope/freshness/geometry/generation/menu/play deadlines ไม่เปลี่ยน ไม่เปิดfull match/auto-resume/สุ่มสลับตัว และไม่เก็บภาพมือถือเพิ่ม

ชุดตรวจที่เกี่ยวข้องในworkspaceผ่าน81กรณี ไม่มีล้ม/ข้าม และassembleDebugผ่าน (efootball-markers39-final.log). ตรวจdiffอิสระเทียบโค้ดจริงและภาพอ้างอิงแล้วไม่พบfindingที่ยืนยันได้. CI/releaseสำเร็จแล้ว ยังต้องยืนยันผลอ่านภาพ/สัมผัสบนโทรศัพท์จริง

- Build APK run43715071117 success: https://github.com/hxyhan2006-code/GameBot/actions/runs/43715071117
- Core verification run43715039376 success. Release source5259f367bd4952a9fa1f1f5d8ba0ac04c9beb438
- ดาวน์โหลด UniversalGameBot-build39.apk กลับมาตรวจ: ขนาด53,157,145bytes, applicationId com.example.universalgamebot, versionCode39 / 0.11.4-training-markers
- Signer SHA256 22f12cc3a50a14c72e9570f8789f5ef47c2e51515d85ffc5d631c07b35044947 ตรงกุญแจเดิม ผ่านapksignerสำหรับSDK36, zipalign16KB, ZIP CRC/ไม่มีชื่อซ้ำ. ไม่มี INTERNET/ACCESS_NETWORK_STATE/SYSTEM_ALERT_WINDOW และไม่มีภาพpublic test fixturesในAPK
- APK SHA256 7300563b966a06f6fefa0a54049ac182c7cf2d64d6255ea6e8d5e400b8924e90

## v0.12.0 — ระหว่างตรวจจากผลมือถือ Build39

เจ้าของยืนยันบอลไม่ขยับตั้งแต่ต้นจนจบ แม้commands78/AndroidCompleted78/failures0; frames590/live588/armedtrue/pipeline155ms, waitingReasons371ครั้งคือfield triangleอ่านไม่ได้. ภาพเป็นจุดเริ่มกลางสนามกับหน้าพักการฝึก เจ้าของสั่งให้อ่านหน้านี้และBackเองทุกครั้งที่พบระหว่างรัน. เจ้าของตอบยืนยันภายหลังว่าจุดเริ่มนี้ต้องกดPassก่อน จึงยืนยันว่าการเลือกเลี้ยงอย่างเดียวไม่เริ่มเล่นจากภาพนี้ ส่วนAndroid callbacksยังไม่พิสูจน์inputเข้าเกมหรือผลการเล่น รุ่นแก้ยังต้องยืนยันสัมผัสจริงหลังPass

งานแก้รอบนี้: (1) centre ball+starting formationหลายเฟรม→observed backward/forward pass candidate (2) จอยmove35msแล้วholdถึง320msจริงด้วยcontinueStrokeแทนPathเส้นเดียวที่วิ่งสม่ำเสมอจนยก (3) continuation authorize+dispatchในcontroller lockเดียว ตรวจscope/current frameอีกครั้งและrelease-onlyเมื่อต่อไม่ได้ (4) native radar role corroborationจากสองfield/radarคู่>=200ms เพื่อใช้CURRENT cursorช่วงtriangleblink ไม่แทนพิกัดเก่า (5) visual feedback/actor identity/บอลร่วมสำหรับdribble/หยุดno-response แยกจากAndroid callback (6) OCRsemantic six-tile pauseและตำแหน่งBackทุกappearanceระหว่างรัน พร้อมverified fresh-field returnและrearm ไม่ต่ออายุ2-minute deadline

ไม่ขยายไปเกม/แอปอื่นหรือแมตช์จริง True foreign focus/match/geometry lossยังstop latched; unknown transitionหยุดinputทันทีและต้องพบเมนูฝึกที่รู้จักภายใน1.5วินาทีจึงรอresumeได้. ป้ายเขียวขึ้นจากmotion evidence ไม่ใช่ส่งคำสั่งสำเร็จเฉย ๆ เป็นscreen classification/OCRที่รองรับหน้านี้ ไม่ใช่การฝึกLLM/AIโมเดลใหม่

ชุดตรวจที่เกี่ยวข้องในworkspaceผ่าน105กรณี ไม่มีล้ม/ข้าม และassembleDebugผ่าน (efootball-feedback40-final-reviewed.log). รวมactual Android descriptorsของ35ms→hold/second pointer, no-button release-onlyเมื่อscope/staleเปลี่ยน, callback timeout/late callback, concurrency lock, recurring menus/rearm/deadline, native-cue fallbackหลังcorroboration/หายปัจจุบันต้องบล็อก และfake Android-success+stationary visualต้องไม่เป็นmotion. ผู้ตรวจอิสระพบTOCTOUcontinuation ข้อความรอผิดเหตุการณ์ และown-MOVE missing-ballถูกนับunchangedผิด นำมาแก้พร้อมnegative regressionsและตรวจซ้ำแล้วไม่พบfindingเพิ่ม. ผลนี้ไม่ใช่สัมผัสจริงบนXiaomi ยังต้องCI/releaseและผลมือถือ
