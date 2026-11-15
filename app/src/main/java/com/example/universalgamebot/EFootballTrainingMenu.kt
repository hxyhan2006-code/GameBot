package com.example.universalgamebot

/** Only this six-tile Free Training pause screen permits one startup Back press. */
data class EFootballTrainingMenu(val sourceNs:Long,val geometryId:Long,val back:EFootballScreenPoint) {
    companion object {
        fun read(words:List<EFootballWord>,w:Int,h:Int,sourceNs:Long,geometryId:Long):EFootballTrainingMenu? {
            fun locate(name:String,l:Float,t:Float,r:Float,b:Float):EFootballWord? = words.singleOrNull {
                it.text.lowercase().filter(Char::isLetter)==name && it.x in w*l..w*r && it.y in h*t..h*b
            }
            for((name,rect) in listOf(
                "practicelist" to floatArrayOf(.10f,.34f,.36f,.59f),
                "commandlist" to floatArrayOf(.37f,.34f,.63f,.59f),
                "camera" to floatArrayOf(.64f,.34f,.90f,.59f),
                "playsettings" to floatArrayOf(.10f,.59f,.36f,.85f),
                "audio" to floatArrayOf(.37f,.59f,.63f,.85f),
                "quittraining" to floatArrayOf(.64f,.59f,.90f,.85f))) {
                if(locate(name,rect[0],rect[1],rect[2],rect[3])==null)return null
            }
            val back=locate("back",.05f,.85f,.28f,.98f) ?: return null
            return EFootballTrainingMenu(sourceNs,geometryId,EFootballScreenPoint(back.x,back.y))
        }
    }
}

/** Two fresh matching observations, consumed at dispatch, never a retry after submission. */
class EFootballMenuResume {
    private var last:EFootballTrainingMenu?=null
    private var stable=0
    private var consumed=false
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
        return true
    }
}
