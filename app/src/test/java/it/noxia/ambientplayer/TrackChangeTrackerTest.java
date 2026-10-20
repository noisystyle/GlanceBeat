package it.noxia.ambientplayer;
import org.junit.Test;
import static org.junit.Assert.*;
public class TrackChangeTrackerTest {
 @Test public void positionResetBeforeMetadataIsRecognized() {
  var t=new TrackChangeTracker();t.observe("old",240000,180000,10,false);
  assertTrue(t.observe("old",240000,0,20,false).reset());
  var change=t.observe("new",300000,300,20,false);
  assertTrue(change.identity());assertFalse(change.stalePosition());
 }
 @Test public void metadataBeforePlaybackDoesNotReuseOldPosition() {
  var t=new TrackChangeTracker();t.observe("old",240000,180000,10,false);
  assertTrue(t.observe("new",300000,180100,10,false).stalePosition());
  assertFalse(t.observe("new",300000,0,20,false).stalePosition());
 }
 @Test public void equalDurationTracksAndRepeatedTracksStillReset() {
  var t=new TrackChangeTracker();t.observe("a",240000,100000,10,false);
  assertTrue(t.observe("b",240000,0,20,false).identity());
  t.observe("b",240000,100000,20,false);
  assertTrue(t.observe("b",240000,0,30,false).reset());
 }
 @Test public void duplicateMetadataAndNormalProgressDoNotRestartAnimation() {
  var t=new TrackChangeTracker();t.observe("a",240000,100000,10,false);
  assertFalse(t.observe("a",240000,101000,10,false).any());
 }
 @Test public void lateDurationIsRecognizedSeparately() {
  var t=new TrackChangeTracker();t.observe("a",240000,100000,10,false);
  t.observe("b",240000,0,20,false);
  assertTrue(t.observe("b",300000,300,20,false).duration());
 }
 @Test public void manualSeekDoesNotLookLikeNewTrack() {
  var t=new TrackChangeTracker();t.observe("a",240000,180000,10,false);
  assertFalse(t.observe("a",240000,0,20,true).any());
  assertFalse(t.observe("a",240000,1000,20,false).any());
 }
}
