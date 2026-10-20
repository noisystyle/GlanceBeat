package com.music.bitchord;
import android.app.Application;
import android.graphics.*;
import android.media.*;
import android.media.session.*;
import android.os.Bundle;
import java.util.*;
/** No audio, network, accounts or libraries: only the observed public favorite and shuffle protocols. */
public class FavoriteFixture extends Application {
 static final String ACTION="com.music.bitchord.action.TOGGLE_FAVORITE",ICON="androidx.media3.session.EXTRAS_KEY_COMMAND_BUTTON_ICON_COMPAT",ID="androidx.media.PlaybackStateCompat.Extras.KEY_MEDIA_ID";
 static boolean shuffleOffered,shuffled,stars;static int shuffleCommands,shuffleIcon,fullPlayerLaunches;
 static MediaSession session;static boolean favorite,playing,ack=true;static long metadataId=100,stateId=100,extraId=100;static int commands,icon;
 static void metadata(){
  Bitmap art=Bitmap.createBitmap(256,256,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(art);c.drawColor(Color.rgb(30,55,120));Paint p=new Paint();p.setColor(Color.rgb(230,90,130));c.drawCircle(170,90,110,p);
  session.setMetadata(new MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_MEDIA_ID,"id-"+metadataId).putString(MediaMetadata.METADATA_KEY_TITLE,"Brano "+metadataId).putString(MediaMetadata.METADATA_KEY_ARTIST,"Sessione Bitchord senza audio").putLong(MediaMetadata.METADATA_KEY_DURATION,240000).putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART,art).build());
 }
 static void state(){
  Bundle extras=new Bundle();extras.putInt(ICON,icon!=0?icon:favorite?(stars?0xfe838:0xfe87d):(stars?0xe838:0xe87d));Bundle ids=new Bundle();ids.putString(ID,"id-"+extraId);
  PlaybackState.Builder builder=new PlaybackState.Builder().setState(playing?PlaybackState.STATE_PLAYING:PlaybackState.STATE_PAUSED,64000,playing?1:0).setActions(PlaybackState.ACTION_PLAY|PlaybackState.ACTION_PAUSE|PlaybackState.ACTION_SEEK_TO|PlaybackState.ACTION_SKIP_TO_QUEUE_ITEM).setActiveQueueItemId(stateId).setExtras(ids).addCustomAction(new PlaybackState.CustomAction.Builder(ACTION,"Favorite",android.R.drawable.btn_star).setExtras(extras).build());
  if(shuffleOffered){Bundle shuffle=new Bundle();shuffle.putInt(ICON,shuffleIcon!=0?shuffleIcon:shuffled?0xe043:0xfe044);builder.addCustomAction(new PlaybackState.CustomAction.Builder("com.music.bitchord.action.TOGGLE_SHUFFLE",shuffled?"Shuffle off":"Shuffle on",android.R.drawable.ic_menu_rotate).setExtras(shuffle).build());}
  session.setPlaybackState(builder.build());
 }
 @Override public void onCreate(){super.onCreate();session=new MediaSession(this,"Silent Bitchord protocol");
  var queue=new ArrayList<MediaSession.QueueItem>();for(int i=100;i<104;i++)queue.add(new MediaSession.QueueItem(new MediaDescription.Builder().setMediaId("id-"+i).setTitle("Brano "+i).setSubtitle("Sessione Bitchord senza audio").build(),i));session.setQueue(queue);
  session.setCallback(new MediaSession.Callback(){
   @Override public void onCustomAction(String action,Bundle args){if("com.music.bitchord.action.TOGGLE_SHUFFLE".equals(action) && shuffleOffered){shuffleCommands++;if(ack){shuffled=!shuffled;state();}return;}if(!ACTION.equals(action))return;commands++;if(ack){favorite=!favorite;state();}}
   @Override public void onSkipToQueueItem(long id){metadataId=stateId=extraId=id;favorite=false;metadata();state();}
   @Override public void onPlay(){playing=true;state();}
   @Override public void onPause(){playing=false;state();}
  });metadata();state();session.setActive(true);
 }
}
