# eFootball Training — สถานะต้นแบบ

เชื่อมต้นแบบ Free Training ตามคำสั่งเจ้าของแล้ว: Activity/Screen แนวนอน → MediaProjection → bundled local Latin OCR + pixel reader → perception → policy/session/controller → Android gestures → รายงานในเครื่อง. เผยแพร่ Build34 แล้ว (source commit65a5574) ไม่อ้างว่าผ่านบน Xiaomi หรือเล่นแมตช์เต็มแล้ว

วิธีใช้และข้อจำกัด: [EFOOTBALL_TRAINING_GUIDE.md](EFOOTBALL_TRAINING_GUIDE.md)

## ขอบเขตจริง

เป้าหมายคงที่ `jp.konami.pesam`. ผู้ใช้เปิดสนาม Free Training เอง เลือกทิศบุก/สีจุดทีมในเรดาร์ เริ่มจาก GameBot แล้วอนุญาตจับภาพทั้งจอ. รอเข้าเกม20วินาที รอหลักฐานสนามฝึก15วินาที จำกัดช่วงฝึก2นาที. ออกจากเกม/unknown focus/หมุนจอ/พักเกม/เมนู/รีเพลย์จะหยุด ไม่ resume เอง. ไม่เปลี่ยน ArenaGate ของเกมทดสอบเดิม ไม่รองรับ PvP/ซื้อของ/เมนู

ไอคอน Home/Away อย่างเดียวแยก Training ไม่ได้: เพิ่ม veto สี scoreboard ปัจจุบันและ OCR นาฬิกา/คะแนนในมุมบนซ้าย. มีเทสต์ภาพแมตช์จริง แม้ใส่ตำแหน่งปุ่มแบบเดียวกับเจ้าของ. หลักฐาน4ภาพไม่ใช่การรับรองกับทุกหน้าตาเกม

## ความสามารถรุ่นแรก

- เลี้ยง ส่ง ยิงระยะใกล้ และคุมพื้นที่ด้วยตัวปัจจุบันตามภาพที่อ่านได้
- คง `playerCoverageConfirmed=false` ใน visual pilot และเปิดนโยบาย partial coverage เฉพาะ config ของ runtime นี้. ค่าเริ่มต้นของ policy/session ยังคงบล็อกเมื่อ coverage ไม่ครบ. ไม่อ้างว่าเลนว่างจากการไม่เห็นตัวบล็อก
- ไม่สลับตัวแบบสุ่ม ไม่ใช้ Advanced feint/tackle sequence/lูกตั้งเตะจนกว่าจะจับคู่ marker และจังหวะได้. Catalogue สกิลยังไม่มีสูตรเปิดใช้
- การอ่านสี/ความมั่นใจเป็น heuristic และการแปลงทิศจากเส้นสนามเป็น approximation สำหรับ Dynamic Wide; ไม่ใช่โมเดลที่ฝึกจากโปรเพลเยอร์
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

## แก้ v0.11.1 ระหว่างตรวจ

เจ้าของแจ้ง Build34 ไม่เล่น โดยภาพแสดง Training Pause ตอนกลับเข้าเกมและปุ่ม Clear แทน Shoot ในสนาม. แก้การอ่าน Clear/ความสามารถยิงแยกกัน, แยก scene สนามจาก controls ที่อ่านไม่ครบ, เก็บ OCR tokens ในรายงาน, invalidated queued decision ไม่ใช่ physical failure, startup60วินาทีและเริ่มงบเล่นเมื่อพบสนามจริง. รองรับ Back ครั้งเดียวก่อนเริ่มเล่นเฉพาะเมนูหกช่องที่ยืนยันสองเฟรมสดและตรวจ foreground/geometry/อายุภาพก่อน dispatch. Watchdog แยกช่วงพักเริ่มต้นจากช่วงเล่นและ rebase เมื่อเข้าจริง ห้ามอ่านภาพแอปอื่นและห้าม auto Back หลัง arm

ชุดทดสอบที่เกี่ยวข้องใน workspace ผ่าน59กรณี ไม่มีล้ม/ข้าม และ build ผ่าน (efootball-focus-final.log). Independent diff review ปิด blocker watchdog แล้วและไม่พบ blockerใหม่. เจ้าของส่งรายงานจริง3รอบ frames=0/target focus lost or unknown ยืนยันว่าหยุดก่อนอ่านภาพ จึงเพิ่ม focus-window metadata validation และ focus diagnostics; ไม่ถือว่ารายงานนี้พิสูจน์ OCR/บอล/กล้องถูกต้องแล้ว. ยังต้อง CI/release และยืนยันบนเครื่องรุ่นแก้
