package it.noxia.ambientplayer;

import org.junit.Test;
import static org.junit.Assert.*;

public class AutoPlayerGateTest {
    @Test public void launchNeedsAllRequirementsAndDoesNotDuplicatePendingRequest() {
        AutoPlayerGate gate = new AutoPlayerGate();
        assertFalse(gate.request(true, false, true, true, false));
        assertFalse(gate.request(true, true, true, false, false));
        assertFalse(gate.request(true, true, false, true, false));
        assertTrue(gate.request(true, true, true, true, false));
        assertFalse(gate.request(true, true, true, true, false));
        gate.opened();
        assertFalse(gate.request(true, true, true, true, true));
    }
    @Test public void dismissSurvivesMetadataEventsAndRequestTimeout() {
        AutoPlayerGate gate = new AutoPlayerGate();
        gate.dismissed();
        gate.requestExpired();
        assertFalse(gate.request(true, true, true, true, false));
        gate.newPlayback();
        assertTrue(gate.request(true, true, true, true, false));
    }
    @Test public void manualScreenOffDoesNotImmediatelyWakePlayerAgain() {
        AutoPlayerGate gate = new AutoPlayerGate();
        gate.newLockCycle(true);
        assertFalse(gate.request(true, true, true, true, false));
        gate.userWokeScreen();
        assertTrue(gate.request(true, true, true, true, false));
    }
    @Test public void nextLockCycleRearmsAfterUnlockAndDoesNotLaunchUnlocked() {
        AutoPlayerGate gate = new AutoPlayerGate();
        gate.dismissed();
        gate.newLockCycle(false);
        assertFalse(gate.request(true, true, true, false, false));
        assertTrue(gate.request(true, true, true, true, false));
    }
    @Test public void boundedRetryCanRecoverAnExpiredRequestButCannotUndoDismissal() {
        AutoPlayerGate gate=new AutoPlayerGate();
        assertTrue(gate.canRetry());assertTrue(gate.request(true,true,true,true,false));
        gate.requestExpired();assertTrue(gate.request(true,true,true,true,false));
        gate.dismissed();assertFalse(gate.canRetry());gate.requestExpired();assertFalse(gate.request(true,true,true,true,false));
    }
    @Test public void disabledRemainsDisabledAfterWakeAndNewPlayback() {
        AutoPlayerGate gate = new AutoPlayerGate();
        gate.userWokeScreen();
        gate.newPlayback();
        assertFalse(gate.request(false, true, true, true, false));
    }
}
