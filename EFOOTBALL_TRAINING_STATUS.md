# eFootball Training — สถานะต้นแบบ

เชื่อมต้นแบบ Free Training ตามคำสั่งเจ้าของแล้ว: Activity/Screen แนวนอน → MediaProjection → bundled local Latin OCR + pixel reader → perception → policy/session/controller → Android gestures → รายงานในเครื่อง. ยังอยู่ระหว่างตรวจเพื่อปล่อยรุ่นทดลอง ไม่อ้างว่าผ่านบน Xiaomi หรือเล่นแมตช์เต็มแล้ว

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
