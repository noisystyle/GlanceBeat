package app.morphe.android.apps.youtube.music;
/** Emulator-only launcher target: opening it never changes playback. */
public final class FixtureActivity extends android.app.Activity {
 @Override public void onCreate(android.os.Bundle saved){super.onCreate(saved);android.widget.TextView text=new android.widget.TextView(this);text.setText("Silent music fixture opened");setContentView(text);}
}
