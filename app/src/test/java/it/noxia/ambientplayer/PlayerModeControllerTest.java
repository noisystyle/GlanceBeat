package it.noxia.ambientplayer;

import org.junit.Test;
import static org.junit.Assert.*;

public class PlayerModeControllerTest {
    @Test public void secondDoubleTapDimsImmediatelyAndOldTimeoutDoesNotAffectNextActivation() {
        PlayerModeController modes = new PlayerModeController();
        modes.toggle(100);
        assertEquals(PlayerModeController.Mode.ACTIVE, modes.mode());
        modes.toggle(500);
        assertEquals(PlayerModeController.Mode.AMBIENT, modes.mode());
        modes.toggle(2_000);
        modes.expire(10_100);
        assertEquals(PlayerModeController.Mode.ACTIVE, modes.mode());
        modes.expire(12_000);
        assertEquals(PlayerModeController.Mode.AMBIENT, modes.mode());
    }
    @Test public void toggleCannotCancelAuthenticationOrReopenAfterUnlock() {
        PlayerModeController modes = new PlayerModeController();
        modes.beginAuthentication();
        modes.toggle(100);
        assertEquals(PlayerModeController.Mode.AUTHENTICATING, modes.mode());
        modes.unlocked();
        modes.toggle(200);
        assertEquals(PlayerModeController.Mode.CLOSED, modes.mode());
    }
    @Test public void singleTapWhileDimmedDoesNotActivateControls() {
        PlayerModeController modes = new PlayerModeController();
        modes.interact(100);
        assertEquals(PlayerModeController.Mode.AMBIENT, modes.mode());
    }
    @Test public void interactionExtendsTimeoutAndOldTimeoutCannotDimEarly() {
        PlayerModeController modes = new PlayerModeController();
        modes.activate(0);
        modes.interact(9_000);
        modes.expire(10_000);
        assertEquals(PlayerModeController.Mode.ACTIVE, modes.mode());
        modes.expire(19_000);
        assertEquals(PlayerModeController.Mode.AMBIENT, modes.mode());
    }
    @Test public void patternChallengeSurvivesOldTimerAndDoubleTap() {
        PlayerModeController modes = new PlayerModeController();
        modes.activate(0);
        assertTrue(modes.beginAuthentication());
        assertFalse(modes.beginAuthentication());
        modes.expire(20_000);
        modes.activate(20_000);
        assertEquals(PlayerModeController.Mode.AUTHENTICATING,modes.mode());
    }
    @Test public void cancelReturnsToAmbientWithoutClaimingUnlock() {
        PlayerModeController modes = new PlayerModeController();
        modes.beginAuthentication();
        modes.cancelAuthentication();
        assertEquals(PlayerModeController.Mode.AMBIENT,modes.mode());
    }
    @Test public void successCannotBeReopenedByLateCancelOrTimeout() {
        PlayerModeController modes = new PlayerModeController();
        modes.activate(0);
        modes.beginAuthentication();
        modes.unlocked();
        modes.cancelAuthentication();
        modes.expire(20_000);
        modes.activate(21_000);
        assertEquals(PlayerModeController.Mode.CLOSED,modes.mode());
    }
    @Test public void directFingerprintUnlockClosesAmbientPlayer() {
        PlayerModeController modes = new PlayerModeController();
        modes.unlocked();
        assertEquals(PlayerModeController.Mode.CLOSED,modes.mode());
        assertFalse(modes.beginAuthentication());
    }
}
