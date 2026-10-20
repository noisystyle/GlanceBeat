package it.noxia.musicfixture;
/** Records whether navigation used the session player or a track link, without playing anything. */
public class FixtureActivity extends android.app.Activity {
 private void receive(android.content.Intent intent){var prefs=getSharedPreferences("launch",0);prefs.edit().putInt("count",prefs.getInt("count",0)+1).putBoolean("session",intent.getBooleanExtra("fixture.player",false)).putString("uri",intent.getDataString()).apply();android.widget.TextView text=new android.widget.TextView(this);text.setText(intent.getBooleanExtra("fixture.player",false)?"Session player":"Track: "+intent.getDataString());setContentView(text);}
 @Override public void onCreate(android.os.Bundle state){super.onCreate(state);receive(getIntent());}
 @Override public void onNewIntent(android.content.Intent intent){super.onNewIntent(intent);setIntent(intent);receive(intent);}
}
