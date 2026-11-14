# eFootball Mobile — หลักฐานภาพและวิดีโอที่ตรวจได้

ฉบับประกอบการหารือวันที่เครื่องเจ้าของ 15 พฤศจิกายน 2026. เอกสารนี้เพิ่มข้อมูลสำหรับต้นแบบโหมดฝึกที่อนุมัติไว้ ไม่ใช่ผลว่าบอทเล่นได้แล้ว และไม่มีการรวมไฟล์ภาพ/วิดีโอของบุคคลอื่นลง repo

## ค่าตั้งจากภาพเจ้าของ

ยืนยันจากภาพที่ส่งในแชต ไม่ใช่ค่า default ที่เดา:

- Smart Assist: Off
- Control Type: Virtual Pad
- Directional Stick Type: Off
- Cursor Change: Manual
- Movement Input Range: Medium
- Feint Command Type: Advanced
- Defending Control Type: Standard
- Button Opacity: 20%
- รายการ Cursor Change Controls แสดง Double Tap & Flick เป็นสีเทา จึงไม่ถือว่าข้อความนี้ยืนยันวิธีสลับตัวที่กำลังใช้กับ Virtual Pad

Stick Type Off ไม่ใช่ปิดความสามารถควบคุมทิศทาง: ภาพสนามไม่มีวงจอยแสดงถาวร แต่ตัวควบคุมยังต้องส่งการลากในฝั่งซ้าย จึงห้ามตั้งเงื่อนไขว่าต้องตรวจพบรูปจอยก่อนส่งคำสั่งเสมอ และไม่อนุมานว่า Off คือ Fixed. ยังไม่ได้ยืนยันศูนย์เริ่มสัมผัส/ช่วงลากบนเครื่องจริงจากการทดลอง; [แพตช์ทางการเรื่อง Movement Input Range](https://www.konami.com/efootball/en/page/2022/season2_patch-notes_v1-1-4) กล่าวถึงการรับ input ของผู้ตั้ง Stick Type Off ด้วย

ภาพสนามเจ้าของสองภาพช่วยยืนยัน marker สามเหลี่ยมสีฟ้าทึบเหนือผู้เล่นและ marker โปร่งของผู้เล่นอื่น โดยภาพรับมีวงสีฟ้าใต้ตัวที่คุมด้วย. ไม่ใช้ชื่อนักเตะหรือวงใต้เท้าเพียงอย่างเดียวระบุตัว active และไม่กำหนดว่าวงทุกสถานการณ์ต้องสีเดียวกัน

## สูตร Advanced จากภาพ Command List ในเกมจริง

ต้นทาง: [ผู้เล่นโพสต์ภาพหน้าต่าง Feints](https://www.reddit.com/r/pesmobile/comments/1sggu2r/body_feint_returns_to_mobile_version_skill_moves/) วันที่หน้า Reddit ชี้ 9 เมษายน 2026. เปิดไฟล์ต้นฉบับทั้งสามภาพและตรวจด้วยเครื่องมือดูภาพแล้ว ไม่ได้อ่านแค่ชื่อโพสต์

1. [Marseille Turn / Double Touch](https://i.redd.it/bco06hc5q3ug1.jpg)
2. [Double Touch / Draw and Open / Draw and Close](https://i.redd.it/gy5mk3l5q3ug1.jpg)
3. [Flip Flap / Scissors Feint / Body Feint](https://i.redd.it/yaqe8ht5q3ug1.jpg)

F หมายถึงทิศที่ผู้เล่นกำลังหันหน้า, B คือทิศตรงข้าม, S คือด้านข้างตั้งฉากกับ F. ทิศนี้ไม่จำเป็นต้องตรงกับทิศบุกหรือทิศจอยล่าสุด

| ท่า | สูตรที่อ่านได้จากภาพ |
|---|---|
| Marseille Turn | ลาก Dash ไป F จากนั้นลากฝั่งซ้ายไป S |
| Double Touch | ลาก Dash ไป S และลากฝั่งซ้ายไป F; ภาพไม่ได้กำหนดระยะหน่วงเป็นตัวเลข |
| Draw and Open / L Feint | ลาก Dash ไป B จากนั้นลากฝั่งซ้ายตั้งฉากกับทิศนั้น |
| Draw and Close / Scotch Move | ลาก Dash ไป B และลากฝั่งซ้ายไป F |
| Out & In / Flip Flap | แตะ Dash แล้วปัดฝั่งซ้ายเฉียง 45 องศาจาก F |
| Scissors Feint | ลากโค้งผ่านด้านหน้ารอบ Dash จากด้านข้างหนึ่งไปด้านตรงข้าม |
| Body Feint | ลากโค้งผ่านด้านหลังรอบ Dash จากด้านข้างหนึ่งไปด้านตรงข้าม |

ตารางเป็นการสรุปภาพคำสั่ง ไม่ได้ยืนยันเวลา ms ระยะพิกเซล จังหวะนิ้วซ้อน จุดเริ่ม touch ของท่าโค้ง เงื่อนไขการ์ด หรือผลสำเร็จของ executor บน Xiaomi. ภาพไม่มีเลข patch ให้ตรวจตรงกับเครื่องเจ้าของ. ใช้ร่วมกับ [การเพิ่ม Advanced บน Mobile ใน v5.4](https://www.konami.com/efootball/en/page/v5/versioninfo_v5-40) โดยไม่แปลงเป็นคำรับรองว่าทดลองได้ผลแล้ว

สูตรชุมชนแบบ “ปัด Dash ทิศไหนก็ได้” ไม่ใช้แทนตารางที่อิงทิศตัวนักเตะทั้งหมด และไม่เอาคู่มือ Bluetooth controller มาแทนคำสั่งสัมผัส

## วิดีโอผู้เล่นที่เข้าถึงไฟล์และตรวจเฟรมจริงแล้ว

ดาวน์โหลดจาก URL วิดีโอสาธารณะได้ HTTP200 ตรวจ metadata ด้วย ffprobe และดูเฟรมตัวอย่าง/ภาพรวมตามลำดับแล้ว. ไม่ได้ดูมือผู้เล่น ไม่ได้อ่าน touch-log และไม่อ้างว่าตรวจทุกเฟรมในทุกคลิป. ค่า 30fps ด้านล่างคือ frame rate ของไฟล์ ไม่ยืนยันค่า FPS ในเกม

### A. ฝึกซ้อมและภาพเมนูแทรก

- [Double touch not working — 14 ต.ค. 2026](https://www.reddit.com/r/pesmobile/comments/1x5sfjm/double_touch_not_working/)
- [ไฟล์สาธารณะ](https://v.redd.it/jkh0we98yfvh1/CMAF_1080.mp4?source=fallback)
- 14.90 วินาที, 1920×888, 30fps
- เห็นฉากฝึก Messi, ปุ่มสี่อันขวา, radar กลางล่าง, marker เหนือหัวและวงใต้เท้า, กล้องเลื่อนตามการเคลื่อนที่
- มี iOS Control Center ต้น/ท้ายและหน้า Home/Away คั่น จึงใช้ตรวจการปฏิเสธ overlay/menu ได้ ห้ามถือทั้งไฟล์เป็น live play ต่อเนื่อง
- ชื่อโพสต์บอกว่าทำ Double Touch ไม่สำเร็จ ไม่ใช้เป็นตัวอย่างท่าที่ทำสำเร็จแล้ว

### B. เปลี่ยนปุ่มรุกเป็นรับ

- [Offline Input Delay Proof — 10 ก.ค. 2026](https://www.reddit.com/r/pesmobile/comments/1usj8vo/offline_input_delay_proof_pressure_button_doesnt/)
- [ไฟล์สาธารณะ](https://v.redd.it/c32kixhfldch1/CMAF_1080.mp4?source=fallback)
- 27.93 วินาที, 1920×864, 30fps
- เห็นชุด Dash/Shoot/Through/Pass เปลี่ยนเป็น Switch/Match-up/Tackle/Dash & Pressure พร้อม radar, marker ทึบและ marker โปร่ง
- ตำแหน่งปุ่มถูกจัดต่างจากภาพเจ้าของ จึงไม่คัดพิกัดไปใช้โดยตรง
- คลิปมีการตัดย้อนเวลาเกม 45:37 ไป 45:11 ต้องแบ่งช่วงก่อนใช้คำนวณความเร็วหรือผลของคำสั่ง
- ชื่อโพสต์อ้าง input delay แต่ไม่มีหลักฐานเวลาแตะกับผลที่วัดเอง จึงยังไม่ยืนยันสาเหตุ/จำนวน ms

### C. ต่อบอล เลี้ยง ยิง และฉลอง

- [One of the smoothest double touch… — 1 ต.ค. 2026](https://www.reddit.com/r/pesmobile/comments/1wv6imb/one_of_the_smoothest_double_touch_i_have_done_in/)
- [ไฟล์สาธารณะ](https://v.redd.it/x1qz2sfsawsh1/CMAF_1080.mp4?source=fallback)
- 9.93 วินาที, 1546×1080, 30fps
- เห็นทีมเสื้อแดงส่งบอลขึ้นเกมไปทางประตูซ้าย มีวง/marker เปลี่ยนตัวที่คุม แล้วมีจังหวะยิงและเปลี่ยนเป็นฉากฉลอง
- ไม่มีมือผู้เล่นหรือ touch-log จึงไม่ใช้ชื่อ Double Touch ของโพสต์เป็นหลักฐานว่าแกะ input ท่านั้นสำเร็จแล้ว
- รูปแบบปุ่มรับมี Pressure/Dash แยก ต่างจาก Standard ของเจ้าของ และอัตราส่วนภาพต่างจากอีกสองคลิป ใช้ศึกษาฉาก/การเคลื่อน แต่ไม่ยก layout นี้มาใช้กับเครื่องเจ้าของ

### D. ตัวอย่างที่ต้องจัดเป็น Replay

- [May I know why the defender stopped… — 30 ต.ค. 2026](https://www.reddit.com/r/pesmobile/comments/1xjacfp/may_i_know_why_the_defender_stopped_and_moving/)
- [ไฟล์สาธารณะ](https://v.redd.it/f5mpszyflkyh1/CMAF_1080.mp4?source=fallback)
- 6.50 วินาที, 1920×888, 30fps
- ตรวจเห็น Highlights, Back และ Goal Scorer Eden Hazard43′ ไม่มี live buttons/radar สำหรับควบคุม
- ใช้เป็นตัวอย่างลบสำหรับตัวแยก replay ไม่ใช้พิสูจน์การกดของผู้เล่นหรือสรุปสาเหตุที่กองหลังเคลื่อนผิด

## ข้อจำกัดและผลต่องานพัฒนา

ขณะนี้มีตัวอย่างสาธารณะสำหรับฉากฝึก, live/replay/menu, ชุดปุ่มรุก/รับ, marker/radar, กล้องเคลื่อน, ภาพตัดย้อน และ goal→celebration แล้ว. **ไม่ต้องให้เจ้าของส่งวิดีโอซ้ำเป็นเงื่อนไขเริ่มทำต่อ** ใช้ภาพเจ้าของเป็นหลักสำหรับค่าตั้งและ layout แล้วใช้คลิปเหล่านี้ตรวจการเปลี่ยนสถานะ

ยังไม่มีคลิปที่ยืนยันค่าตั้งตรงเจ้าของครบทุกข้อ โดยเฉพาะ opacity20%, Manual และ Advanced; การ์ด/ทีม/กล้อง/ขนาดจอไม่เหมือนกันทั้งหมด จึงต้องตรวจความทนทานของตัวอ่านภาพ ไม่อ้างว่าค่า threshold จากคลิปหนึ่งใช้กับ Xiaomi ได้แน่นอน

ภาพนิ่งยังไม่พิสูจน์การติดตามหลายเฟรม. ยังต้องทำ perception, แปลงทิศกล้อง, OCR/ตรวจปุ่ม และเชื่อมตัวส่ง gesture จริงก่อนปล่อย APK. สื่อเหล่านี้เป็นข้อมูลทดสอบที่ตรวจได้ ไม่ใช่ผลว่าบอทฝึกหรือเล่นสำเร็จแล้ว

ไฟล์สื่อเก็บเฉพาะ cache ชั่วคราวนอก repo ไม่อัปโหลด/แจกซ้ำผ่าน Release. หาก cache หายให้ตรวจแหล่งต้นฉบับและ metadata/hash ใหม่ ไม่เปลี่ยน URL ที่เข้าไม่ได้เป็นหลักฐานว่ายังได้ดูไฟล์แล้ว

## SHA256 ของไฟล์ที่ตรวจ

```text
CommandList1 0d3aa4061cd3f2870730593d974dc5ec5db39c53b72af70ab05bb7e84bed8a1f
CommandList2 4607ed417f5cf606220856ae7db4c37a5b60a9c73b73b3140fc7a90cc4efc039
CommandList3 f517a5bdfb98e2b22a7adbcfe37a1327c895cf6a410a68639c6bf6bd20da371d
TrainingA    05333d522bd589d16ded811f6aeef93570f569faedc06f42b23cabba0271c980
LiveB        e35e45e2e868f99a73c87f49b0dc543670a657ef00a479fb8ce6cf3e6ea577f9
LiveC        d59631ee812a41924a126d6e904d68ad85e7af3a31453439380d64fe930ab09d
ReplayD      f6ceb1088887f07f1632890c910f61614a06bdc1d114a0beacb384f124bff2af
```
