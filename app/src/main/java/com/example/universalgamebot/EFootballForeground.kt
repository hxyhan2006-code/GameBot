package com.example.universalgamebot

/** OS window metadata only. A cached package alone never authorizes input. */
data class EFootballWindow(val id:Int,val focused:Boolean,val active:Boolean,val application:Boolean,val rootPackage:String?,val accessibilityOverlay:Boolean=false)
class EFootballForeground {
    @Volatile private var targetWindowId=-1
    @Volatile var diagnostic="no foreground evidence";private set
    fun announced(pkg:String?,windowId:Int){targetWindowId=if(pkg==EFootballTrainingSession.TARGET && windowId>=0)windowId else -1}
    fun reset(){targetWindowId=-1;diagnostic="foreground evidence reset"}
    fun resolveWithOwnedOverlay(rootPackage:String?,rootWindowId:Int,windows:List<EFootballWindow>,overlayId:Int,ownerPackage:String):String? {
        val overlay=if(overlayId>=0)windows.singleOrNull{it.id==overlayId && it.accessibilityOverlay && !it.focused} else null
        if(overlay==null)return resolve(rootPackage,windows)
        val root=if(rootWindowId==overlay.id && rootPackage==ownerPackage)null else rootPackage
        return resolve(root,windows.filter{it.id!=overlay.id})
    }
    fun resolve(rootPackage:String?,windows:List<EFootballWindow>):String? {
        if(rootPackage!=null){diagnostic="active root=$rootPackage";return rootPackage}
        val focused=windows.filter{it.focused}.singleOrNull()
        if(focused==null || !focused.active || !focused.application){diagnostic="root unavailable; no unique active focused application";return null}
        focused.rootPackage?.let{diagnostic="focused window root=$it";return it}
        if(focused.id>=0 && focused.id==targetWindowId){diagnostic="root unavailable; current focused application matches OS target window id=${focused.id}";return EFootballTrainingSession.TARGET}
        diagnostic="root unavailable; focused window id=${focused.id} does not match target id=$targetWindowId"
        return null
    }
}
