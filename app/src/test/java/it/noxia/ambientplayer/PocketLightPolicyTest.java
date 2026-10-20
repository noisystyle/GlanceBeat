package it.noxia.ambientplayer;
import org.junit.Test;
import static org.junit.Assert.*;
public class PocketLightPolicyTest {
 @Test public void startingInDarknessDoesNotMeanPocket() {
  var p=new PocketLightPolicy();p.add(0,100);assertFalse(p.candidate(1500));
 }
 @Test public void recentCoverageAndUncoverHaveDifferentThresholds() {
  var p=new PocketLightPolicy();p.add(35,100);p.add(0,200);assertTrue(p.candidate(1500));assertFalse(p.uncovered());
  p.add(12,1700);assertTrue(p.uncovered());
 }
 @Test public void oldBrightReadingCannotTriggerLaterNighttimeBlackout() {
  var p=new PocketLightPolicy();p.add(35,100);p.add(0,7000);assertFalse(p.candidate(8000));
 }
 @Test public void manualRevealStaysVisibleUntilLightReturns() {
  var p=new PocketLightPolicy();p.add(35,100);p.add(0,200);p.revealManually();assertFalse(p.candidate(300));
  p.add(35,400);p.add(0,500);assertTrue(p.candidate(600));
 }
 @Test public void verticalOrFaceDownCanCoverButFaceUpAndMotionCannot() {
  assertTrue(PocketLightPolicy.coveredOrientation(0,9.8f,0));
  assertTrue(PocketLightPolicy.coveredOrientation(0,0,-9.8f));
  assertFalse(PocketLightPolicy.coveredOrientation(0,0,9.8f));
  assertFalse(PocketLightPolicy.coveredOrientation(0,19.6f,0));
 }
}
