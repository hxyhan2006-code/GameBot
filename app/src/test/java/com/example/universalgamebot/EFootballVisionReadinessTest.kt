package com.example.universalgamebot

import org.junit.Assert.*
import org.junit.Test

class EFootballVisionReadinessTest {
    @Test fun phoneClockNeverCountsAsAMatchAndStillBlocksInput() {
        for(text in listOf("17:57","9:05","0:34")) {
            val e=EFootballHeader.read(listOf(EFootballWord(text,60f,30f),EFootballWord("40",1200f,30f)),1280,591)
            assertFalse(text,e.match);assertTrue(text,e.obstructed)
        }
        val gate=EFootballRunGate(1_000_000_000L)
        assertNull(gate.scene(EFootballScene.UNKNOWN,1_100_000_000L,false))
        assertFalse(gate.armed)
        gate.scene(EFootballScene.TRAINING_LIVE,1_200_000_000L,true)
        gate.scene(EFootballScene.TRAINING_LIVE,1_400_000_000L,true)
        assertTrue(gate.armed)
        assertNull(gate.scene(EFootballScene.UNKNOWN,1_500_000_000L,false))
        assertFalse(gate.playing)
        assertNotNull(gate.scene(EFootballScene.TRAINING_LIVE,1_600_000_000L,true))
    }
    @Test fun gameClockNeedsActualScoreTokensOnTheSameHeaderRow() {
        val clock=EFootballWord("44:52",280f,30f)
        val scores=listOf(EFootballWord("0",135f,30f),EFootballWord("1",170f,30f))
        assertTrue(EFootballHeader.read(scores+clock,1280,591).match)
        assertTrue(EFootballHeader.read(listOf(EFootballWord("0-1",140f,30f),clock),1280,591).match)
        assertFalse(EFootballHeader.read(scores.map{it.copy(y=90f)}+clock,1280,591).match)
        assertFalse(EFootballHeader.read(scores.map{it.copy(x=500f)}+clock,1280,591).match)
        assertFalse(EFootballHeader.read(listOf(EFootballWord("17:57",30f,15f),EFootballWord("99",1100f,15f)),1280,591).match)
        for(text in listOf("0-1","0:1")) {
            val scoreOnly=EFootballHeader.read(listOf(EFootballWord(text,140f,30f)),1280,591)
            assertFalse(scoreOnly.match);assertTrue("Score-only OCR still prohibits input",scoreOnly.obstructed)
        }
    }
    @Test fun observedDashFragmentRequiresItsCornerAndThreeOtherCurrentButtons() {
        val words=listOf(EFootballWord("4Through",1013f,354f),EFootballWord("Pass",980f,487f),
            EFootballWord("Clear",1145f,322f),EFootballWord("Da",1135f,476f))
        val hud=EFootballHud.read(words,1280,591)!!
        assertTrue(hud.attack);assertFalse(hud.canShoot)
        assertEquals(1135f,hud.buttons.getValue("dash").x,0f)
        for(i in words.indices)assertNull(EFootballHud.read(words.filterIndexed{j,_->i!=j},1280,591))
        assertNull(EFootballHud.read(words.map{if(it.text=="Da")it.copy(x=950f)else it},1280,591))
        assertNull(EFootballHud.read(words.map{if(it.text=="Da")it.copy(text="D")else it},1280,591))
        assertNull(EFootballHud.read(words+words.last(),1280,591))
    }
    @Test fun enlargedOcrTilesReturnEachLabelToItsSourceCoordinatesAndRejectGaps() {
        val f=SampledFrame.fromRgb(1440,665,IntArray(1440*665){0x306014},1)
        val atlas=EFootballOcrInput(f)
        val positions=listOf(.79f to .60f,.895f to .545f,.765f to .824f,.886f to .805f)
        for((nx,ny) in positions) {
            val x=nx*f.width;val y=ny*f.height
            val tile=atlas.tiles.single{it.scale==2 && x>=it.left && x<it.left+it.w && y>=it.top && y<it.top+it.h}
            val word=atlas.word("label",tile.x+(x-tile.left)*2,tile.y+(y-tile.top)*2)!!
            assertEquals(x,word.x,.01f);assertEquals(y,word.y,.01f)
        }
        assertEquals(EFootballWord("17:57",40f,20f),atlas.word("17:57",40f,20f))
        assertNull(atlas.word("gap",0f,atlas.tiles.first().h+5f))
        assertNull(atlas.word("outside",atlas.width.toFloat(),atlas.height.toFloat()))
        assertTrue("Tile atlas should stay below 0.6 megapixels",atlas.rgb.size<600_000)
    }
    @Test fun faintWhiteLabelsOnGrassGainContrastAndAreEnlargedWithoutMovingCoordinates() {
        val f=SampledFrame.fromRgb(1440,665,IntArray(1440*665){0x306014},1)
        val x=(1440*.89f).toInt();val y=(665*.80f).toInt()
        // Synthetic 20%-white stroke on grass, not a claim about the ML Kit engine.
        f.rgb[y*1440+x]=0x59803f
        val atlas=EFootballOcrInput(f);val tile=atlas.tiles.last()
        val at=(tile.y+(y-tile.top)*2)*atlas.width+tile.x+(x-tile.left)*2
        assertEquals(atlas.rgb[at],atlas.rgb[at+1]);assertEquals(atlas.rgb[at],atlas.rgb[at+atlas.width])
        assertTrue((atlas.rgb[at-2] and 255)-(atlas.rgb[at] and 255)>150)
        // Absolute gain used to clip BOTH the opaque button and its white text to black.
        java.util.Arrays.fill(f.rgb,0x83b883)
        f.rgb[y*1440+x]=0xffffff
        val opaque=EFootballOcrInput(f)
        assertTrue("Opaque labels must survive as well as faint labels",(opaque.rgb[at-2] and 255)-(opaque.rgb[at] and 255)>150)
    }
    @Test fun badgeReadsTheFieldWhileReadinessIsIncompleteInsteadOfWaitingForAFieldAlreadySeen() {
        val s=EFootballReadout.Snapshot(running=true,armed=false,status="training")
        assertEquals(EFootballBadgeState.READING,EFootballBadgeModel.state(s,10))
        assertEquals(EFootballBadgeState.WAITING,EFootballBadgeModel.state(s.copy(status="uncertain"),10))
        assertEquals(EFootballBadgeState.STOPPED,EFootballBadgeModel.state(s.copy(running=false),10))
    }
}
