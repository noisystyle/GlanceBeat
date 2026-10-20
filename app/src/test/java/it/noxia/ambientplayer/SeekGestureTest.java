package it.noxia.ambientplayer;
import org.junit.Test;
import static org.junit.Assert.*;
public class SeekGestureTest {
 @Test public void quickTapDoesNotSeek() {
  var seek=new SeekGesture();assertTrue(seek.begin(50,100,240000));assertEquals(-1,seek.release());
 }
 @Test public void holdThenDragCommitsChosenPositionOnce() {
  var seek=new SeekGesture();seek.begin(25,100,240000);assertTrue(seek.activate());seek.move(75,100);
  assertEquals(180000,seek.release());assertEquals(-1,seek.release());
 }
 @Test public void cancelledHoldDoesNotCommit() {
  var seek=new SeekGesture();seek.begin(50,100,240000);seek.activate();seek.cancel();assertEquals(-1,seek.release());
 }
 @Test public void DragOutsideTrackIsClamped() {
  var seek=new SeekGesture();seek.begin(50,100,240000);seek.activate();seek.move(-10,100);assertEquals(0,seek.position());
  seek.move(150,100);assertEquals(240000,seek.release());
 }
 @Test public void missingDurationAndNewUnarmedPressCannotReusePreviousSeek() {
  var seek=new SeekGesture();assertFalse(seek.begin(50,100,0));assertFalse(seek.activate());
  seek.begin(50,100,240000);seek.activate();seek.begin(10,100,240000);assertEquals(-1,seek.release());
 }
}
