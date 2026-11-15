package com.example.universalgamebot

/** In-memory OCR atlas for the owner's Standard layout. Enlarges faint labels, not the full pitch. */
internal class EFootballOcrInput(f:SampledFrame) {
    data class Tile(val left:Int,val top:Int,val w:Int,val h:Int,val x:Int,val y:Int,val scale:Int)
    val width:Int
    val height:Int
    val rgb:IntArray
    val tiles:List<Tile>
    init {
        fun tile(l:Float,t:Float,r:Float,b:Float,x:Int,y:Int,scale:Int):Tile {
            val left=(f.width*l).toInt();val top=(f.height*t).toInt()
            return Tile(left,top,(f.width*r).toInt()-left,(f.height*b).toInt()-top,x,y,scale)
        }
        val header=tile(0f,0f,.55f,.18f,0,0,1)
        val gap=12;val row1=header.h+gap
        val through=tile(.70f,.54f,.86f,.69f,0,row1,2)
        val shot=tile(.86f,.46f,.99f,.62f,through.w*2+gap,row1,2)
        val row2=row1+maxOf(through.h,shot.h)*2+gap
        val pass=tile(.70f,.75f,.85f,.91f,0,row2,2)
        val dash=tile(.85f,.70f,.99f,.94f,pass.w*2+gap,row2,2)
        tiles=listOf(header,through,shot,pass,dash)
        width=tiles.maxOf{it.x+it.w*it.scale};height=tiles.maxOf{it.y+it.h*it.scale}
        rgb=IntArray(width*height){-1}
        for(tile in tiles){
            val stride=tile.w+1;val sum=IntArray(stride*(tile.h+1))
            fun channel(x:Int,y:Int):Int {
                val c=f.rgb[(tile.top+y)*f.width+tile.left+x]
                val r=c shr 16 and 255;val g=c shr 8 and 255;val b=c and 255
                return if(tile.scale==1)(r*77+g*150+b*29)/256 else minOf(r,g,b)
            }
            if(tile.scale>1)for(y in 0 until tile.h)for(x in 0 until tile.w)
                sum[(y+1)*stride+x+1]=channel(x,y)+sum[y*stride+x+1]+sum[(y+1)*stride+x]-sum[y*stride+x]
            for(y in 0 until tile.h*tile.scale)for(x in 0 until tile.w*tile.scale){
                val sx=x/tile.scale;val sy=y/tile.scale;val c=channel(sx,sy)
                val v=if(tile.scale==1)c else {
                    val l=(sx-8).coerceAtLeast(0);val r=(sx+9).coerceAtMost(tile.w)
                    val t=(sy-8).coerceAtLeast(0);val b=(sy+9).coerceAtMost(tile.h)
                    val average=(sum[b*stride+r]-sum[t*stride+r]-sum[b*stride+l]+sum[t*stride+l])/((r-l)*(b-t))
                    // Local contrast keeps both faint and opaque white glyphs distinct from
                    // their button/grass background; absolute clipping would erase opaque labels.
                    (200-(c-average)*5).coerceIn(0,255)
                }
                rgb[(tile.y+y)*width+tile.x+x]=-0x1000000 or (v shl 16) or (v shl 8) or v
            }
        }
    }
    fun word(text:String,x:Float,y:Float):EFootballWord? {
        val tile=tiles.singleOrNull{x>=it.x && y>=it.y && x<it.x+it.w*it.scale && y<it.y+it.h*it.scale} ?: return null
        return EFootballWord(text,tile.left+(x-tile.x)/tile.scale,tile.top+(y-tile.y)/tile.scale)
    }
}
