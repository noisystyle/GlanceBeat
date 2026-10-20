package it.noxia.ambientplayer;

import org.junit.Test;
import static org.junit.Assert.*;

public class DrivingModePolicyTest {
    private boolean active(boolean enabled, String selected, boolean permission, boolean on,
            boolean connected, boolean chargingOnly, boolean charging, boolean playing, boolean pocket) {
        return DrivingModePolicy.active(enabled, selected, permission, on, connected, chargingOnly, charging, playing, pocket);
    }
    @Test public void onlyChosenConnectedDeviceAndRealPlaybackActivateDriving() {
        assertTrue(active(true,"car",true,true,true,false,false,true,false));
        assertFalse(active(false,"car",true,true,true,false,false,true,false));
        assertFalse(active(true,"",true,true,true,false,false,true,false));
        assertFalse(active(true,null,true,true,true,false,false,true,false));
        assertFalse(active(true,"car",true,true,false,false,false,true,false));
        assertFalse(active(true,"car",true,true,true,false,false,false,false));
    }
    @Test public void permissionBluetoothAndPocketAlwaysTakePrecedence() {
        assertFalse(active(true,"car",false,true,true,false,true,true,false));
        assertFalse(active(true,"car",true,false,true,false,true,true,false));
        assertFalse(active(true,"car",true,true,true,false,true,true,true));
    }
    @Test public void chargingRestrictionIsOptionalAndRespondsToUnplugging() {
        assertFalse(active(true,"car",true,true,true,true,false,true,false));
        assertTrue(active(true,"car",true,true,true,true,true,true,false));
        assertTrue(active(true,"car",true,true,true,false,false,true,false));
    }
    @Test public void drivingSurvivesNormalTimeoutWithoutAnIdleTimer() {
        PlayerModeController m=new PlayerModeController();m.setDriving(true,100);
        assertEquals(PlayerModeController.Mode.ACTIVE,m.mode());assertFalse(m.hasActiveTimeout());
        m.expire(10_000_000);assertEquals(PlayerModeController.Mode.ACTIVE,m.mode());
    }
    @Test public void doubleTapDimsAndRepeatedBluetoothUpdatesCannotReilluminate() {
        PlayerModeController m=new PlayerModeController();m.setDriving(true,100);m.toggle(200);
        m.setDriving(true,300);m.expire(10_000_000);
        assertEquals(PlayerModeController.Mode.AMBIENT,m.mode());assertFalse(m.hasActiveTimeout());
        m.toggle(10_000_001);m.expire(20_000_000);
        assertEquals(PlayerModeController.Mode.ACTIVE,m.mode());assertFalse(m.hasActiveTimeout());
    }
    @Test public void disconnectRestoresFreshNormalTimeout() {
        PlayerModeController m=new PlayerModeController();m.setDriving(true,0);m.setDriving(false,100_000);
        assertTrue(m.hasActiveTimeout());m.expire(109_999);assertEquals(PlayerModeController.Mode.ACTIVE,m.mode());
        m.expire(110_000);assertEquals(PlayerModeController.Mode.AMBIENT,m.mode());
    }
    @Test public void disconnectDoesNotWakeManuallyDimmedScreen() {
        PlayerModeController m=new PlayerModeController();m.setDriving(true,0);m.toggle(100);m.setDriving(false,200);
        assertEquals(PlayerModeController.Mode.AMBIENT,m.mode());
        m.setDriving(true,300);assertEquals(PlayerModeController.Mode.ACTIVE,m.mode());
    }
    @Test public void connectionCannotCoverNativeAuthentication() {
        PlayerModeController m=new PlayerModeController();m.beginAuthentication();m.setDriving(true,100);
        m.expire(100_000);assertEquals(PlayerModeController.Mode.AUTHENTICATING,m.mode());
        m.cancelAuthentication();assertEquals(PlayerModeController.Mode.ACTIVE,m.mode());assertFalse(m.hasActiveTimeout());
    }
    @Test public void disconnectedAuthenticationReturnsToAmbient() {
        PlayerModeController m=new PlayerModeController();m.setDriving(true,0);m.beginAuthentication();m.setDriving(false,100);
        m.cancelAuthentication();assertEquals(PlayerModeController.Mode.AMBIENT,m.mode());
    }
    @Test public void successfulUnlockCannotBeReopenedByBluetoothOrLateCancel() {
        PlayerModeController m=new PlayerModeController();m.setDriving(true,0);m.beginAuthentication();m.unlocked();
        m.setDriving(false,100);m.setDriving(true,200);m.cancelAuthentication();m.toggle(300);
        assertEquals(PlayerModeController.Mode.CLOSED,m.mode());
    }
}
