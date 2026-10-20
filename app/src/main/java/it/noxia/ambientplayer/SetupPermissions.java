package it.noxia.ambientplayer;

import android.content.Context;
import android.provider.Settings;

/** Required access is always checked against Android, never a saved completion flag. */
final class SetupPermissions {
    static boolean music(Context context){return MusicSessionService.accessGranted(context);}
    static boolean overlay(Context context){return Settings.canDrawOverlays(context);}
    static boolean ready(Context context){return music(context) && overlay(context);}
    private SetupPermissions() { }
}
