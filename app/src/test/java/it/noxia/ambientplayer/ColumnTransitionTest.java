package it.noxia.ambientplayer;
import org.junit.Test;
import static org.junit.Assert.*;
public class ColumnTransitionTest {
    @Test public void queueWaitsForPlayerAndColumnsToSettle(){
        for(int i=0;i<=1000;i++){
            var frame=ColumnTransition.at(i/1000f,true);
            if(frame.queue()>0){assertEquals(1,frame.player(),0);assertEquals(1,frame.columns(),0);}
        }
    }
    @Test public void closingHidesQueueBeforePlayerCrossesItsColumn(){
        for(int i=0;i<=1000;i++){
            var frame=ColumnTransition.at(i/1000f,false);
            if(frame.columns()>0)assertEquals(1,frame.queue(),0);
        }
    }
    @Test public void openingStartsWithLightBeforeHorizontalTravel(){
        var frame=ColumnTransition.at(.1f,true);
        assertTrue(frame.player()>0);assertEquals(0,frame.columns(),0);assertEquals(0,frame.queue(),0);
    }
    @Test public void phaseBoundariesHaveNoDiscontinuity(){
        for(boolean opening:new boolean[]{true,false})for(float t:new float[]{.15f,.20f,.35f,.55f,.75f,.85f}){
            var before=ColumnTransition.at(t-.0001f,opening);var after=ColumnTransition.at(t+.0001f,opening);
            assertTrue(Math.abs(before.player()-after.player())<.002f);
            assertTrue(Math.abs(before.columns()-after.columns())<.002f);
            assertTrue(Math.abs(before.queue()-after.queue())<.002f);
        }
    }
    @Test public void endpointsAreExactAndClamped(){
        for(boolean opening:new boolean[]{true,false}){
            assertEquals(new ColumnTransition.Stages(0,0,0),ColumnTransition.at(-1,opening));
            assertEquals(new ColumnTransition.Stages(1,1,1),ColumnTransition.at(2,opening));
        }
    }
}
