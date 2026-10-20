package it.noxia.ambientplayer;

import android.content.Context;
import android.service.notification.StatusBarNotification;
import android.service.notification.NotificationListenerService.RankingMap;

/** Public edition: no accessory observer, notification parsing, panel or hidden app icons. */
final class AccessorySupport {
    AccessorySupport(Context context, Runnable changed) { }
    void start() { }
    void stop() { }
    boolean listening() { return false; }
    boolean connected() { return false; }
    String name() { return ""; }
    static AccessoryBattery readBattery(StatusBarNotification[] notifications, RankingMap rankings) { return null; }
    static boolean hidesNotification(String packageName) { return false; }
    static AccessoryPanel createPanel(Context context, int accent, Runnable interact, Runnable close) { return null; }
}
