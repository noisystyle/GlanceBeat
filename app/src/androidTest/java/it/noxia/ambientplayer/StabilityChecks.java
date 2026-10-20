package it.noxia.ambientplayer;
import android.app.*;
import android.content.*;
import android.graphics.*;
import android.media.*;
import android.media.session.*;
import android.os.*;
import android.view.*;
import com.google.android.material.button.MaterialButton;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import org.json.*;
/** Emulator only. Silent real-session interruptions plus deliberately mixed rating/track snapshots. */
final class StabilityChecks {
 private final Instrumentation host;private final JSONArray checks=new JSONArray();
 private MainActivity activity;private PreviewActivity preview;private MediaSession session;private PlayerView scene;private PlayerSurface widgets;
 private int feedbackCommands;
 private StabilityChecks(Instrumentation h){host=h;}
 static void run(Instrumentation h){new StabilityChecks(h).run();}
 private void main(Runnable r){host.runOnMainSync(r);}
 private Field field(Object o,String n){for(Class<?> c=o instanceof Class?(Class<?>)o:o.getClass();c!=null;c=c.getSuperclass())try{Field f=c.getDeclaredField(n);f.setAccessible(true);return f;}catch(NoSuchFieldException ignored){}throw new RuntimeException(n);}
 private Object get(Object o,String n){try{return field(o,n).get(o instanceof Class?null:o);}catch(Exception e){throw new RuntimeException(e);}}
 private void set(Object o,String n,Object v){try{field(o,n).set(o instanceof Class?null:o,v);}catch(Exception e){throw new RuntimeException(e);}}
 private void check(String n,boolean b)throws Exception{if(!b)throw new AssertionError(n);checks.put(new JSONObject().put("name",n).put("passed",true));}
 private void shell(String s)throws Exception{try(var fd=host.getUiAutomation().executeShellCommand(s);var in=new FileInputStream(fd.getFileDescriptor())){in.readAllBytes();}}
 private MainActivity current(){return (MainActivity)((java.lang.ref.WeakReference<?>)get(MainActivity.class,"surface")).get();}
 private void fixture(String args)throws Exception{shell("am broadcast -n app.morphe.android.apps.youtube.music/.FixtureReceiver "+args);Thread.sleep(250);}
 private void awaitCoverMotion()throws Exception{
  long deadline=SystemClock.uptimeMillis()+2500;boolean[] moving={true};
  do{main(()->moving[0]=get(scene,"coverReturn")!=null || ((ContentLayer)get(scene,"coverLayer")).running());if(moving[0])Thread.sleep(20);}while(moving[0] && SystemClock.uptimeMillis()<deadline);
  if(moving[0])throw new AssertionError("Cover motion did not settle");
 }
 private boolean closing(){return ((Handler)get(activity,"handler")).hasCallbacks((Runnable)get(activity,"closePaused"));}
 private void publish(long metadataId,long stateId,Rating rating)throws Exception{
  main(()->{
   session.setMetadata(new MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_MEDIA_ID,"id-"+metadataId).putString(MediaMetadata.METADATA_KEY_TITLE,"Titolo ripetuto").putString(MediaMetadata.METADATA_KEY_ARTIST,"Artista").putLong(MediaMetadata.METADATA_KEY_DURATION,240000).putRating(MediaMetadata.METADATA_KEY_USER_RATING,rating).build());
   PlaybackState p=new PlaybackState.Builder().setState(PlaybackState.STATE_PLAYING,1000,1).setActiveQueueItemId(stateId).setActions(PlaybackState.ACTION_SET_RATING).build();session.setPlaybackState(p);set(MusicSessionService.class,"playbackSnapshot",p);
  });Thread.sleep(40);main(()->{scene.refreshMedia();widgets.refreshSession();});awaitCoverMotion();main(widgets::refreshFeedback);
 }
 private void run(){Bundle result=new Bundle();
  try{
   if(!Build.FINGERPRINT.contains("generic") && !Build.MODEL.contains("sdk"))throw new AssertionError("Emulator only");
   shell("cmd appops set "+host.getTargetContext().getPackageName()+" SYSTEM_ALERT_WINDOW allow");main(()->PlayerPreferences.setEnabled(host.getTargetContext(),true));
   shell("cmd notification allow_listener "+host.getTargetContext().getPackageName()+"/it.noxia.ambientplayer.MusicSessionService");Thread.sleep(700);
   fixture("--ez playing true --ei queue_count 3 --el queue_current 100");
   shell("locksettings set-pin 1234");shell("input keyevent KEYCODE_SLEEP");Thread.sleep(2200);
   activity=current();check("automatic_surface_created_for_playing_session",activity!=null && !activity.isFinishing());
   fixture("--ei transport_state 6");Thread.sleep(5700);
   check("buffering_longer_than_old_timeout_keeps_same_surface",current()==activity && !activity.isFinishing());
   check("buffering_has_no_close_timer",!closing());
   fixture("--ei transport_state 8");check("connecting_keeps_surface_without_close_timer",current()==activity && !activity.isFinishing() && !closing());
   fixture("--ez session_active false");Thread.sleep(1000);
   check("temporary_session_gap_keeps_surface_with_finite_deadline",!activity.isFinishing() && closing());
   fixture("--ez session_active true");fixture("--ez playing true");Thread.sleep(400);
   check("session_return_cancels_expiry_without_recreating_player",current()==activity && !activity.isFinishing() && !closing());
   PocketProximity pocket=(PocketProximity)get(activity,"pocket");
   main(()->{pocket.stop();set(pocket,"covered",true);set(pocket,"pendingCovered",true);set(pocket,"proximityNear",true);pocket.setEnabled(false);});
   check("monitor_pause_does_not_synthesize_an_uncover",pocket.isCovered() && !pocket.isListening());
   main(pocket::stop);check("final_sensor_stop_clears_coverage",!pocket.isCovered());
   fixture("--ez playing false");Thread.sleep(5500);check("genuine_pause_still_expires_player",activity.isFinishing());
   shell("cmd notification disallow_listener "+host.getTargetContext().getPackageName()+"/it.noxia.ambientplayer.MusicSessionService");shell("am force-stop app.morphe.android.apps.youtube.music");shell("locksettings clear --old 1234");shell("locksettings set-disabled true");shell("input keyevent KEYCODE_WAKEUP");shell("input keyevent KEYCODE_MENU");shell("wm dismiss-keyguard");Thread.sleep(600);
   preview=(PreviewActivity)host.startActivitySync(new Intent(host.getTargetContext(),PreviewActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
   main(()->{
    session=new MediaSession(preview,"Silent feedback fixture");session.setCallback(new MediaSession.Callback(){@Override public void onSetRating(Rating rating){feedbackCommands++;}},new Handler(Looper.getMainLooper()));session.setRatingType(Rating.RATING_THUMB_UP_DOWN);session.setActive(true);
    session.setQueue(List.of(new MediaSession.QueueItem(new MediaDescription.Builder().setMediaId("id-100").setTitle("Titolo ripetuto").setSubtitle("Artista").build(),100),new MediaSession.QueueItem(new MediaDescription.Builder().setMediaId("id-101").setTitle("Titolo ripetuto").setSubtitle("Artista").build(),101)));
    MusicSessionService.current=new MediaController(preview,session.getSessionToken());scene=new PlayerView(preview);widgets=new PlayerSurface(preview,scene,()->{},false,()->{});preview.setContentView(widgets);scene.setActive(true);widgets.setVisualProgress(1);
   });Thread.sleep(350);
   Rating liked=Rating.newThumbRating(true),neutral=Rating.newUnratedRating(Rating.RATING_THUMB_UP_DOWN);
   publish(100,100,liked);check("like_is_shown_for_matching_track",widgets.hasLike() && widgets.hasDislike() && widgets.isLikeSelected() && !widgets.isDislikeSelected());
   publish(100,101,liked);check("old_metadata_rating_is_neutral_while_queue_moves_ahead",!widgets.isLikeSelected() && !widgets.isDislikeSelected());
   MaterialButton like=(MaterialButton)get(widgets,"like");main(like::performClick);Thread.sleep(100);check("mismatched_rating_target_cannot_send_feedback",feedbackCommands==0);
   publish(100,999,liked);check("unpublished_active_queue_id_cannot_reuse_metadata_fallback_rating",!widgets.isLikeSelected() && !widgets.isDislikeSelected());
   publish(101,100,neutral);check("metadata_ahead_of_queue_also_keeps_rating_neutral",!widgets.isLikeSelected() && !widgets.isDislikeSelected());
   publish(101,101,neutral);check("unrated_next_track_does_not_inherit_like",!widgets.isLikeSelected() && !widgets.isDislikeSelected() && like.isEnabled());
   float x=like.getWidth()/2f,y=like.getHeight()/2f;long t=SystemClock.uptimeMillis();main(()->{MotionEvent e=MotionEvent.obtain(t,t,MotionEvent.ACTION_DOWN,x,y,0);like.dispatchTouchEvent(e);e.recycle();});
   publish(100,100,neutral);
   main(()->{MotionEvent e=MotionEvent.obtain(t,SystemClock.uptimeMillis(),MotionEvent.ACTION_UP,x,y,0);like.dispatchTouchEvent(e);e.recycle();});Thread.sleep(100);
   check("track_change_during_tap_does_not_vote_for_new_track",feedbackCommands==0);
   main(like::performClick);Thread.sleep(350);check("valid_tap_sends_exactly_one_public_rating",feedbackCommands==1 && !widgets.isLikeSelected());
   publish(100,100,Rating.newThumbRating(false));check("explicit_dislike_is_distinct_from_missing_rating",widgets.isDislikeSelected() && !widgets.isLikeSelected());
   publish(100,100,null);check("missing_rating_resets_both_selections",!widgets.isDislikeSelected() && !widgets.isLikeSelected());
   JSONObject diagnostic=new JSONObject().put("event","paused").put("mode","AMBIENT").put("visibilityKind","PLAYING").put("title","PRIVATE_TEST_TITLE").put("message","PRIVATE_TEST_MESSAGE");
   main(()->VisibilityJournal.record(preview,diagnostic));Thread.sleep(500);
   String journal=new String(java.nio.file.Files.readAllBytes(new File(preview.getFilesDir(),"visibility-history.json").toPath()));
   check("visibility_history_is_bounded_and_contains_no_contents",new JSONArray(journal).length()<=96 && !journal.contains("PRIVATE_TEST"));
   result.putString("status","passed");
  }catch(Throwable failure){result.putString("status","failed");result.putString("error",failure.toString());}
  finally{
   main(()->{if(activity!=null)activity.finishAndRemoveTask();if(preview!=null)preview.finish();if(session!=null)session.release();MusicSessionService.current=null;set(MusicSessionService.class,"playbackSnapshot",null);});
   try{shell("locksettings clear --old 1234");}catch(Exception ignored){}
   try(var out=new FileOutputStream(new File(host.getTargetContext().getFilesDir(),"stability.json"))){out.write(new JSONObject().put("checks",checks).put("success","passed".equals(result.getString("status"))).put("error",result.getString("error","")).toString(2).getBytes());}catch(Exception ignored){}
  }
  host.finish("passed".equals(result.getString("status"))?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
 }
}
