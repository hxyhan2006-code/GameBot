package com.example.universalgamebot

/** Semantic six-tile Free Training pause scene; Back comes from this image's OCR location. */
data class EFootballTrainingMenu(val sourceNs:Long,val geometryId:Long,val back:EFootballScreenPoint) {
    companion object {
        fun read(words:List<EFootballWord>,w:Int,h:Int,sourceNs:Long,geometryId:Long,screenWidth:Int=w,screenHeight:Int=h):EFootballTrainingMenu? {
            fun locate(name:String)=words.singleOrNull{it.text.lowercase().filter(Char::isLetter)==name && it.x in 0f..w.toFloat() && it.y in 0f..h.toFloat()}
            val top=listOf("practicelist","commandlist","camera").map{locate(it) ?: return null}
            val bottom=listOf("playsettings","audio","quittraining").map{locate(it) ?: return null}
            if(top.zipWithNext().any{(a,b)->a.x>=b.x} || bottom.zipWithNext().any{(a,b)->a.x>=b.x})return null
            if(top.maxOf{it.y}-top.minOf{it.y}>h*.14f || bottom.maxOf{it.y}-bottom.minOf{it.y}>h*.14f ||
                bottom.minOf{it.y}-top.maxOf{it.y}<h*.10f || top.indices.any{kotlin.math.abs(top[it].x-bottom[it].x)>w*.10f})return null
            val back=locate("back") ?: return null
            if(back.x>w*.35f || back.y<bottom.maxOf{it.y}+h*.04f || back.y>h*.98f)return null
            return EFootballTrainingMenu(sourceNs,geometryId,EFootballScreenPoint(back.x*screenWidth/w,back.y*screenHeight/h))
        }
    }
}

/** Two fresh matching observations, consumed at dispatch, never a retry after submission. */
class EFootballMenuResume {
    private var last:EFootballTrainingMenu?=null
    private var stable=0
    private var consumed=false
    private var submittedNs=0L
    var resumed=0;private set
    @Synchronized fun observe(value:EFootballTrainingMenu?) {
        val old=last
        stable=if(value!=null && old!=null && value.sourceNs>old.sourceNs && value.geometryId==old.geometryId &&
            kotlin.math.hypot(value.back.x-old.back.x,value.back.y-old.back.y)<20f)stable+1 else if(value!=null)1 else 0
        last=value
    }
    @Synchronized fun ready(value:EFootballTrainingMenu)=!consumed && stable>=2 && last===value
    @Synchronized fun claim(value:EFootballTrainingMenu,pkg:String?,geometryId:Long,nowNs:Long):Boolean {
        if(!ready(value) || pkg!=EFootballTrainingSession.TARGET || value.geometryId!=geometryId ||
            nowNs-value.sourceNs !in 0..500_000_000L)return false
        consumed=true
        submittedNs=nowNs
        return true
    }
    /** Only a fresh, fully ready field (not a missing OCR result) opens the next menu episode. */
    @Synchronized fun fieldConfirmed(sourceNs:Long){
        if(consumed && sourceNs>submittedNs){resumed++;consumed=false;submittedNs=0;last=null;stable=0}
    }
    @Synchronized fun awaitingField()=consumed
}
