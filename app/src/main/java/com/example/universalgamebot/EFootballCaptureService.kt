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

/** Fixed target, foreground + pixel training gate, local OCR, one image and command at a time. */
class EFootballCaptureService:Service() {
    private val thread=HandlerThread("EFootballVision")
    private lateinit var worker:Handler
    private var projection:MediaProjection?=null;private var display:VirtualDisplay?=null;private var reader:ImageReader?=null
    private var ocr:EFootballOcr?=null
    private var perception:EFootballPerception?=null
    @Volatile private var controller:EFootballTrainingController?=null
    @Volatile private var active=false
    @Volatile private var entered=false
    private var started=false;private var busy=false;private var id=0L
    private var width=0;private var height=0
    private var startNs=0L;private var enteredNs=0L;private var lastSourceNs=0L;private var lastArrivalNs=0L
    private var seenTraining=false
    private var rotation=0
    private val sample=SampledFrame()
    private val config=EFootballTrainingConfig(maxFrameAgeMs=500,allowPartialCoverage=true)
    private val watcher:(String?,String?)->Unit={ foreground,announced ->
        if(active && entered && (foreground!=EFootballTrainingSession.TARGET || announced!=EFootballTrainingSession.TARGET)) stopRun("target focus lost or unknown")
    }
    override fun attachBaseContext(base:android.content.Context) {super.attachBaseContext(LocaleHelper.wrap(base))}
    override fun onBind(intent:Intent?)=null
    override fun onCreate(){super.onCreate();ErrorLog.init(this);thread.start();worker=Handler(thread.looper)}
    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int {
        if(intent?.action=="STOP"){stopRun("stopped by user");return START_NOT_STICKY}
        if(started)return START_NOT_STICKY
        started=true
        if(BotState.running || Board2048Readout.snapshot.running || BrickReadout.snapshot.running || EFootballReadout.snapshot.running || BotAccessibilityService.instance==null){stopSelf();return START_NOT_STICKY}
        val data:Intent?=if(Build.VERSION.SDK_INT>=33)intent?.getParcelableExtra("data",Intent::class.java) else {
            @Suppress("DEPRECATION") intent?.getParcelableExtra("data")}
        if(data==null || intent?.getIntExtra("code",0)!=Activity.RESULT_OK){stopSelf();return START_NOT_STICKY}
        id=EFootballReadout.begin()
        try {
            val nm=getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel("efootball-training",getString(R.string.ef_title),NotificationManager.IMPORTANCE_LOW))
            val stop=PendingIntent.getService(this,3401,Intent(this,EFootballCaptureService::class.java).setAction("STOP"),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val open=PendingIntent.getActivity(this,3401,Intent(this,EFootballTrainingActivity::class.java),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            startForeground(3401,Notification.Builder(this,"efootball-training").setContentTitle(getString(R.string.ef_title))
                .setContentText(getString(R.string.ef_notification)).setSmallIcon(android.R.drawable.ic_media_play).setContentIntent(open)
                .addAction(android.R.drawable.ic_delete,getString(R.string.notif_stop),stop).build(),ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
            val size=dimensions();width=size.first;height=size.second;rotation=rotation()
            if(width<=height){stopRun("landscape required");return START_NOT_STICKY}
            val direction=if(intent.getBooleanExtra("left",false))EFootballDirection.LEFT else EFootballDirection.RIGHT
            perception=EFootballPerception(id,geometry(),direction,intent.getBooleanExtra("ownRed",false),config)
            ocr=EFootballOcr()
            projection=getSystemService(MediaProjectionManager::class.java).getMediaProjection(Activity.RESULT_OK,data)
            projection!!.registerCallback(object:MediaProjection.Callback(){override fun onStop(){stopRun("capture stopped")}},worker)
            active=true;startNs=System.nanoTime();lastArrivalNs=startNs
            BotAccessibilityService.efootballScopeObserver=watcher
            reader=ImageReader.newInstance(width,height,PixelFormat.RGBA_8888,3)
            reader!!.setOnImageAvailableListener({source->
                val image=try{source.acquireLatestImage()}catch(e:Exception){stopRun("image acquire failed");null}
                if(image!=null)try {
                    if(!active || busy)return@setOnImageAvailableListener
                    val now=System.nanoTime();val pkg=BotAccessibilityService.instance?.activePackage()
                    if(!entered && pkg==EFootballTrainingSession.TARGET){entered=true;enteredNs=now}
                    if(!entered)return@setOnImageAvailableListener
                    if(pkg!=EFootballTrainingSession.TARGET){stopRun("target focus lost or unknown");return@setOnImageAvailableListener}
                    if(image.width!=width || image.height!=height || (dimensions()!=(width to height) || rotation()!=rotation)){stopRun("capture geometry changed");return@setOnImageAvailableListener}
                    val timestamp=image.timestamp
                    if(timestamp<=lastSourceNs || timestamp<enteredNs || now-timestamp !in 0..500_000_000L)return@setOnImageAvailableListener
                    lastSourceNs=timestamp;lastArrivalNs=now
                    val plane=image.planes[0]
                    if(!SampledFrame.sample(plane.buffer,plane.rowStride,plane.pixelStride,width,height,0,0,width,height,1,sample))return@setOnImageAvailableListener
                    busy=true
                    ocr!!.read(sample){words->worker.post {
                        busy=false
                        if(!active)return@post
                        try {
                            if(BotAccessibilityService.instance?.activePackage()!=EFootballTrainingSession.TARGET){stopRun("target lost during OCR");return@post}
                            if(System.nanoTime()-timestamp>500_000_000L){EFootballReadout.note(id,"source too old after OCR");return@post}
                            val o=perception!!.read(sample,timestamp,words)
                            if(seenTraining && o.frame.scene!=EFootballScene.TRAINING_LIVE){stopRun("training view lost; no automatic resume");return@post}
                            if(o.frame.scene==EFootballScene.TRAINING_LIVE) {
                                seenTraining=true
                                if(controller==null)controller=EFootballTrainingController(EFootballTrainingSession(id,geometry(),enteredNs,config),
                                    EFootballAndroidGestures(BotAccessibilityService.instance!!),{if(active && entered)BotAccessibilityService.instance?.activePackage() else null},{System.nanoTime()},countersChanged={a,b,c->EFootballReadout.tally(id,a,b,c)})
                                if(!active){controller!!.stop("run already stopped");return@post}
                                controller!!.observe(o)
                            }
                            EFootballReadout.frame(id,o,(System.nanoTime()-timestamp)/1_000_000,perception!!.lastDiagnostic,controller)
                            if(controller?.isStopped()==true)stopRun(controller!!.lastReason)
                        }catch(e:Exception){fail(e)}
                    }}
                }catch(e:Exception){fail(e)}finally{image.close()}
            },worker)
            display=projection!!.createVirtualDisplay("EFootballTraining",width,height,resources.displayMetrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,reader!!.surface,null,worker)
            worker.post(tick)
        }catch(e:Exception){fail(e)}
        return START_NOT_STICKY
    }
    @Suppress("DEPRECATION") private fun rotation()=getSystemService(WindowManager::class.java).defaultDisplay.rotation
    private fun geometry()=width.toLong()*100_000+height
    private fun dimensions():Pair<Int,Int> {
        val wm=getSystemService(WindowManager::class.java)
        if(Build.VERSION.SDK_INT>=30)return wm.maximumWindowMetrics.bounds.let{it.width() to it.height()}
        val m=android.util.DisplayMetrics();@Suppress("DEPRECATION") wm.defaultDisplay.getRealMetrics(m)
        return m.widthPixels to m.heightPixels
    }
    private val tick=object:Runnable{override fun run(){
        if(!active)return
        val now=System.nanoTime()
        when {
            !entered && now-startNs>20_000_000_000L->stopRun("target not entered within 20 seconds")
            entered && BotAccessibilityService.instance?.activePackage()!=EFootballTrainingSession.TARGET->stopRun("target focus lost or unknown")
            (dimensions()!=(width to height) || rotation()!=rotation)->stopRun("display geometry changed")
            entered && now-enteredNs>=120_000_000_000L->stopRun("two minute training limit")
            entered && now-lastArrivalNs>3_000_000_000L->stopRun("no fresh capture frames")
            entered && !seenTraining && now-enteredNs>15_000_000_000L->stopRun("training field could not be established")
            else->{controller?.tick();if(controller?.isStopped()==true)stopRun(controller!!.lastReason)}
        }
        if(active)worker.postDelayed(this,50)
    }}
    @Synchronized private fun stopRun(reason:String){active=false;controller?.stop(reason);if(id>0)EFootballReadout.stop(id,reason,controller);stopSelf()}
    private fun fail(e:Exception){DebugMonitor.error("eFootball: ${e.javaClass.simpleName}: ${e.message}");stopRun("error: ${e.javaClass.simpleName}: ${e.message}")}
    override fun onDestroy(){
        active=false;controller?.stop("service destroyed")
        if(BotAccessibilityService.efootballScopeObserver===watcher)BotAccessibilityService.efootballScopeObserver=null
        if(id>0)EFootballReadout.stop(id,"service stopped",controller)
        worker.removeCallbacksAndMessages(null)
        worker.post {try{display?.release();reader?.setOnImageAvailableListener(null,null);reader?.close();projection?.stop();ocr?.close()}
            catch(e:Exception){DebugMonitor.error("eFootball cleanup: ${e.message}")}finally{thread.quitSafely()}}
        super.onDestroy()
    }
}
