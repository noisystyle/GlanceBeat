package it.noxia.ambientplayer;

import android.content.Context;
import android.content.SharedPreferences;

/** One persisted master switch, shared by settings, sessions and the lock-screen surface. */
final class PlayerPreferences {
    static final String ENABLED = "player_enabled";
    static final String DRIVE_ENABLED = "drive_enabled";
    static final String DRIVE_ADDRESS = "drive_address";
    static final String DRIVE_NAME = "drive_name";
    static final String DRIVE_CHARGING = "drive_charging";
    static final String BT_PERMISSION_ASKED = "bt_permission_asked";
    static final String INTERNAL_REVERSED = "internal_columns_reversed";
    static boolean internalReversed(Context context) { return store(context).getBoolean(INTERNAL_REVERSED,false); }
    static boolean drivingKey(String key) { return key != null && key.startsWith("drive_"); }
    static boolean driveEnabled(Context context) { return store(context).getBoolean(DRIVE_ENABLED, false); }
    static boolean driveCharging(Context context) { return store(context).getBoolean(DRIVE_CHARGING, false); }
    static String driveAddress(Context context) { return store(context).getString(DRIVE_ADDRESS, ""); }
    static String driveName(Context context) { return store(context).getString(DRIVE_NAME, ""); }
    static void selectCar(Context context, String address, String name) {
        store(context).edit().putString(DRIVE_ADDRESS, address).putString(DRIVE_NAME, name).apply();
    }
    static SharedPreferences store(Context context) {
        return context.getSharedPreferences("player_settings", Context.MODE_PRIVATE);
    }
    static boolean isEnabled(Context context) { return store(context).getBoolean(ENABLED, false); }
    static void setEnabled(Context context, boolean enabled) {
        store(context).edit().putBoolean(ENABLED, enabled).apply();
    }
    private PlayerPreferences() { }
}
