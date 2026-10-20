package it.noxia.ambientplayer;

/** Keep this independent of Bluetooth callbacks and Android lifecycle races. */
final class DrivingModePolicy {
    static boolean active(boolean enabled, String selected, boolean permission, boolean bluetoothOn,
            boolean selectedConnected, boolean chargingOnly, boolean charging, boolean playing, boolean pocketed) {
        return enabled && selected != null && !selected.isBlank() && permission && bluetoothOn
            && selectedConnected && (!chargingOnly || charging) && playing && !pocketed;
    }
    private DrivingModePolicy() { }
}
