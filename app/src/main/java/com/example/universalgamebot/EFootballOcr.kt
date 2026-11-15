package com.example.universalgamebot

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

/** Bundled Latin model. Enlarged control-label tiles + header, or a startup menu. No files. */
internal class EFootballOcr:AutoCloseable {
    private val recognizer=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    var inputDescription="not sampled";private set
    fun read(f:SampledFrame,fullFrame:Boolean=false,done:(List<EFootballWord>)->Unit) {
        val atlas=if(fullFrame)null else EFootballOcrInput(f)
        val bitmap=if(fullFrame){
            val colors=IntArray(f.width*f.height){i->-0x1000000 or f.rgb[i]}
            val original=Bitmap.createBitmap(colors,f.width,f.height,Bitmap.Config.ARGB_8888)
            val resized=if(f.width>1280)Bitmap.createScaledBitmap(original,1280,(f.height*1280f/f.width).toInt(),true) else original
            if(resized!==original)original.recycle()
            resized
        }else Bitmap.createBitmap(atlas!!.rgb,atlas.width,atlas.height,Bitmap.Config.ARGB_8888)
        inputDescription="${if(fullFrame)"menu" else "control-tiles-2x"}:${bitmap.width}x${bitmap.height}"
        val scale=if(fullFrame)f.width.toFloat()/bitmap.width else 1f
        try {
            recognizer.process(InputImage.fromBitmap(bitmap,0)).addOnSuccessListener { result ->
                val words=ArrayList<EFootballWord>()
                fun add(text:String,x:Float,y:Float) {
                    if(fullFrame)words+=EFootballWord(text,x*scale,y*scale)
                    else atlas!!.word(text,x,y)?.let{words+=it}
                }
                for(line in result.textBlocks.flatMap{it.lines}) {
                    val normalized=line.text.lowercase().filter{it.isLetter()}
                    if(normalized in setOf("matchup","practicelist","commandlist","playsettings","quittraining"))line.boundingBox?.let{add(line.text,it.exactCenterX(),it.exactCenterY())}
                    else for(e in line.elements)e.boundingBox?.let{add(e.text,it.exactCenterX(),it.exactCenterY())}
                }
                bitmap.recycle();done(words)
            }.addOnFailureListener {bitmap.recycle();done(emptyList())}
        }catch(e:Exception){bitmap.recycle();done(emptyList())}
    }
    override fun close(){recognizer.close()}
}
