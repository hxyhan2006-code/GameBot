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

/** Dedicated pixel reader. Never uses ArenaGate, BotEngine, or gesture dispatch. */
class Board2048CaptureService:Service() {
    private val thread=HandlerThread("Read2048")
    private lateinit var worker:Handler
    private var projection:MediaProjection?=null
    private var display:VirtualDisplay?=null
    private var reader:ImageReader?=null
    private lateinit var session:Board2048Session
    private val detector=Board2048Reader()
    private val stable=Stable2048Board()
    private val frame=SampledFrame()
    private val frameClock=Board2048FrameClock()
    private var targetEnteredNs=0L
    @Volatile private var active=false
    private var startedAt=0L
    private var lastFrame=0L
    private var lastSample=0L

    override fun attachBaseContext(base:android.content.Context) { super.attachBaseContext(LocaleHelper.wrap(base)) }
    override fun onBind(intent:Intent?)=null
    override fun onCreate() {
        super.onCreate(); ErrorLog.init(this)
        thread.start(); worker=Handler(thread.looper)
    }
    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int {
        if(intent?.action=="STOP") { stopRead(); return START_NOT_STICKY }
        if(active) return START_NOT_STICKY
        if(BotState.running || BotAccessibilityService.instance==null) { fail("reader requires idle bot and Accessibility"); return START_NOT_STICKY }
        val data:Intent? = if(Build.VERSION.SDK_INT>=33) intent?.getParcelableExtra("data",Intent::class.java) else {
            @Suppress("DEPRECATION") intent?.getParcelableExtra("data")
        }
        if(data==null || intent?.getIntExtra("code",0)!=Activity.RESULT_OK) { fail("projection permission missing"); return START_NOT_STICKY }
        try {
            val nm=getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel("read2048",getString(R.string.read2048_title),NotificationManager.IMPORTANCE_LOW))
            val stop=PendingIntent.getService(this,2048,Intent(this,Board2048CaptureService::class.java).setAction("STOP"),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val open=PendingIntent.getActivity(this,2048,Intent(this,Board2048Activity::class.java),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val n=Notification.Builder(this,"read2048").setContentTitle(getString(R.string.read2048_title))
                .setContentText(getString(R.string.read2048_notification)).setSmallIcon(android.R.drawable.ic_menu_view)
                .setContentIntent(open).addAction(android.R.drawable.ic_delete,getString(R.string.notif_stop),stop).build()
            startForeground(2048,n,ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
            projection=getSystemService(MediaProjectionManager::class.java).getMediaProjection(Activity.RESULT_OK,data)
            projection?.registerCallback(object:MediaProjection.Callback() { override fun onStop() { if(active) stopRead() } },worker)
            val wm=getSystemService(WindowManager::class.java)
            val size=if(Build.VERSION.SDK_INT>=30) wm.maximumWindowMetrics.bounds.let { it.width() to it.height() } else {
                val m=android.util.DisplayMetrics()
                @Suppress("DEPRECATION") wm.defaultDisplay.getRealMetrics(m)
                m.widthPixels to m.heightPixels
            }
            startedAt=SystemClock.elapsedRealtime(); session=Board2048Session(startedAt)
            Board2048Readout.begin(); active=true
            reader=ImageReader.newInstance(size.first,size.second,PixelFormat.RGBA_8888,3)
            reader!!.setOnImageAvailableListener({ source ->
                val img=try { source.acquireLatestImage() } catch(e:Exception) { fail("acquire: ${e.message}"); null }
                if(img!=null) try {
                    if(active) {
                        val now=SystemClock.elapsedRealtime()
                        when(session.access(BotAccessibilityService.instance?.activePackage(),now)) {
                            Board2048Session.Access.STOP -> stopRead()
                            Board2048Session.Access.READ -> if(now-lastSample>=200) {
                                lastSample=now
                                if(targetEnteredNs==0L) targetEnteredNs=System.nanoTime()
                                val timestamp=img.timestamp
                                if(!frameClock.accept(timestamp,System.nanoTime(),targetEnteredNs)) {
                                    stable.invalidate()
                                    Board2048Readout.frame(null,now)
                                    return@setOnImageAvailableListener
                                }
                                lastFrame=now
                                val p=img.planes[0]
                                if(SampledFrame.sample(p.buffer,p.rowStride,p.pixelStride,img.width,img.height,0,0,img.width,img.height,4,frame)) {
                                    val result=stable.observe(detector.read(frame),timestamp/1_000_000)
                                    if(active && session.access(BotAccessibilityService.instance?.activePackage(),SystemClock.elapsedRealtime())==Board2048Session.Access.READ)
                                        Board2048Readout.frame(result,now)
                                    else stopRead()
                                }
                            }
                            else -> {}
                        }
                    }
                } catch(e:Exception) { fail("read: ${e.message}") } finally { img.close() }
            },worker)
            display=projection?.createVirtualDisplay("ReadOnly2048",size.first,size.second,resources.displayMetrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,reader!!.surface,null,worker)
            if(display==null) { fail("virtual display missing"); return START_NOT_STICKY }
            worker.post(ticker)
        } catch(e:Exception) { fail("setup: ${e.javaClass.simpleName}: ${e.message}") }
        return START_NOT_STICKY
    }
    private val ticker=object:Runnable {
        override fun run() {
            if(!active) return
            val now=SystemClock.elapsedRealtime()
            if(BotState.running || session.access(BotAccessibilityService.instance?.activePackage(),now)==Board2048Session.Access.STOP) { stopRead(); return }
            if((lastFrame==0L && now-startedAt>10_000) || (lastFrame>0L && now-lastFrame>3000)) { fail("no fresh capture frames"); return }
            worker.postDelayed(this,200)
        }
    }
    private fun stopRead() { active=false; Board2048Readout.stop(); stopSelf() }
    private fun fail(reason:String) { active=false; DebugMonitor.error("2048 reader: $reason"); Board2048Readout.fail(); stopSelf() }
    override fun onDestroy() {
        active=false
        if(Board2048Readout.snapshot.running) Board2048Readout.stop()
        worker.removeCallbacksAndMessages(null)
        worker.post {
            try { display?.release(); reader?.setOnImageAvailableListener(null,null); reader?.close(); projection?.stop() }
            catch(e:Exception) { DebugMonitor.error("2048 reader cleanup: ${e.message}") }
            finally { thread.quitSafely() }
        }
        super.onDestroy()
    }
}
