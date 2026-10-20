package it.noxia.musicfixture;
import android.content.*;
import android.media.Rating;
public class FixtureReceiver extends BroadcastReceiver {
 @Override public void onReceive(Context context,Intent i){
  if(i.getBooleanExtra("query_launch",false)){var prefs=context.getSharedPreferences("launch",0);setResultData(prefs.getInt("count",0)+","+prefs.getBoolean("session",false)+","+prefs.getString("uri",""));return;}
  if(i.hasExtra("activity_mode"))FixtureApp.activity(i.getIntExtra("activity_mode",0));
  if(i.hasExtra("media_uri"))FixtureApp.mediaUri=i.getStringExtra("media_uri");
  if(i.getBooleanExtra("query",false)){setResultData(FixtureApp.nextCommands+","+FixtureApp.previousCommands+","+FixtureApp.seekCommands+","+FixtureApp.ratingCommands+","+FixtureApp.shuffleCommands);return;}
  if(i.hasExtra("active")){FixtureApp.compat.setActive(i.getBooleanExtra("active",true));return;}
  if(i.hasExtra("ack"))FixtureApp.ack=i.getBooleanExtra("ack",true);
  if(i.hasExtra("playing"))FixtureApp.playing=i.getBooleanExtra("playing",false);
  if(i.hasExtra("queue_off"))FixtureApp.queueOff=i.getBooleanExtra("queue_off",false);
  if(i.hasExtra("rating_style")){FixtureApp.ratingStyle=i.getIntExtra("rating_style",0);FixtureApp.rating=FixtureApp.ratingStyle==0?null:Rating.newUnratedRating(FixtureApp.ratingStyle);}
  if(i.hasExtra("favorite"))FixtureApp.rating=Rating.newHeartRating(i.getBooleanExtra("favorite",false));
  if(i.hasExtra("shuffle_supported"))FixtureApp.shuffleSupported=i.getBooleanExtra("shuffle_supported",false);
  if(i.hasExtra("shuffle")){FixtureApp.shuffle=i.getIntExtra("shuffle",-1);FixtureApp.compat.setShuffleMode(FixtureApp.shuffle);if(i.getBooleanExtra("shuffle_only",false))return;}
  FixtureApp.metadata();FixtureApp.queue();FixtureApp.state();
 }
}
