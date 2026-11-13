package com.example.universalgamebot

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.util.DisplayMetrics
import android.view.WindowManager

/**
 * ท่อหลัก: Screen Capture → Vision (VisionEngine) → Decision (DecisionEngine) → Gesture (GestureExecutor)
 * ไม่รู้จักเกมใดโดยตรง ทุกอย่างเฉพาะเกมมาจาก GameProfile ที่ส่งมาทาง Intent ("profile")
 */
class CaptureService : Service() {
    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private val handlerThread = HandlerThread("BotVision")
    private lateinit var handler: Handler
    @Volatile private var running = false

    private lateinit var profile: GameProfile
    private lateinit var vision: VisionEngine
    private var engine: BotEngine? = null
    private val sampled = SampledFrame()
    private var seq = 0L
    private var lastProcessAt = 0L
    private var session: SessionStats? = null
    private var lastFpsAt = 0L
    private var lastFpsCount = 0L
    private var finished = false
    // วินิจฉัยกรณีไม่มีภาพ: นับจำนวนครั้งที่ ImageReader เรียกกลับ และจำนวนที่ดึงภาพไม่ได้
    @Volatile private var imageCallbacks = 0
    @Volatile private var nullImages = 0
    private var captureStartedAt = 0L
    private var noFrameReported = false

    override fun onBind(intent: Intent?) = null
    override fun attachBaseContext(base: android.content.Context) { super.attachBaseContext(LocaleHelper.wrap(base)) }

