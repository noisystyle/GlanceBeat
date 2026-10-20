package it.noxia.ambientplayer;

/** Center the visible music block; the system-style status row remains independent. */
final class PlayerContentLayout {
    private PlayerContentLayout() { }
    static float offsetPx(float active,float ambientNotifications,float activeNotifications,float scale,int safeTop,int safeBottom) {
        float presence=ambientNotifications+(activeNotifications-ambientNotifications)*active;
        // Center [safeTop, height-safeBottom], instead of the whole display.
        float safeCenterShift=(Math.max(0,safeTop)-Math.max(0,safeBottom))/2f;
        return offset(active,ambientNotifications,activeNotifications)*scale+safeCenterShift*(1-presence);
    }
    static float offset(float active, float ambientNotifications, float activeNotifications) {
        // AOD bounds: cover -163 to artist +37. Active: cover -238 to arrow +230.
        float ambient=63*(1-ambientNotifications),awake=4*(1-activeNotifications);
        return ambient+(awake-ambient)*active;
    }
}
