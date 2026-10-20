package app.morphe.android.apps.youtube.music;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.session.PlaybackState;
public class FixtureReceiver extends BroadcastReceiver {
 @Override public void onReceive(Context c, Intent intent) {
  if(intent.getBooleanExtra("query_shuffle",false)){setResultData(FixtureApp.shuffleCommands+":"+FixtureApp.lastShuffle);return;}
  if(intent.hasExtra("shuffle_supported"))FixtureApp.shuffleSupported=intent.getBooleanExtra("shuffle_supported",false);
  if(intent.hasExtra("shuffle_ack"))FixtureApp.shuffleAck=intent.getBooleanExtra("shuffle_ack",true);
  if(intent.hasExtra("shuffle_mode")){FixtureApp.compat.setShuffleMode(intent.getIntExtra("shuffle_mode",-1));if(intent.getBooleanExtra("shuffle_only",false))return;}
  FixtureApp.seekAllowed=intent.getBooleanExtra("seek",true);
  if(intent.hasExtra("queue_count")) {
   FixtureApp.trimQueue=intent.getBooleanExtra("queue_trim",false);
   FixtureApp.artDelayMs=intent.getIntExtra("queue_art_delay",0);
   FixtureApp.queueSkipFlag=intent.getBooleanExtra("queue_skip_flag",true);
   FixtureApp.ignoreQueue=intent.getBooleanExtra("queue_ignore",false);
   FixtureApp.activeQueueId=intent.getLongExtra("queue_current",100);
   FixtureApp.publishQueue(intent.getIntExtra("queue_count",16),intent.getBooleanExtra("queue_media_ids",true));
  }
  if(intent.hasExtra("transport_state")) {
   int state=intent.getIntExtra("transport_state",PlaybackState.STATE_BUFFERING);
   FixtureApp.session.setPlaybackState(new PlaybackState.Builder().setState(state,FixtureApp.position,state==PlaybackState.STATE_PLAYING?1:0).setActiveQueueItemId(FixtureApp.activeQueueId).setActions(PlaybackState.ACTION_PLAY|PlaybackState.ACTION_PAUSE|PlaybackState.ACTION_SKIP_TO_NEXT).build());return;
  }
  if(intent.hasExtra("session_active")){FixtureApp.session.setActive(intent.getBooleanExtra("session_active",true));return;}
  if (intent.getBooleanExtra("state_only",false)) {
   FixtureApp.position=intent.getLongExtra("position_ms",0);FixtureApp.playback(true);return;
  }
  if (intent.hasExtra("track_id")) {
   FixtureApp.mediaId=intent.getStringExtra("track_id");
   FixtureApp.title=intent.getStringExtra("track_title");
   FixtureApp.artist=intent.getStringExtra("track_artist");
   FixtureApp.durationMs=intent.getLongExtra("duration_ms",240000);
   if(intent.hasExtra("art_variant"))FixtureApp.artwork(intent.getIntExtra("art_variant",0));
   FixtureApp.metadata();
   if (intent.getBooleanExtra("metadata_only",false)) return;
  }
  if (intent.hasExtra("duration_ms")) {
   FixtureApp.durationMs=intent.getLongExtra("duration_ms",240000);
   FixtureApp.position=intent.getLongExtra("position_ms",0);FixtureApp.metadata();
  }
  if (intent.hasExtra("art_variant")) {
   FixtureApp.artwork(intent.getIntExtra("art_variant",0));FixtureApp.metadata();
  }
  if (intent.getBooleanExtra("art_missing",false)) {
   FixtureApp.art=null;FixtureApp.metadata();
  }
  if (intent.getBooleanExtra("metadata_gap",false)) FixtureApp.session.setMetadata(null);
  if (intent.getBooleanExtra("metadata_ready",false)) { FixtureApp.position=0;FixtureApp.metadata(); }
  if (intent.getBooleanExtra("next_track",false)) {
   FixtureApp.position=0;FixtureApp.session.setMetadata(new android.media.MediaMetadata.Builder().putString(android.media.MediaMetadata.METADATA_KEY_TITLE,"Altro brano").putLong(android.media.MediaMetadata.METADATA_KEY_DURATION,180000).build());
  }
  boolean playing=intent.getBooleanExtra("playing",false);
  FixtureApp.playback(playing);
 }
}