    override fun onCreate() {
        super.onCreate()
        ErrorLog.init(this)
        handlerThread.start()
        handler = Handler(handlerThread.looper)
        val channel = NotificationChannel("bot", getString(R.string.notif_channel), NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        val stopIntent = PendingIntent.getService(this, 0, Intent(this, CaptureService::class.java).setAction("STOP"), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = Notification.Builder(this, "bot").setContentTitle(getString(R.string.notif_title)).setContentText(getString(R.string.notif_text)).setSmallIcon(android.R.drawable.ic_media_play).addAction(android.R.drawable.ic_delete, getString(R.string.notif_stop), stopIntent).build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        else startForeground(1, notification)
    }

    /** ขนาดจอจริงทั้งหน้าจอ (รวมแถบสถานะ/แถบนำทาง/รอยบาก) ใช้เป็นขนาด buffer และฐานของการแปลงพิกัด */
    private fun realScreenSize(): Pair<Int, Int> {
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        return if (Build.VERSION.SDK_INT >= 30) {
            val b = wm.maximumWindowMetrics.bounds
            b.width() to b.height()
        } else {
            val m = DisplayMetrics()
            @Suppress("DEPRECATION") wm.defaultDisplay.getRealMetrics(m)
            m.widthPixels to m.heightPixels
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") { EmergencyStop.trigger("stop from notification"); stopSelf(); return START_NOT_STICKY }
        if (running) return START_NOT_STICKY
        DebugMonitor.resetCounters()
        EmergencyStop.reset()
        OverlayState.clear()
        PerfMonitor.reset(this)
        ControlGestureStats.reset()
        DebugMonitor.captureState = "Starting"
        val data: Intent? = if (Build.VERSION.SDK_INT >= 33) {
            intent?.getParcelableExtra("data", Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra("data")
        }
        if (data == null) { fail("no projection data"); return START_NOT_STICKY }
        val code = intent?.getIntExtra("code", Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED
        profile = GameProfiles.byId(intent?.getStringExtra("profile"))
        DebugMonitor.profileName = profile.id
        val limit = intent?.getIntExtra("limit", 0) ?: 0 // 0 = ไม่จำกัด, N = หยุดเองหลัง N action (โหมดทดสอบ)
        val startedAt = SystemClock.elapsedRealtime()
        session = SessionStats(profile.id, startedAt, profile.config.visionIntervalMs).also { DebugMonitor.session = it }
        lastFpsAt = startedAt; lastFpsCount = 0; finished = false
        try {
            vision = VisionEngine(profile.createDetectors(this)) { msg, t -> DebugMonitor.error(msg, t) }
            val listener = object : EngineListener {
                override fun onSnapshot(snapshot: EngineSnapshot) { DebugMonitor.engine = snapshot }
                override fun onStop(reason: String, isError: Boolean) {
                    if (isError) DebugMonitor.error("bot stopped: $reason") else DebugMonitor.log("bot stopped: $reason")
                    stopSelf()
                }
                override fun onLog(message: String) { DebugMonitor.log(message) }
            }
            val control = profile.createControlStrategy(this)
            engine = if (control != null) {
                // โปรไฟล์ควบคุมต่อเนื่อง (จอย + ปุ่มพร้อมกัน): limit > 0 = หยุดเมื่อทำประตูติดกันครบ streakGoal หรือยิงครบ limit
                val rt = profile.config.realtime.let { if (limit > 0) it.copy(maxShots = limit, stopAtStreak = profile.config.streakGoal) else it }
                RealtimeEngine(control, rt, ContinuousGestureExecutor(packageName) { handler.post(it) }, listener, SystemClock::elapsedRealtime)
            } else {
                DecisionEngine(
                    profile.createStrategy(this) ?: throw IllegalStateException("profile ${profile.id} has no strategy"),
                    profile.config.engine.let { if (limit > 0) it.copy(maxActions = limit) else it },
                    GestureExecutor(packageName) { handler.post(it) }, listener, SystemClock::elapsedRealtime
                )
            }
            val mgr = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            projection = mgr.getMediaProjection(code, data)
            // Android 14+ บังคับ registerCallback ก่อน createVirtualDisplay
            projection?.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    // เรียกกลับทุกครั้งที่ projection หยุด รวมถึงตอนเราหยุดเอง: เฉพาะที่ระบบหยุดให้ขณะยังทำงานอยู่จึงนับเป็น error
                    if (running) DebugMonitor.error("MediaProjection stopped by system") else DebugMonitor.log("MediaProjection stopped (normal shutdown)")
                    stopSelf()
                }
            }, handler)
            val (w, h) = realScreenSize()
            val dpi = resources.displayMetrics.densityDpi
            reader = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 3)
            display = projection?.createVirtualDisplay("GameBotCapture", w, h, dpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader?.surface, null, handler)
            if (display == null) { fail("createVirtualDisplay returned null"); return START_NOT_STICKY }
            DebugMonitor.captureSize = "${w}x$h @${dpi}dpi"
            DebugMonitor.log("capture started ${DebugMonitor.captureSize} profile=${profile.id} reader=${reader?.width}x${reader?.height} surfaceValid=${reader?.surface?.isValid} displayState=${display?.display?.state}")
        } catch (e: Exception) {
            fail("setup failed: ${e.javaClass.simpleName}: ${e.message}", e)
            return START_NOT_STICKY
        }
        running = true
        imageCallbacks = 0; nullImages = 0; noFrameReported = false; captureStartedAt = SystemClock.elapsedRealtime()
        DebugMonitor.captureState = "Active"
        BotState.set(true)
        reader?.setOnImageAvailableListener({ source ->
            if (!running) { try { source.acquireLatestImage()?.close() } catch (_: Exception) {}; return@setOnImageAvailableListener }
            imageCallbacks++
            var acquireFailed = false
            val img: Image? = try { source.acquireLatestImage() } catch (e: Exception) { acquireFailed = true; session?.onAcquireError(); DebugMonitor.error("acquireLatestImage: ${e.message}", e); null }
            // null โดยไม่มี error = เฟรมนี้ถูกดึงไปพร้อมเฟรมที่ใหม่กว่าแล้ว (acquireLatest ทิ้งเฟรมเก่า) = เรายังประมวลผลไม่ทัน (backpressure)
            if (img == null) { nullImages++; if (!acquireFailed) session?.onSuperseded(); return@setOnImageAvailableListener }
            session?.onReceived()
            engine?.onFrameReceived(SystemClock.elapsedRealtime())
            try { processFrame(img) } catch (e: Exception) { DebugMonitor.error("frame error: ${e.javaClass.simpleName}: ${e.message}", e) } finally { img.close() }
        }, handler)
        handler.postDelayed(ticker, 40)
        return START_NOT_STICKY
    }

    private fun fail(msg: String, t: Throwable? = null) {
        DebugMonitor.captureState = "Error"
        DebugMonitor.error(msg, t)
        stopSelf()
    }

    /** Vision: สุ่มพิกเซลเฉพาะ arena → ตรวจจับ → แปลงพิกัดภาพเป็นพิกัดหน้าจอจริง → ส่งให้ Decision Engine */
    private fun processFrame(img: Image) {
        if (DebugMonitor.framesReceived.incrementAndGet() == 1L) DebugMonitor.log("first frame ${SystemClock.elapsedRealtime() - captureStartedAt} ms after capture start")
        PerfMonitor.onFrameReceived()
        val now = SystemClock.elapsedRealtime()
        DebugMonitor.lastFrameAt = now
        if (now - lastProcessAt < profile.config.visionIntervalMs) { DebugMonitor.framesThrottled.incrementAndGet(); session?.onThrottled(); return }
        val arena = ArenaGate.info
        if (arena == null) { DebugMonitor.skip("game screen not in foreground (MainActivity paused)"); return }
        val scaleX = img.width / arena.screenW.toFloat(); val scaleY = img.height / arena.screenH.toFloat()
        if (kotlin.math.abs(scaleX - scaleY) > 0.02f) { DebugMonitor.skip("size mismatch img=${img.width}x${img.height} screen=${arena.screenW}x${arena.screenH}"); return }
        lastProcessAt = now
        val r = arena.rect
        val plane = img.planes[0]
        val ok = SampledFrame.sample(plane.buffer, plane.rowStride, plane.pixelStride, img.width, img.height,
            (r.left * scaleX).toInt(), (r.top * scaleY).toInt(), (r.right * scaleX).toInt(), (r.bottom * scaleY).toInt(),
            profile.config.sampleStep, sampled)
        if (!ok) { DebugMonitor.skip("arena region empty"); return }
        val t0 = System.nanoTime()
        val result = vision.process(sampled)
        val procMs = (System.nanoTime() - t0) / 1_000_000f
        PerfMonitor.onFrameProcessed(procMs)
        session?.onProcessed(procMs)
        DebugMonitor.framesProcessed.incrementAndGet()
        DebugMonitor.avgBrightness = sampled.avgBrightness
        // ภาพ → จอจริง
        val inv = 1f / scaleX
        val objects = result.objects.map { it.copy(box = it.box.scaled(inv, inv)) }
        val best = objects.maxByOrNull { it.confidence }
        DebugMonitor.objectCount = objects.size
        DebugMonitor.bestConfidence = best?.confidence ?: 0f
        DebugMonitor.bestLabel = best?.label ?: "-"
        if (best != null) {
            DebugMonitor.screenX = best.cx; DebugMonitor.screenY = best.cy
            DebugMonitor.imageX = best.cx * scaleX; DebugMonitor.imageY = best.cy * scaleY
        } else {
            DebugMonitor.screenX = Float.NaN; DebugMonitor.screenY = Float.NaN
            DebugMonitor.imageX = Float.NaN; DebugMonitor.imageY = Float.NaN
            DebugMonitor.lastSkipReason = "nothing detected (brightness=${sampled.avgBrightness})"
        }
        OverlayState.publish(objects, now)
        val bounds = Box(r.left.toFloat(), r.top.toFloat(), r.right.toFloat(), r.bottom.toFloat())
        engine?.onObservation(Observation(++seq, now, objects, bounds))
    }

    private val ticker = object : Runnable {
        override fun run() {
            if (!running) return
            if (EmergencyStop.triggered) {
                engine?.emergencyStop()
                stopSelf()
                return
            }
            if (checkNoFrames()) return
            engine?.step()
            sampleFps()
            handler.postDelayed(this, 40)
        }
    }

    /**
     * ไม่มีภาพเข้ามาเลยหลังเริ่มจับภาพ 4 วินาที = บอทตาบอด (ไม่มีทางทำอะไรได้) → บันทึกข้อมูลวินิจฉัย แล้วหยุดพร้อมบอกว่าจับภาพล้มเหลว
     * ไม่ปล่อยให้หน้าจอขึ้น "บอททำงาน" ทั้งที่ไม่เห็นอะไร · คืน true ถ้าหยุดแล้ว
     */
    private fun checkNoFrames(): Boolean {
        if (noFrameReported || DebugMonitor.framesReceived.get() > 0L) return false
        val since = SystemClock.elapsedRealtime() - captureStartedAt
        if (since < 4000) return false
        noFrameReported = true
        DebugMonitor.error("no frames ${since} ms after capture start: imageCallbacks=$imageCallbacks nullImages=$nullImages reader=${reader?.width}x${reader?.height} surfaceValid=${reader?.surface?.isValid} displayState=${display?.display?.state}")
        DebugMonitor.captureState = "Error"
        engine?.release()
        stopSelf()
        return true
    }

    /** ทุก 1 วินาที เก็บ FPS ของ Vision ขณะบอททำงานจริง (ไว้หา min/max/เฉลี่ยหลังหยุด) */
    private fun sampleFps() {
        val s = session ?: return
        val now = SystemClock.elapsedRealtime()
        if (now - lastFpsAt < 1000) return
        val processed = s.processed
        s.sampleFps((processed - lastFpsCount) * 1000f / (now - lastFpsAt))
        lastFpsAt = now; lastFpsCount = processed
    }

    /** สรุปรอบนี้ลงสถิติสะสมของโปรไฟล์ + ไฟล์ sessions.log + แผง Debug (คงอยู่หลังหยุด) */
    private fun finishSession() {
        if (finished) return
        val s = session ?: return
        val eng = engine ?: return
        finished = true
        val now = SystemClock.elapsedRealtime()
        s.end(now)
        val stats = eng.statsCopy()
        StatsStore.addSession(this, profile.id, stats)
        val sg = profile.config.streakGoal
        val goalTxt = if (sg > 0) (if (stats.bestStreak >= sg) "PASS ($sg in a row)" else "NOT YET (best ${stats.bestStreak}/$sg)") else stats.goalLine()
        val streak = if (stats.bestStreak > 0) " | best streak ${stats.bestStreak}" else ""
        val pad = (if (ControlInputStats.used) " | ${ControlInputStats.line()}" else "") + (if (ControlGestureStats.total() > 0) " | gestures: ${ControlGestureStats.line()}" else "")
        val extra = DebugMonitor.engine?.extra?.takeIf { it.isNotEmpty() }?.let { " | $it" } ?: ""
        val line = "${profile.id}: ${stats.summary()} | fails: ${stats.failureLine()} | ${s.fpsLine(now)} | ${GroundTruth.line()}$streak$pad$extra | goal $goalTxt"
        DebugMonitor.lastSessionLine = line
        DebugMonitor.sessionProfile = profile.id
        DebugMonitor.sessionActions = stats
        StatsStore.appendSession(this, line)
        StatsStore.appendSession(this, "  frames: ${s.dropAnalysis()}")
        DebugMonitor.log("session finished: $line")
    }

    override fun onDestroy() {
        engine?.release() // ปล่อยนิ้วจอยที่ค้าง (ถ้ามี) ก่อนปิดบริการ
        finishSession()
        running = false
        BotState.set(false)
        if (DebugMonitor.captureState != "Error") DebugMonitor.captureState = "Idle"
        handler.removeCallbacksAndMessages(null)
        reader?.setOnImageAvailableListener(null, null)
        OverlayState.clear()
        val d = display; val r = reader; val p = projection; val ht = handlerThread
        // ปิดทรัพยากรจับภาพบน thread เดียวกับที่อ่านเฟรม กันชนกัน (เคยเกิด "buffer is inaccessible" ตอนหยุดบอท)
        handler.post {
            try { d?.release(); r?.close(); p?.stop() } catch (e: Exception) { DebugMonitor.log("capture shutdown: ${e.message}") }
            ht.quitSafely()
        }
        super.onDestroy()
    }
}
