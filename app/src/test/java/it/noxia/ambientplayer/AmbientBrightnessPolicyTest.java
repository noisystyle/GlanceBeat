package it.noxia.ambientplayer;
import org.junit.Test;
import static org.junit.Assert.*;
public class AmbientBrightnessPolicyTest {
 @Test public void darknessHasReadableFloorAndSunlightHasCap() {
  assertEquals(AmbientBrightnessPolicy.MIN,AmbientBrightnessPolicy.forLux(0),0.00001f);
  assertEquals(AmbientBrightnessPolicy.MAX,AmbientBrightnessPolicy.forLux(200000),0.00001f);
 }
 @Test public void brighterRoomsNeverMakeThePlayerDarker() {
  float previous=0;
  for(float lux:new float[]{0,1,5,20,50,100,300,800,1500,5000,10000,30000}) {
   float level=AmbientBrightnessPolicy.forLux(lux);assertTrue(level>=previous);previous=level;
  }
 }
 @Test public void invalidReadingsUseFallbackAndDoNotPolluteSamples() {
  var policy=new AmbientBrightnessPolicy();assertFalse(policy.add(Float.NaN));assertFalse(policy.add(-1));
  assertEquals(AmbientBrightnessPolicy.FALLBACK,policy.target(),0.00001f);
 }
 @Test public void briefBrightSpikeIsRejectedButSustainedLightWins() {
  var policy=new AmbientBrightnessPolicy();policy.add(40);policy.add(40);policy.add(40);policy.add(5000);policy.add(40);
  assertEquals(40,policy.medianLux(),0.001f);
  policy.add(5000);policy.add(5000);policy.add(5000);
  assertEquals(5000,policy.medianLux(),0.001f);
 }
 @Test public void SmallNoiseDoesNotTriggerBrightnessUpdates() {
  assertFalse(AmbientBrightnessPolicy.materiallyDifferent(0.06f,0.062f));
  assertTrue(AmbientBrightnessPolicy.materiallyDifferent(0.06f,0.10f));
 }
}
