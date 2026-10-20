package it.noxia.ambientplayer.stabilityfixture;
/** Empty transparent activity: same focus interruption, no clipboard or playback access. */
public final class TransparentActivity extends android.app.Activity {
    @Override public void onCreate(android.os.Bundle state) {
        super.onCreate(state);
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(this::finish,2500);
    }
}
