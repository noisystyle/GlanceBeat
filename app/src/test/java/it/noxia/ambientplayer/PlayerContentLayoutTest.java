package it.noxia.ambientplayer;
import org.junit.Test;
import static org.junit.Assert.*;
public class PlayerContentLayoutTest {
 @Test public void emptyAmbientCentersCoverAndArtistBounds(){float y=PlayerContentLayout.offset(0,0,0);assertEquals(-(-163+37)/2f,y,.001f);}
 @Test public void emptyActiveCentersCoverAndUnlockBounds(){assertEquals(-(-238+230)/2f,PlayerContentLayout.offset(1,0,0),.001f);}
 @Test public void visibleNotificationsKeepExistingPositions(){assertEquals(0,PlayerContentLayout.offset(0,1,0),.001f);assertEquals(0,PlayerContentLayout.offset(1,0,1),.001f);}
 @Test public void ambientSuppressionDoesNotReserveAnInvisibleRow(){assertEquals(63,PlayerContentLayout.offset(0,0,1),.001f);assertEquals(0,PlayerContentLayout.offset(1,0,1),.001f);}
 @Test public void transitionsInterpolateWithoutChangingDimensions(){assertEquals(31.5f,PlayerContentLayout.offset(0,.5f,1),.001f);assertEquals(33.5f,PlayerContentLayout.offset(.5f,0,0),.001f);}
 private void equalUsefulMargins(float active,int height,float scale,int top,int bottom,float upper,float lower){
  float center=height/2f+PlayerContentLayout.offsetPx(active,0,0,scale,top,bottom);
  assertEquals(center+upper*scale-top,height-bottom-(center+lower*scale),.001f);
 }
 @Test public void ambientCentersInsideUsefulAreaAcrossFoldDisplays(){
  equalUsefulMargins(0,1972,1972/650f,104,0,-163,37);
  equalUsefulMargins(0,2400,2.7f,88,0,-163,37);
  equalUsefulMargins(0,1848,2.8f,0,0,-163,37);
 }
 @Test public void activeCentersInsideUsefulAreaIncludingRotatedCutout(){
  equalUsefulMargins(1,1972,1972/650f,104,0,-238,230);
  equalUsefulMargins(1,2400,2.7f,88,0,-238,230);
  equalUsefulMargins(1,1200,1.8f,0,72,-238,230);
 }
 @Test public void visibleNotificationsPreserveExistingLayoutWithCutout(){
  assertEquals(0,PlayerContentLayout.offsetPx(0,1,1,3,104,0),.001f);
  assertEquals(0,PlayerContentLayout.offsetPx(1,1,1,3,104,0),.001f);
 }
 @Test public void notificationTransitionIncludesUsefulAreaShiftContinuously(){
  float empty=PlayerContentLayout.offsetPx(0,0,0,3,104,0);
  float halfway=PlayerContentLayout.offsetPx(0,.5f,1,3,104,0);
  assertEquals(empty/2,halfway,.001f);
  assertEquals(empty,PlayerContentLayout.offsetPx(0,0,1,3,104,0),.001f);
 }
}
