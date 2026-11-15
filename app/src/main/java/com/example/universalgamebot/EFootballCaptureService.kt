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
    private val captureHealth=EFootballFrameWatchdog()
    private val entered:Boolean get()=captureHealth.enteredNs>0
    private var started=false;private var busy=false;private var id=0L
    private var width=0;private var height=0
    private var startNs=0L;private var lastSourceNs=0L
    @Volatile private var gate:EFootballRunGate?=null
    private val menuResume=EFootballMenuResume()
    @Volatile private var resumePending=false
    @Volatile private var resumeBarrierNs=0L
    private var rotation=0
    private val sample=SampledFrame()
    private val scenePixels=EFootballPixels()
    private val config=EFootballTrainingConfig(maxFrameAgeMs=500,allowPartialCoverage=true)
    private val watcher:(String?,String?)->Unit={ foreground,announced ->
        if(active)EFootballReadout.focus(id,"foreground=$foreground announced=$announced; ${BotAccessibilityService.instance?.efootballFocusDescription()}")
        if(active && (foreground!=EFootballTrainingSession.TARGET || announced!=EFootballTrainingSession.TARGET)) {
            if(gate?.armed==true)stopRun("target focus lost or unknown")
            else {captureHealth.leaveBeforePlay();menuResume.observe(null);resumeBarrierNs=System.nanoTime()}
        }
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
            startNs=System.nanoTime();gate=EFootballRunGate(startNs);active=true
            BotAccessibilityService.efootballScopeObserver=watcher
            reader=ImageReader.newInstance(width,height,PixelFormat.RGBA_8888,3)
            reader!!.setOnImageAvailableListener({source->
                val image=try{source.acquireLatestImage()}catch(e:Exception){stopRun("image acquire failed");null}
                if(image!=null)try {
                    if(!active || busy)return@setOnImageAvailableListener
                    val now=System.nanoTime();val pkg=BotAccessibilityService.instance?.efootballForeground()
                    EFootballReadout.focus(id,"foreground=$pkg; ${BotAccessibilityService.instance?.efootballFocusDescription()}")
                    if(!entered && pkg==EFootballTrainingSession.TARGET)captureHealth.enter(now)
                    if(!entered)return@setOnImageAvailableListener
                    if(pkg!=EFootballTrainingSession.TARGET){
                        gate?.check(pkg,now)?.let{stopRun(it)}
                        if(gate?.armed!=true)captureHealth.leaveBeforePlay()
                        return@setOnImageAvailableListener
                    }
                    if(image.width!=width || image.height!=height || (dimensions()!=(width to height) || rotation()!=rotation)){stopRun("capture geometry changed");return@setOnImageAvailableListener}
                    val timestamp=image.timestamp
                    if(timestamp<=lastSourceNs || timestamp<captureHealth.enteredNs || timestamp<=resumeBarrierNs || now-timestamp !in 0..500_000_000L)return@setOnImageAvailableListener
                    lastSourceNs=timestamp;captureHealth.fresh(now)
                    val plane=image.planes[0]
                    if(!SampledFrame.sample(plane.buffer,plane.rowStride,plane.pixelStride,width,height,0,0,width,height,1,sample))return@setOnImageAvailableListener
                    busy=true
                    ocr!!.read(sample,fullFrame=gate?.armed!=true && !scenePixels.trainingContext(sample).first){words->worker.post {
                        busy=false
                        if(!active)return@post
                        try {
                            if(BotAccessibilityService.instance?.efootballForeground()!=EFootballTrainingSession.TARGET){
                                if(gate?.armed==true)stopRun("target lost during OCR")
                                else {captureHealth.leaveBeforePlay();menuResume.observe(null);resumeBarrierNs=System.nanoTime()}
                                return@post
                            }
                            if(timestamp<=resumeBarrierNs)return@post
                            if(System.nanoTime()-timestamp>500_000_000L){EFootballReadout.note(id,"source too old after OCR");return@post}
                            val menu=if(gate?.armed!=true)EFootballTrainingMenu.read(words,width,height,timestamp,geometry()) else null
                            menuResume.observe(menu)
                            if(menu!=null){
                                EFootballReadout.menu(id,"training pause menu confirmed; startup Back only")
                                resumeMenu(menu)
                            }
                            val o=perception!!.read(sample,timestamp,words)
                            if(resumePending){
                                EFootballReadout.frame(id,o,(System.nanoTime()-timestamp)/1_000_000,perception!!.lastDiagnostic,controller)
                                return@post
                            }
                            gate!!.scene(o.frame.scene,timestamp)?.let{
                                EFootballReadout.frame(id,o,(System.nanoTime()-timestamp)/1_000_000,perception!!.lastDiagnostic,controller)
                                stopRun("$it: ${perception!!.lastDiagnostic}");return@post
                            }
                            if(o.frame.scene==EFootballScene.TRAINING_LIVE) {
                                if(controller==null)controller=EFootballTrainingController(EFootballTrainingSession(id,geometry(),gate!!.liveStartedNs,config),
                                    EFootballAndroidGestures(BotAccessibilityService.instance!!),{if(active && entered)BotAccessibilityService.instance?.efootballForeground() else null},{System.nanoTime()},countersChanged={a,b,c->EFootballReadout.tally(id,a,b,c)})
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
    private fun resumeMenu(menu:EFootballTrainingMenu) {
        if(resumePending || gate?.armed==true || !menuResume.ready(menu))return
        val service=BotAccessibilityService.instance ?: return
        resumePending=true
        var submitted=false
        val p=menu.back
        val plan=EFootballGesturePlan(listOf(EFootballPointerStroke(1,0,listOf(
            EFootballGestureKeyframe(0,p),EFootballGestureKeyframe(60,p)))))
        EFootballAndroidGestures(service).enqueue(plan,{submit->synchronized(this){
            val pkg=BotAccessibilityService.instance?.efootballForeground()
            val now=System.nanoTime()
            if(!active || gate?.armed==true || gate?.check(pkg,now)!=null || dimensions()!=(width to height) || rotation()!=rotation ||
                !menuResume.claim(menu,pkg,geometry(),System.nanoTime()))false
            else {submitted=true;EFootballReadout.menu(id,"startup Back submitted once");submit()}
        }},{ok->
            resumePending=false
            if(ok){resumeBarrierNs=System.nanoTime();EFootballReadout.menu(id,"startup Back completed; waiting for fresh field")}
            else if(submitted)stopRun("startup Back gesture failed; no retry")
        })
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
            gate?.check(BotAccessibilityService.instance?.efootballForeground(),now)!=null->stopRun(gate!!.check(BotAccessibilityService.instance?.efootballForeground(),now)!!)
            (dimensions()!=(width to height) || rotation()!=rotation)->stopRun("display geometry changed")
            captureHealth.expired(now,gate?.armed==true)->stopRun("no fresh capture frames")
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
