package app.morphe.android.apps.youtube.music;
import android.app.Application;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.media.MediaMetadata;
import android.media.Rating;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Bundle;
import android.util.Log;
public class FixtureApp extends Application {
 static MediaSession session;
 static android.support.v4.media.session.MediaSessionCompat compat;
 static boolean shuffleSupported,shuffleAck=true;static int shuffleCommands,lastShuffle=-1;
 static Bitmap art;
 static long position=64000;
 static long durationMs=240000;
 static String title="Brano di prova",artist="Sessione senza audio",mediaId="fixture-0";
 static boolean isPlaying;
 static boolean seekAllowed=true;
 static boolean queueSkipFlag=true,ignoreQueue=false;
 static int artDelayMs;
 static boolean trimQueue,hideQueue;
 static long activeQueueId=100;
 static java.util.List<MediaSession.QueueItem> queue=java.util.List.of();
 static void publishQueue(int count,boolean mediaIds) {
  var items=new java.util.ArrayList<MediaSession.QueueItem>();
  for(int i=0;i<count;i++)items.add(new MediaSession.QueueItem(new android.media.MediaDescription.Builder()
   .setTitle(i==0?"Brano di prova":"Orizzonti "+i).setSubtitle("Artista "+i)
   .setMediaId(mediaIds?"fixture-"+i:null).setIconBitmap(i%3==0?createArtwork(i%3):null)
   .setIconUri(android.net.Uri.parse("content://ambient.fixture.art/"+i+"?delay="+artDelayMs)).build(),100+i));
  queue=java.util.List.copyOf(items);publishVisibleQueue();session.setQueueTitle("Prossimi brani");
 }
 static void publishVisibleQueue() {
  int first=0;
  if(trimQueue)for(int i=0;i<queue.size();i++)if(queue.get(i).getQueueId()==activeQueueId){first=i;break;}
  session.setQueue(hideQueue?java.util.List.of():queue.subList(first,queue.size()));
 }
 static void choose(long id) {
  Log.i("FixtureQueue","requested="+id);
  if(ignoreQueue)return;
  for(var item:queue)if(item.getQueueId()==id) {
   activeQueueId=id;mediaId=item.getDescription().getMediaId();
   title=item.getDescription().getTitle().toString();artist=item.getDescription().getSubtitle().toString();
   position=0;durationMs=180000+(id%4)*30000;artwork((int)((id-100)%3));metadata();publishVisibleQueue();playback(true);return;
  }
 }
 static Rating rating=Rating.newUnratedRating(Rating.RATING_THUMB_UP_DOWN);
 static void artwork(int variant) { art=createArtwork(variant); }
 static Bitmap createArtwork(int variant) {
  Bitmap image=Bitmap.createBitmap(256,256,Bitmap.Config.ARGB_8888);
  Canvas canvas=new Canvas(image);Paint p=new Paint();
  canvas.drawColor(variant==0?Color.rgb(24,48,102):variant==1?Color.rgb(132,32,65):Color.rgb(22,123,77));
  p.setColor(variant==0?Color.rgb(203,102,110):variant==1?Color.rgb(239,164,60):Color.rgb(65,181,161));canvas.drawCircle(170,80,105,p);
  p.setColor(variant==0?Color.rgb(57,167,181):variant==1?Color.rgb(181,58,98):Color.rgb(36,102,148));canvas.drawCircle(35,225,120,p);
  return image;
 }
 static void metadata() {
  session.setMetadata(new MediaMetadata.Builder()
   .putString(MediaMetadata.METADATA_KEY_TITLE,title)
   .putString(MediaMetadata.METADATA_KEY_ARTIST,artist)
   .putString(MediaMetadata.METADATA_KEY_MEDIA_ID,mediaId)
   .putLong(MediaMetadata.METADATA_KEY_DURATION,durationMs)
   .putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART,art)
   .putRating(MediaMetadata.METADATA_KEY_USER_RATING,rating).build());
 }
 static void playback(boolean playing) {
  isPlaying=playing;
  session.setPlaybackState(new PlaybackState.Builder()
   .setState(playing?PlaybackState.STATE_PLAYING:PlaybackState.STATE_PAUSED,position,playing?1:0)
   .setActions(PlaybackState.ACTION_PLAY|PlaybackState.ACTION_PAUSE|PlaybackState.ACTION_PLAY_FROM_MEDIA_ID|PlaybackState.ACTION_SKIP_TO_PREVIOUS|PlaybackState.ACTION_SKIP_TO_NEXT
    |(shuffleSupported?2097152L:0)|(queueSkipFlag?PlaybackState.ACTION_SKIP_TO_QUEUE_ITEM:0)|(seekAllowed?PlaybackState.ACTION_SEEK_TO:0))
   .setActiveQueueItemId(activeQueueId)
   .addCustomAction("thumbs_up_action","Mi piace",android.R.drawable.star_big_on)
   .addCustomAction("thumbs_down_action","Non mi piace",android.R.drawable.star_big_off).build());
 }
 @Override public void onCreate() {
  super.onCreate(); compat=new android.support.v4.media.session.MediaSessionCompat(this,"Silent fixture");session=(MediaSession)compat.getMediaSession();compat.setShuffleMode(-1);compat.setSessionActivity(android.app.PendingIntent.getActivity(this,73,new android.content.Intent(this,FixtureActivity.class).setAction("fixture.NOW_PLAYING"),android.app.PendingIntent.FLAG_IMMUTABLE|android.app.PendingIntent.FLAG_UPDATE_CURRENT));
  artwork(0);
  metadata();publishQueue(16,true);
  session.setRatingType(Rating.RATING_THUMB_UP_DOWN);
  compat.setCallback(new android.support.v4.media.session.MediaSessionCompat.Callback() {
   @Override public void onSetShuffleMode(int mode){if(!shuffleSupported)return;shuffleCommands++;lastShuffle=mode;if(shuffleAck)compat.setShuffleMode(mode);}
   @Override public void onSkipToNext(){choose(activeQueueId+1);}
   @Override public void onSkipToPrevious() {choose(activeQueueId-1);}
   @Override public void onSkipToQueueItem(long id) { choose(id); }
   @Override public void onPlayFromMediaId(String id,Bundle extras) {
    for(var item:queue)if(java.util.Objects.equals(id,item.getDescription().getMediaId())){choose(item.getQueueId());return;}
   }
   @Override public void onSeekTo(long target) {
    position=target;playback(isPlaying);Log.i("FixtureSeek","position="+target);
   }
   @Override public void onCustomAction(String action,Bundle extras) {
    boolean positive="thumbs_up_action".equals(action);
    if(!positive && !"thumbs_down_action".equals(action))return;
    rating=rating.isRated() && rating.isThumbUp()==positive
      ? Rating.newUnratedRating(Rating.RATING_THUMB_UP_DOWN):Rating.newThumbRating(positive);
    Log.i("FixtureFeedback",action+" rated="+rating.isRated()+" up="+rating.isThumbUp());metadata();
   }
  });
  session.setActive(true);
 }
}
