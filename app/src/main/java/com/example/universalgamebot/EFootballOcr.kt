package com.example.universalgamebot

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

/** Bundled Latin model. One image, two ROIs: scoreboard veto + current control labels. No files. */
internal class EFootballOcr:AutoCloseable {
    private val recognizer=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    fun read(f:SampledFrame,fullFrame:Boolean=false,done:(List<EFootballWord>)->Unit) {
        val left=(f.width*.70f).toInt();val top=(f.height*.35f).toInt()
        val controlW=f.width-left;val controlH=(f.height*.96f).toInt()-top
        val width=(f.width*.55f).toInt();val header=(f.height*.18f).toInt()
        val gap=12;val height=header+gap+controlH
        val p=IntArray(width*height){-1}
        fun gray(v:Int)=-0x1000000 or (v shl 16) or (v shl 8) or v
        for(y in 0 until header)for(x in 0 until width) {
            val c=f.rgb[y*f.width+x];val r=c shr 16 and 255;val g=c shr 8 and 255;val b=c and 255
            p[y*width+x]=gray(((r*77+g*150+b*29)/256).coerceIn(0,255))
        }
        for(y in 0 until controlH)for(x in 0 until controlW) {
            val c=f.rgb[(top+y)*f.width+left+x]
            val v=((minOf(c shr 16 and 255,c shr 8 and 255,c and 255)-40)*2).coerceIn(0,255)
            p[(header+gap+y)*width+x]=gray(v)
        }
        val bitmap=if(fullFrame){
            val colors=IntArray(f.width*f.height){i->-0x1000000 or f.rgb[i]}
            val original=Bitmap.createBitmap(colors,f.width,f.height,Bitmap.Config.ARGB_8888)
            val resized=if(f.width>1280)Bitmap.createScaledBitmap(original,1280,(f.height*1280f/f.width).toInt(),true) else original
            if(resized!==original)original.recycle()
            resized
        }else Bitmap.createBitmap(p,width,height,Bitmap.Config.ARGB_8888)
        val scale=if(fullFrame)f.width.toFloat()/bitmap.width else 1f
        try {
            recognizer.process(InputImage.fromBitmap(bitmap,0)).addOnSuccessListener { result ->
                val words=ArrayList<EFootballWord>()
                fun add(text:String,x:Float,y:Float) {
                    if(fullFrame)words+=EFootballWord(text,x*scale,y*scale)
                    else if(y<header)words+=EFootballWord(text,x,y)
                    else if(y>=header+gap)words+=EFootballWord(text,left+x,top+y-header-gap)
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
