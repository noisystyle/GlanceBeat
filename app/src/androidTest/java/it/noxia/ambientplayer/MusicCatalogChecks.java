package it.noxia.ambientplayer;
import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.regex.*;
import org.json.*;
/** Emulator only: Explicit provider packages and standard controls on silent cross-process fixtures. */
final class MusicCatalogChecks {
 private final Instrumentation host;private final JSONArray checks=new JSONArray();private MainActivity activity;private PlayerSurface surface;private PlayerView scene;
 private MusicCatalogChecks(Instrumentation h){host=h;}
 static void run(Instrumentation h){new MusicCatalogChecks(h).run();}
 private void main(Runnable r){host.runOnMainSync(r);}
 private Object get(Object o,String n){for(Class<?> c=o instanceof Class?(Class<?>)o:o.getClass();c!=null;c=c.getSuperclass())try{Field f=c.getDeclaredField(n);f.setAccessible(true);return f.get(o instanceof Class?null:o);}catch(NoSuchFieldException ignored){}catch(Exception e){throw new RuntimeException(e);}throw new RuntimeException(n);}
 private void call(Object o,String n){try{Method m=o.getClass().getDeclaredMethod(n);m.setAccessible(true);m.invoke(o);}catch(Exception e){throw new RuntimeException(e);}}
 private String shell(String s)throws Exception{try(var fd=host.getUiAutomation().executeShellCommand(s);var in=new FileInputStream(fd.getFileDescriptor())){return new String(in.readAllBytes());}}
 private String source;
 private void fixture(String args)throws Exception{shell("am broadcast -n "+source+"/it.noxia.musicfixture.FixtureReceiver "+args);Thread.sleep(180);}
 private void check(String n,boolean b)throws Exception{if(!b)throw new AssertionError(n);checks.put(new JSONObject().put("name",n).put("passed",true));}
 private int[] counts()throws Exception{Matcher m=Pattern.compile("data=\"([0-9,]+)\"").matcher(shell("am broadcast -n "+source+"/it.noxia.musicfixture.FixtureReceiver --ez query true"));if(!m.find())throw new AssertionError("Missing catalog counter");return java.util.Arrays.stream(m.group(1).split(",")).mapToInt(Integer::parseInt).toArray();}
 private void pressTransport(boolean previous){main(()->{float scale=Math.min(((Integer)get(surface,"viewportWidth"))/400f,scene.getHeight()/650f);long t=SystemClock.uptimeMillis();MotionEvent e=MotionEvent.obtain(t,t,MotionEvent.ACTION_UP,scene.contentCenterX()+(previous?-88:88)*scale,scene.getHeight()/2f+scene.contentOffsetY()+58*scale,0);try{Method m=PlayerView.class.getDeclaredMethod("handleSingleTap",MotionEvent.class);m.setAccessible(true);m.invoke(scene,e);}catch(Exception failure){throw new RuntimeException(failure);}finally{e.recycle();}});}
 private void active(){main(()->{((PlayerModeController)get(activity,"modes")).activate(SystemClock.uptimeMillis());call(activity,"applyMode");});}
 private void capture(String name)throws Exception{
  var committed=new java.util.concurrent.CountDownLatch(1);
  main(()->{View root=activity.getWindow().getDecorView();root.getViewTreeObserver().registerFrameCommitCallback(committed::countDown);root.invalidate();});
  if(!committed.await(4,java.util.concurrent.TimeUnit.SECONDS))throw new AssertionError("Emulator did not commit screenshot frame");
  Thread.sleep(100);var image=host.getUiAutomation().takeScreenshot();
  try(var out=new FileOutputStream(new File(host.getTargetContext().getFilesDir(),name))){image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}image.recycle();
 }
 private static final String[] PROVIDERS={MusicSources.SPOTIFY,MusicSources.APPLE,MusicSources.TIDAL,MusicSources.DEEZER,MusicSources.AMAZON};
 private void run(){Bundle result=new Bundle();try{
  if(!Build.FINGERPRINT.contains("generic") && !Build.MODEL.contains("sdk"))throw new AssertionError("Emulator only");
  for(String p:PROVIDERS)shell("am force-stop "+p);shell("am force-stop com.music.bitchord");shell("am force-stop app.morphe.android.apps.youtube.music");
  shell("cmd appops set "+host.getTargetContext().getPackageName()+" SYSTEM_ALERT_WINDOW allow");main(()->PlayerPreferences.setEnabled(host.getTargetContext(),true));shell("cmd notification allow_listener "+host.getTargetContext().getPackageName()+"/it.noxia.ambientplayer.MusicSessionService");Thread.sleep(500);
  source=PROVIDERS[0];fixture("--ez playing true --ei activity_mode 0 --ei rating_style 1 --ez shuffle_supported true --ei shuffle 0");shell("locksettings set-pin 1234");shell("input keyevent KEYCODE_SLEEP");Thread.sleep(2500);
  activity=(MainActivity)((java.lang.ref.WeakReference<?>)get(MainActivity.class,"surface")).get();check("catalog_source_automatically_opens_locked_player",activity!=null && !activity.isFinishing() && host.getTargetContext().getSystemService(KeyguardManager.class).isKeyguardLocked());
  surface=(PlayerSurface)get(activity,"widgets");scene=(PlayerView)get(activity,"player");
  for(int i=0;i<PROVIDERS.length;i++){
   if(i>0){fixture("--ez playing false");source=PROVIDERS[i];}
   int style=i<2?1:i==2?2:0;boolean shuffleCap=i!=1 && i!=4;
   fixture("--ez playing true --ei activity_mode 0 --ei rating_style "+style+" --ez shuffle_supported "+shuffleCap+" --ei shuffle "+(shuffleCap?0:-1)+" --ez queue_off "+(i==4));active();Thread.sleep(950);
   String label=MusicSources.name(source);View like=(View)get(surface,"like"),dislike=(View)get(surface,"dislike"),shuffle=(View)get(surface,"shuffle");
   check(label+"_selected_from_real_package_session",source.equals(MusicSessionService.current.getPackageName()) && MusicSessionService.isPlaying() && !activity.isFinishing());
   check(label+"_metadata_artwork_and_seek_available",get(scene,"contentArtwork")!=null && surface.seekAvailable());
   check(label+"_only_published_feedback_is_visible",(like.getVisibility()==View.VISIBLE)==(style!=0) && (dislike.getVisibility()==View.VISIBLE)==(style==2) && (shuffle.getVisibility()==View.VISIBLE)==shuffleCap);
   if(style==0 && shuffleCap)check(label+"_shuffle_without_ratings_is_centered",Math.abs(shuffle.getX()+shuffle.getWidth()/2f-scene.contentCenterX())<2);
   if(style==1 && !shuffleCap)check(label+"_heart_without_shuffle_is_centered",Math.abs(like.getX()+like.getWidth()/2f-scene.contentCenterX())<2);
   int before=counts()[0];pressTransport(false);Thread.sleep(900);check(label+"_native_next_command_and_animated_handoff",counts()[0]==before+1 && "id-101".equals(MusicSessionService.current.getMetadata().getString(android.media.MediaMetadata.METADATA_KEY_MEDIA_ID)) && get(scene,"frozenRow")==null);
   before=counts()[1];pressTransport(true);Thread.sleep(900);check(label+"_native_previous_command_and_handoff",counts()[1]==before+1 && "id-100".equals(MusicSessionService.current.getMetadata().getString(android.media.MediaMetadata.METADATA_KEY_MEDIA_ID)));
   if(i==4)check(label+"_missing_queue_is_not_fabricated",MusicQueue.read().entries().isEmpty());
   else{
    boolean[] sent={false};main(()->{MusicQueue.Snapshot q=MusicQueue.read();sent[0]=q.entries().size()==4 && MusicQueue.select(q.token(),q.entries().get(2));});Thread.sleep(900);
    check(label+"_public_queue_selection_starts_requested_item",sent[0] && "id-102".equals(MusicSessionService.current.getMetadata().getString(android.media.MediaMetadata.METADATA_KEY_MEDIA_ID)));
    main(()->{MusicQueue.Snapshot q=MusicQueue.read();sent[0]=MusicQueue.select(q.token(),q.entries().get(0));});Thread.sleep(900);check(label+"_queue_can_return_to_first_item",sent[0] && "id-100".equals(MusicSessionService.current.getMetadata().getString(android.media.MediaMetadata.METADATA_KEY_MEDIA_ID)));
   }
   if(style!=0){
    before=counts()[3];main(like::performClick);Thread.sleep(280);check(label+"_standard_rating_acknowledged",counts()[3]==before+1 && surface.isLikeSelected());
    if(style==1){fixture("--ez favorite false");check(label+"_heart_state_updated_from_source",!surface.isLikeSelected());fixture("--ez ack false");before=counts()[3];main(like::performClick);Thread.sleep(220);check(label+"_heart_is_not_optimistic",counts()[3]==before+1 && !surface.isLikeSelected());fixture("--ez ack true");}
    else{main(dislike::performClick);Thread.sleep(280);check(label+"_standard_dislike_is_exclusive",surface.isDislikeSelected() && !surface.isLikeSelected());}
   }
   if(shuffleCap){before=counts()[4];main(shuffle::performClick);Thread.sleep(250);check(label+"_standard_shuffle_acknowledged",counts()[4]==before+1 && shuffle.isSelected());fixture("--ei shuffle 0 --ez shuffle_only true");check(label+"_shuffle_callback_without_metadata",!shuffle.isSelected());}
   if(i==0)capture("catalog-heart-shuffle.png");if(i==3)capture("catalog-shuffle-only.png");if(i==4)capture("catalog-transport-only.png");
   active();main(()->call(activity,"openMusic"));Thread.sleep(400);MusicLaunch.Request pending=(MusicLaunch.Request)get(activity,"pendingMusicLaunch");check(label+"_cover_launch_targets_current_app_after_unlock",pending!=null && pending.sessionActivity()!=null && source.equals(pending.source()) && host.getTargetContext().getSystemService(KeyguardManager.class).isKeyguardLocked());shell("input keyevent KEYCODE_BACK");Thread.sleep(450);active();
  }
  fixture("--ez playing false");source=MusicSources.SPOTIFY;fixture("--ez playing true --ei activity_mode 0 --ei rating_style 1 --ez shuffle_supported true --ei shuffle 0");active();Thread.sleep(900);
  View like=(View)get(surface,"like"),shuffle=(View)get(surface,"shuffle");int spotifyBefore=counts()[3];long down=SystemClock.uptimeMillis();main(()->{MotionEvent e=MotionEvent.obtain(down,down,MotionEvent.ACTION_DOWN,like.getWidth()/2f,like.getHeight()/2f,0);like.dispatchTouchEvent(e);e.recycle();});
  fixture("--ez playing false");source=MusicSources.APPLE;fixture("--ez playing true --ei activity_mode 0 --ei rating_style 1 --ez shuffle_supported true --ei shuffle 0");Thread.sleep(800);int appleBefore=counts()[3];
  main(()->{MotionEvent e=MotionEvent.obtain(down,SystemClock.uptimeMillis(),MotionEvent.ACTION_UP,like.getWidth()/2f,like.getHeight()/2f,0);like.dispatchTouchEvent(e);e.recycle();});Thread.sleep(220);check("switching_provider_during_rating_touch_does_not_vote_new_song",counts()[3]==appleBefore);
  source=MusicSources.SPOTIFY;check("switching_provider_during_touch_does_not_vote_old_source",counts()[3]==spotifyBefore);fixture("--ei shuffle 1 --ez shuffle_only true");
  check("late_paused_provider_shuffle_callback_cannot_steal_or_color_current_source",MusicSources.APPLE.equals(MusicSessionService.current.getPackageName()) && !shuffle.isSelected() && !activity.isFinishing());source=MusicSources.APPLE;
  result.putString("status","passed");
 }catch(Throwable failure){result.putString("status","failed");result.putString("error",failure.toString());}
 finally{main(()->{if(activity!=null)activity.finishAndRemoveTask();});try{shell("locksettings clear --old 1234");shell("wm size reset");shell("wm density reset");for(String p:PROVIDERS)shell("am force-stop "+p);}catch(Exception ignored){}try(var out=new FileOutputStream(new File(host.getTargetContext().getFilesDir(),"catalog.json"))){out.write(new JSONObject().put("checks",checks).put("success","passed".equals(result.getString("status"))).put("error",result.getString("error","")).toString(2).getBytes());}catch(Exception ignored){}}
 host.finish("passed".equals(result.getString("status"))?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
 }
}
