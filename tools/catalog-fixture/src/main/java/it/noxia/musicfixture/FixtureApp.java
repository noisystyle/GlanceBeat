package it.noxia.musicfixture;
import android.app.Application;
import android.graphics.*;
import android.media.*;
import android.media.session.*;
import android.os.Bundle;
import android.support.v4.media.RatingCompat;
import android.support.v4.media.session.*;
import java.util.*;
/** Native cross-process protocol only. No audio, network, account, streaming or official client code. */
public class FixtureApp extends Application {
 static MediaSessionCompat compat;static MediaSession session;static Bitmap art;
 static boolean playing,ack=true,shuffleSupported,queueOff;
 static String mediaUri;static android.app.PendingIntent sessionIntent;
 static int ratingStyle,index,shuffle=-1;static Rating rating;
 static int nextCommands,previousCommands,seekCommands,ratingCommands,shuffleCommands;static long position=64000;
 static void metadata(){
  MediaMetadata.Builder b=new MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_MEDIA_ID,"id-"+(100+index)).putString(MediaMetadata.METADATA_KEY_TITLE,"Brano sintetico "+(index+1)).putString(MediaMetadata.METADATA_KEY_ARTIST,"Sessione di compatibilità senza audio").putLong(MediaMetadata.METADATA_KEY_DURATION,240000).putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART,art);
  if(mediaUri!=null)b.putString(MediaMetadata.METADATA_KEY_MEDIA_URI,mediaUri);
  if(rating!=null)b.putRating(MediaMetadata.METADATA_KEY_USER_RATING,rating);
  session.setRatingType(ratingStyle);session.setMetadata(b.build());
 }
 static void queue(){var q=new ArrayList<MediaSession.QueueItem>();if(!queueOff)for(int i=0;i<4;i++)q.add(new MediaSession.QueueItem(new MediaDescription.Builder().setMediaId("id-"+(100+i)).setTitle("Brano sintetico "+(i+1)).setSubtitle("Sessione di compatibilità senza audio").setIconBitmap(art).build(),100+i));session.setQueue(q);}
 static void state(){long flags=PlaybackState.ACTION_PLAY|PlaybackState.ACTION_PAUSE|PlaybackState.ACTION_SKIP_TO_PREVIOUS|PlaybackState.ACTION_SKIP_TO_NEXT|PlaybackState.ACTION_SEEK_TO;
  if(!queueOff)flags|=PlaybackState.ACTION_SKIP_TO_QUEUE_ITEM;if(ratingStyle!=0)flags|=PlaybackState.ACTION_SET_RATING;if(shuffleSupported)flags|=PlaybackStateCompat.ACTION_SET_SHUFFLE_MODE;
  compat.setPlaybackState(PlaybackStateCompat.fromPlaybackState(new PlaybackState.Builder().setState(playing?PlaybackState.STATE_PLAYING:PlaybackState.STATE_PAUSED,position,playing?1:0).setActiveQueueItemId(100+index).setActions(flags).build()));
 }
 static void choose(int target){index=Math.max(0,Math.min(3,target));position=0;rating=ratingStyle==0?null:Rating.newUnratedRating(ratingStyle);metadata();state();}
 static void activity(int mode){
  if(mode==2){compat.setSessionActivity(null);return;}
  android.content.Intent intent=new android.content.Intent(FixtureAppHolder.context,FixtureActivity.class).putExtra("fixture.player",true).setAction("it.noxia.fixture.NOW_PLAYING");
  if(mode==3){intent=new android.content.Intent(FixtureAppHolder.context,FixtureReceiver.class);sessionIntent=android.app.PendingIntent.getBroadcast(FixtureAppHolder.context,71,intent,android.app.PendingIntent.FLAG_IMMUTABLE|android.app.PendingIntent.FLAG_UPDATE_CURRENT);}
  else sessionIntent=android.app.PendingIntent.getActivity(FixtureAppHolder.context,71,intent,android.app.PendingIntent.FLAG_IMMUTABLE|android.app.PendingIntent.FLAG_UPDATE_CURRENT);
  compat.setSessionActivity(sessionIntent);if(mode==1)sessionIntent.cancel();
 }
 private static class FixtureAppHolder{static android.content.Context context;}
 @Override public void onCreate(){super.onCreate();FixtureAppHolder.context=this;compat=new MediaSessionCompat(this,"Silent "+getPackageName());session=(MediaSession)compat.getMediaSession();
  activity(0);
  art=Bitmap.createBitmap(256,256,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(art);c.drawColor(Color.rgb(32,55,110));Paint p=new Paint();p.setColor(Color.rgb(224,130,158));c.drawCircle(165,90,105,p);
  compat.setCallback(new MediaSessionCompat.Callback(){
   @Override public void onPlay(){playing=true;state();}
   @Override public void onPause(){playing=false;state();}
   @Override public void onSkipToNext(){nextCommands++;if(ack)choose(index+1);}
   @Override public void onSkipToPrevious(){previousCommands++;if(ack)choose(index-1);}
   @Override public void onSkipToQueueItem(long id){if(ack)choose((int)id-100);}
   @Override public void onSeekTo(long value){seekCommands++;if(ack){position=value;state();}}
   @Override public void onSetRating(RatingCompat value){ratingCommands++;if(ack){rating=(Rating)value.getRating();metadata();}}
   @Override public void onSetShuffleMode(int mode){shuffleCommands++;if(ack){shuffle=mode;compat.setShuffleMode(mode);}}
  });metadata();queue();state();compat.setShuffleMode(-1);compat.setActive(true);
 }
}
