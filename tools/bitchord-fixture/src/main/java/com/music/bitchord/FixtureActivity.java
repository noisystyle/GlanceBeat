package com.music.bitchord;
public class FixtureActivity extends android.app.Activity {
 private void receive(android.content.Intent intent){boolean full=intent.getBooleanExtra("bitchord.openPlayer",false);if(full)FavoriteFixture.fullPlayerLaunches++;android.widget.TextView text=new android.widget.TextView(this);text.setText(full?"Full player":"Sessione Bitchord senza audio");setContentView(text);}
 @Override public void onCreate(android.os.Bundle state){super.onCreate(state);receive(getIntent());}
 @Override public void onNewIntent(android.content.Intent intent){super.onNewIntent(intent);setIntent(intent);receive(intent);}
}
