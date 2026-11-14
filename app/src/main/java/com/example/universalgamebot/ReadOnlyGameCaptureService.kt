package com.example.universalgamebot

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.view.WindowManager

/** Read-only pixel pipeline. No gesture executor, action command, game-state access or screenshot writer. */
class ReadOnlyGameCaptureService:Service() {
    private val thread=HandlerThread("ReadOnlyGame")
    private lateinit var worker:Handler
    private lateinit var session:ReadOnlyGameSession
    private var projection:MediaProjection?=null
    private var display:VirtualDisplay?=null
    private var reader:ImageReader?=null
    private val frame=SampledFrame()
    private val clock=CapturedFrameClock()
    private val vision:PixelGameReader=BrickVision()
    private val tracker=PixelMotionTracker()
    @Volatile private var active=false
    private var started=false
    private var runId=0L
    private var startedAt=0L; private var enteredNs=0L; private var lastFrame=0L; private var lastSample=0L
    private var width=0; private var height=0
    private val focusWatcher:(String?,String?)->Unit={ foreground,announced ->
        val pkg=if(foreground==ReadOnlyGameSession.TARGET)announced else foreground
        if(active && session.access(pkg,SystemClock.elapsedRealtime())==ReadOnlyGameSession.Access.STOP) {
            active=false; worker.post { stopRead("target focus lost or unknown") }
        }
    }
    override fun attachBaseContext(base:android.content.Context) { super.attachBaseContext(LocaleHelper.wrap(base)) }
    override fun onBind(intent:Intent?)=null
    override fun onCreate() { super.onCreate(); ErrorLog.init(this); thread.start(); worker=Handler(thread.looper) }
    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int {
        if(intent?.action=="STOP") { stopRead("stopped by user"); return START_NOT_STICKY }
        if(started)return START_NOT_STICKY
        started=true
        if(BotState.running || Board2048Readout.snapshot.running || BrickReadout.snapshot.running || BotAccessibilityService.instance==null) {
            fail("requires idle bot/readers and Accessibility"); return START_NOT_STICKY
        }
        val data:Intent?=if(Build.VERSION.SDK_INT>=33) intent?.getParcelableExtra("data",Intent::class.java) else {
            @Suppress("DEPRECATION") intent?.getParcelableExtra("data")
        }
        if(data==null || intent?.getIntExtra("code",0)!=Activity.RESULT_OK) { fail("capture permission missing"); return START_NOT_STICKY }
        runId=BrickReadout.begin()
        try {
            val nm=getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel("brick-reader",getString(R.string.brick_title),NotificationManager.IMPORTANCE_LOW))
            val stop=PendingIntent.getService(this,3301,Intent(this,ReadOnlyGameCaptureService::class.java).setAction("STOP"),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val open=PendingIntent.getActivity(this,3301,Intent(this,BrickReaderActivity::class.java),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val notice=Notification.Builder(this,"brick-reader").setContentTitle(getString(R.string.brick_title)).setContentText(getString(R.string.brick_notification))
                .setSmallIcon(android.R.drawable.ic_menu_view).setContentIntent(open).addAction(android.R.drawable.ic_delete,getString(R.string.notif_stop),stop).build()
            startForeground(3301,notice,ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
            projection=getSystemService(MediaProjectionManager::class.java).getMediaProjection(Activity.RESULT_OK,data)
            projection?.registerCallback(object:MediaProjection.Callback() { override fun onStop() { if(active) stopRead("capture stopped") } },worker)
            val size=dimensions(); width=size.first; height=size.second
            startedAt=SystemClock.elapsedRealtime(); session=ReadOnlyGameSession(startedAt)
            active=true
            BotAccessibilityService.readOnlyScopeObserver=focusWatcher
            reader=ImageReader.newInstance(width,height,PixelFormat.RGBA_8888,3)
            reader!!.setOnImageAvailableListener({ source ->
                val img=try { source.acquireLatestImage() } catch(e:Exception) { if(active)fail("acquire: ${e.message}"); null }
                if(img!=null) try {
                    if(active) {
                        val now=SystemClock.elapsedRealtime()
                        when(session.access(BotAccessibilityService.instance?.activePackage(),now)) {
                            ReadOnlyGameSession.Access.STOP -> stopRead("target focus lost / deadline")
                            ReadOnlyGameSession.Access.READ -> if(now-lastSample>=100) {
                                lastSample=now
                                if(enteredNs==0L)enteredNs=System.nanoTime()
                                if(img.width!=width || img.height!=height || !clock.accept(img.timestamp,System.nanoTime(),enteredNs)) return@setOnImageAvailableListener
                                lastFrame=now
                                val plane=img.planes[0]
                                if(SampledFrame.sample(plane.buffer,plane.rowStride,plane.pixelStride,img.width,img.height,0,0,img.width,img.height,3,frame)) {
                                    val observation=vision.read(frame,img.timestamp)
                                    val motion=tracker.observe(observation)
                                    if(active && session.access(BotAccessibilityService.instance?.activePackage(),SystemClock.elapsedRealtime())==ReadOnlyGameSession.Access.READ) BrickReadout.frame(observation,motion,now,runId)
                                    else if(active) stopRead("target lost while reading")
                                }
                            }
                            else -> {}
                        }
                    }
                } catch(e:Exception) { if(active)fail("read: ${e.message}") } finally { img.close() }
            },worker)
            display=projection?.createVirtualDisplay("BrickReadOnly",width,height,resources.displayMetrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,reader!!.surface,null,worker)
            if(display==null) { fail("virtual display missing"); return START_NOT_STICKY }
            worker.post(ticker)
        } catch(e:Exception) { fail("setup: ${e.javaClass.simpleName}: ${e.message}") }
        return START_NOT_STICKY
    }
    private fun dimensions():Pair<Int,Int> {
        val wm=getSystemService(WindowManager::class.java)
        if(Build.VERSION.SDK_INT>=30) return wm.maximumWindowMetrics.bounds.let { it.width() to it.height() }
        val metrics=android.util.DisplayMetrics()
        @Suppress("DEPRECATION") wm.defaultDisplay.getRealMetrics(metrics)
        return metrics.widthPixels to metrics.heightPixels
    }
    private val ticker=object:Runnable {
        override fun run() {
            if(!active)return
            val now=SystemClock.elapsedRealtime()
            if(BotState.running || Board2048Readout.snapshot.running || session.access(BotAccessibilityService.instance?.activePackage(),now)==ReadOnlyGameSession.Access.STOP) { stopRead("scope lost or deadline"); return }
            if(dimensions()!=(width to height)) { stopRead("display geometry changed"); return }
            if((BrickReadout.snapshot.last==null && now-startedAt>10_000) || (BrickReadout.snapshot.last!=null && lastFrame>0L && now-lastFrame>3000)) { fail("no fresh capture frames"); return }
            worker.postDelayed(this,100)
        }
    }
    private fun stopRead(reason:String) { active=false; BrickReadout.stop(reason,runId); stopSelf() }
    private fun fail(reason:String) { DebugMonitor.error("Brick reader: $reason"); stopRead("error: $reason") }
    override fun onDestroy() {
        active=false
        if(BotAccessibilityService.readOnlyScopeObserver===focusWatcher) BotAccessibilityService.readOnlyScopeObserver=null
        if(BrickReadout.snapshot.running) BrickReadout.stop("reader service stopped",runId)
        worker.removeCallbacksAndMessages(null)
        worker.post {
            try { display?.release(); reader?.setOnImageAvailableListener(null,null); reader?.close(); projection?.stop() }
            catch(e:Exception) { DebugMonitor.error("Brick reader cleanup: ${e.message}") }
            finally { thread.quitSafely() }
        }
        super.onDestroy()
    }
}
