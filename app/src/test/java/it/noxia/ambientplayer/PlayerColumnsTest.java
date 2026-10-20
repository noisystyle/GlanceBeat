package it.noxia.ambientplayer;
import org.junit.Test;
import static org.junit.Assert.*;
public class PlayerColumnsTest {
    @Test public void coverDisplayKeepsPagesEvenInLandscape(){
        assertFalse(PlayerColumns.resolve(1248,2.625f,475,false).split());
        assertFalse(PlayerColumns.resolve(1972,2.625f,475,false).split());
    }
    @Test public void internalDisplayDefaultsToQueueOnLeft(){
        var p=PlayerColumns.resolve(2448,2.625f,704,false);
        assertTrue(p.split());assertEquals(0,p.queueLeft());assertEquals(1224,p.playerLeft());
        assertEquals(2448,p.playerWidth()+p.queueWidth());
    }
    @Test public void reverseAndOddWidthStillFillWindowWithoutOverlap(){
        var p=PlayerColumns.resolve(1849,2.625f,704,true);
        assertTrue(p.split());assertEquals(0,p.playerLeft());assertEquals(p.playerWidth(),p.queueLeft());
        assertEquals(1849,p.playerWidth()+p.queueWidth());
    }
    @Test public void narrowWindowOnInnerDisplayKeepsPages(){
        assertFalse(PlayerColumns.resolve(1200,2.625f,704,true).split());
    }
}
