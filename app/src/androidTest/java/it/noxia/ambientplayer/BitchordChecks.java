package it.noxia.ambientplayer;
import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.regex.*;
import org.json.*;
/** Emulator only: two silent app sessions and Bitchord’s observed Media3 heart/shuffle contracts. */
final class BitchordChecks {
 private final Instrumentation host;private final JSONArray checks=new JSONArray();private MainActivity activity;private PlayerSurface surface;private PlayerView scene;
 private BitchordChecks(Instrumentation h){host=h;}
 static void run(Instrumentation h){new BitchordChecks(h).run();}
 private void main(Runnable r){host.runOnMainSync(r);}
 private Object get(Object o,String n){for(Class<?> c=o instanceof Class?(Class<?>)o:o.getClass();c!=null;c=c.getSuperclass())try{Field f=c.getDeclaredField(n);f.setAccessible(true);return f.get(o instanceof Class?null:o);}catch(NoSuchFieldException ignored){}catch(Exception e){throw new RuntimeException(e);}throw new RuntimeException(n);}
 private void call(Object o,String n){try{Method m=o.getClass().getDeclaredMethod(n);m.setAccessible(true);m.invoke(o);}catch(Exception e){throw new RuntimeException(e);}}
 private String shell(String s)throws Exception{try(var fd=host.getUiAutomation().executeShellCommand(s);var in=new FileInputStream(fd.getFileDescriptor())){return new String(in.readAllBytes());}}
 private void fixture(String args)throws Exception{shell("am broadcast -n com.music.bitchord/.FixtureReceiver "+args);Thread.sleep(200);}
 private void awaitCoverMotion()throws Exception{
  long deadline=SystemClock.uptimeMillis()+2500;boolean[] moving={true};
  do{main(()->moving[0]=get(scene,"coverReturn")!=null || ((ContentLayer)get(scene,"coverLayer")).running());if(moving[0])Thread.sleep(20);}while(moving[0] && SystemClock.uptimeMillis()<deadline);
  if(moving[0])throw new AssertionError("Cover motion did not settle");
 }
 private void check(String n,boolean b)throws Exception{if(!b)throw new AssertionError(n);checks.put(new JSONObject().put("name",n).put("passed",true));}
 private int commands()throws Exception{Matcher m=Pattern.compile("data=\"(\\d+)\"").matcher(shell("am broadcast -n com.music.bitchord/.FixtureReceiver --ez query true"));if(!m.find())throw new AssertionError("Missing fixture counter");return Integer.parseInt(m.group(1));}
 private int shuffleCommands()throws Exception{Matcher m=Pattern.compile("data=\"(\\d+)\"").matcher(shell("am broadcast -n com.music.bitchord/.FixtureReceiver --ez query_shuffle true"));if(!m.find())throw new AssertionError("Missing shuffle counter");return Integer.parseInt(m.group(1));}
 private void tapWindow(View view){
  int[] location=new int[2];float[] point=new float[2];main(()->{view.getLocationOnScreen(location);point[0]=location[0]+view.getWidth()/2f;point[1]=location[1]+view.getHeight()/2f;});
  long t=SystemClock.uptimeMillis();MotionEvent down=MotionEvent.obtain(t,t,MotionEvent.ACTION_DOWN,point[0],point[1],0),up=MotionEvent.obtain(t,t+80,MotionEvent.ACTION_UP,point[0],point[1],0);
  down.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);up.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);host.sendPointerSync(down);host.sendPointerSync(up);down.recycle();up.recycle();
 }
 private void active(){main(()->{((PlayerModeController)get(activity,"modes")).activate(SystemClock.uptimeMillis());call(activity,"applyMode");});}
 private void capture(String name)throws Exception{
  var committed=new java.util.concurrent.CountDownLatch(1);
  main(()->{View root=activity.getWindow().getDecorView();root.getViewTreeObserver().registerFrameCommitCallback(committed::countDown);root.invalidate();});
  if(!committed.await(4,java.util.concurrent.TimeUnit.SECONDS))throw new AssertionError("Emulator did not commit screenshot frame");
  Thread.sleep(100);var image=host.getUiAutomation().takeScreenshot();
  if(name.equals("bitchord-internal-shuffle.png")){
   float scale=Math.min(((Integer)get(surface,"viewportWidth"))/400f,scene.getHeight()/650f);
   int pixel=image.getPixel(Math.round(scene.contentCenterX()),Math.round(scene.getHeight()/2f+scene.contentOffsetY()-178*scale));
   check("internal_shuffle_capture_contains_rendered_cover",android.graphics.Color.red(pixel)+android.graphics.Color.blue(pixel)>120);
  }
  try(var out=new FileOutputStream(new File(host.getTargetContext().getFilesDir(),name))){image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}image.recycle();
 }
 private void run(){Bundle result=new Bundle();try{
  if(!Build.FINGERPRINT.contains("generic") && !Build.MODEL.contains("sdk"))throw new AssertionError("Emulator only");
  shell("am force-stop com.music.bitchord");shell("am force-stop app.morphe.android.apps.youtube.music");
  shell("cmd appops set "+host.getTargetContext().getPackageName()+" SYSTEM_ALERT_WINDOW allow");main(()->PlayerPreferences.setEnabled(host.getTargetContext(),true));shell("cmd notification allow_listener "+host.getTargetContext().getPackageName()+"/it.noxia.ambientplayer.MusicSessionService");Thread.sleep(600);
  shell("am broadcast -n app.morphe.android.apps.youtube.music/.FixtureReceiver --ez playing false");fixture("--ez playing true --ez ack false");
  shell("locksettings set-pin 1234");shell("input keyevent KEYCODE_SLEEP");Thread.sleep(2500);
  activity=(MainActivity)((java.lang.ref.WeakReference<?>)get(MainActivity.class,"surface")).get();
  check("bitchord_playback_automatically_opens_locked_player",activity!=null && !activity.isFinishing() && MusicSessionService.isPlaying() && MusicSources.BITCHORD.equals(MusicSessionService.current.getPackageName()) && host.getTargetContext().getSystemService(KeyguardManager.class).isKeyguardLocked());
  surface=(PlayerSurface)get(activity,"widgets");scene=(PlayerView)get(activity,"player");active();Thread.sleep(450);
  View like=(View)get(surface,"like"),dislike=(View)get(surface,"dislike");
  check("bitchord_shows_one_centered_heart",surface.hasLike() && !surface.hasDislike() && like.getVisibility()==View.VISIBLE && dislike.getVisibility()==View.INVISIBLE && Math.abs(like.getX()+like.getWidth()/2f-scene.contentCenterX())<2 && like.getContentDescription().toString().equals(host.getTargetContext().getString(R.string.add_favorite)));
  check("outline_heart_matches_unfavorited_source",!surface.isLikeSelected() && like.isEnabled());capture("bitchord-empty.png");
  fixture("--ei icon 59448");check("bitchord181_star_outline_keeps_heart_enabled",!surface.isLikeSelected() && like.isEnabled());
  fixture("--ei icon 1042488");check("bitchord181_star_filled_selects_heart",surface.isLikeSelected() && like.isEnabled());fixture("--ei icon 0");
  tapWindow(like);Thread.sleep(300);check("heart_window_tap_sends_exact_favorite_command_once_without_optimistic_selection",commands()==1 && !surface.isLikeSelected());
  fixture("--ez favorite true");check("state_only_favorite_update_fills_heart",surface.isLikeSelected() && like.getContentDescription().toString().equals(host.getTargetContext().getString(R.string.remove_favorite)));capture("bitchord-filled.png");
  fixture("--ez favorite false");check("favorite_removed_in_source_clears_heart",!surface.isLikeSelected());
  fixture("--ez ack true");main(like::performClick);Thread.sleep(350);check("confirmed_toggle_fills_heart",commands()==2 && surface.isLikeSelected());
  active();fixture("--el metadata_id 101 --ez metadata_only true");check("new_metadata_cannot_inherit_previous_favorite",!surface.isLikeSelected() && !like.isEnabled());
  fixture("--el state_id 101 --el extra_id 100 --ez favorite true");check("stale_media3_state_id_cannot_color_new_track",!surface.isLikeSelected() && !like.isEnabled());
  fixture("--el extra_id 101 --ez favorite false");awaitCoverMotion();check("matching_state_restores_unfavorited_heart",!surface.isLikeSelected() && like.isEnabled());
  fixture("--ei icon 123");check("unknown_media3_icon_is_neutral_and_disabled",!surface.isLikeSelected() && !like.isEnabled());fixture("--ei icon 0");
  long t=SystemClock.uptimeMillis();main(()->{MotionEvent e=MotionEvent.obtain(t,t,MotionEvent.ACTION_DOWN,like.getWidth()/2f,like.getHeight()/2f,0);like.dispatchTouchEvent(e);e.recycle();});
  fixture("--el metadata_id 102 --el state_id 102 --el extra_id 102 --ez favorite false");Thread.sleep(450);
  main(()->{MotionEvent e=MotionEvent.obtain(t,SystemClock.uptimeMillis(),MotionEvent.ACTION_UP,like.getWidth()/2f,like.getHeight()/2f,0);like.dispatchTouchEvent(e);e.recycle();});Thread.sleep(250);
  check("track_switch_during_press_cannot_favorite_next_song",commands()==2 && !surface.isLikeSelected());
  active();fixture("--ez session_active false");Thread.sleep(500);
  check("temporary_bitchord_gap_does_not_show_cached_paused_youtube",MusicSessionService.current==null && MusicSessionService.presenceKind()==PlaybackPresence.Kind.MISSING && !activity.isFinishing());
  fixture("--ez session_active true");Thread.sleep(350);check("returning_bitchord_session_recovers_same_player",MusicSources.BITCHORD.equals(MusicSessionService.current.getPackageName()) && !activity.isFinishing());
  active();fixture("--ez shuffle_offered true --ez shuffled false --ez ack false");Thread.sleep(350);
  View shuffle=(View)get(surface,"shuffle");
  check("official_shuffle_action_adds_matching_button_next_to_heart",shuffle.getVisibility()==View.VISIBLE && dislike.getVisibility()==View.INVISIBLE && like.getWidth()==shuffle.getWidth() && like.getHeight()==shuffle.getHeight() && Math.abs((like.getX()+like.getWidth()/2f+shuffle.getX()+shuffle.getWidth()/2f)/2-scene.contentCenterX())<2);
  check("shuffle_off_matches_source_and_is_accessible",!shuffle.isSelected() && shuffle.isEnabled() && shuffle.getContentDescription().toString().equals(host.getTargetContext().getString(R.string.enable_shuffle)));
  int shuffleBefore=shuffleCommands();main(shuffle::performClick);Thread.sleep(250);
  check("shuffle_sends_only_exact_command_without_optimistic_state",shuffleCommands()==shuffleBefore+1 && !shuffle.isSelected() && commands()==2);
  fixture("--ez shuffled true");check("source_only_shuffle_update_selects_button",shuffle.isSelected() && shuffle.getContentDescription().toString().equals(host.getTargetContext().getString(R.string.disable_shuffle)));
  fixture("--ez ack true");main(shuffle::performClick);Thread.sleep(250);check("shuffle_can_be_disabled_with_source_confirmation",!shuffle.isSelected() && shuffleCommands()==shuffleBefore+2);
  fixture("--ei shuffle_icon 123");check("unknown_shuffle_icon_disables_without_inventing_state",!shuffle.isEnabled() && !shuffle.isSelected());fixture("--ei shuffle_icon 0");
  fixture("--el extra_id 101");check("shuffle_cannot_use_old_track_state",!shuffle.isEnabled() && !shuffle.isSelected());fixture("--el extra_id 102");
  final long shuffleDown=SystemClock.uptimeMillis();main(()->{MotionEvent e=MotionEvent.obtain(shuffleDown,shuffleDown,MotionEvent.ACTION_DOWN,shuffle.getWidth()/2f,shuffle.getHeight()/2f,0);shuffle.dispatchTouchEvent(e);e.recycle();});
  fixture("--el metadata_id 103 --el state_id 103 --el extra_id 103");Thread.sleep(250);
  main(()->{MotionEvent e=MotionEvent.obtain(shuffleDown,SystemClock.uptimeMillis(),MotionEvent.ACTION_UP,shuffle.getWidth()/2f,shuffle.getHeight()/2f,0);shuffle.dispatchTouchEvent(e);e.recycle();});Thread.sleep(200);
  check("changing_track_during_shuffle_press_cancels_command",shuffleCommands()==shuffleBefore+2);
  final long stateDown=SystemClock.uptimeMillis();main(()->{MotionEvent e=MotionEvent.obtain(stateDown,stateDown,MotionEvent.ACTION_DOWN,shuffle.getWidth()/2f,shuffle.getHeight()/2f,0);shuffle.dispatchTouchEvent(e);e.recycle();});
  fixture("--ez shuffled true");
  main(()->{MotionEvent e=MotionEvent.obtain(stateDown,SystemClock.uptimeMillis(),MotionEvent.ACTION_UP,shuffle.getWidth()/2f,shuffle.getHeight()/2f,0);shuffle.dispatchTouchEvent(e);e.recycle();});Thread.sleep(200);
  check("source_state_change_during_press_cannot_toggle_it_back",shuffleCommands()==shuffleBefore+2 && shuffle.isSelected());
  capture("bitchord-shuffle.png");
  main(()->{((PlayerModeController)get(activity,"modes")).toggle(SystemClock.uptimeMillis());call(activity,"applyMode");});Thread.sleep(1100);
  check("aod_hides_both_interactive_buttons",shuffle.getVisibility()==View.INVISIBLE && like.getVisibility()==View.INVISIBLE);active();Thread.sleep(800);
  final long sourceDown=SystemClock.uptimeMillis();main(()->{MotionEvent e=MotionEvent.obtain(sourceDown,sourceDown,MotionEvent.ACTION_DOWN,shuffle.getWidth()/2f,shuffle.getHeight()/2f,0);shuffle.dispatchTouchEvent(e);e.recycle();});
  fixture("--ez playing false");shell("am broadcast -n app.morphe.android.apps.youtube.music/.FixtureReceiver --ez playing true");Thread.sleep(700);
  main(()->{MotionEvent e=MotionEvent.obtain(sourceDown,SystemClock.uptimeMillis(),MotionEvent.ACTION_UP,shuffle.getWidth()/2f,shuffle.getHeight()/2f,0);shuffle.dispatchTouchEvent(e);e.recycle();});Thread.sleep(200);
  check("source_switch_during_shuffle_press_never_targets_either_client",shuffleCommands()==shuffleBefore+2);
  check("playing_youtube_session_wins_over_paused_bitchord",MusicSources.MORPHE.equals(MusicSessionService.current.getPackageName()) && !activity.isFinishing());
  check("switching_to_youtube_restores_both_thumb_buttons",surface.hasLike() && surface.hasDislike() && shuffle.getVisibility()==View.INVISIBLE && dislike.getVisibility()==View.VISIBLE && like.getX()+like.getWidth()/2f<scene.contentCenterX());
  shell("am broadcast -n app.morphe.android.apps.youtube.music/.FixtureReceiver --ez playing false");fixture("--ez playing true --ez shuffle_offered false");Thread.sleep(500);
  check("returning_to_bitchord_restores_one_centered_heart",MusicSources.BITCHORD.equals(MusicSessionService.current.getPackageName()) && !surface.hasDislike() && Math.abs(like.getX()+like.getWidth()/2f-scene.contentCenterX())<2);
  active();shell("wm size 2448x1848");shell("wm density 420");Thread.sleep(1000);
  check("bitchord_heart_stays_centered_in_internal_player_column",surface.splitColumns() && !surface.hasDislike() && Math.abs(like.getX()+like.getWidth()/2f-scene.contentCenterX())<2);
  fixture("--ez shuffle_offered true --ez shuffled true");Thread.sleep(300);
  check("internal_column_keeps_shuffle_and_heart_balanced",shuffle.getVisibility()==View.VISIBLE && Math.abs((like.getX()+like.getWidth()/2f+shuffle.getX()+shuffle.getWidth()/2f)/2-scene.contentCenterX())<2);
  capture("bitchord-internal-shuffle.png");fixture("--ez shuffle_offered false");
  capture("bitchord-internal.png");shell("wm size reset");shell("wm density reset");Thread.sleep(800);
  active();main(()->call(activity,"openMusic"));Thread.sleep(450);MusicLaunch.Request pending=(MusicLaunch.Request)get(activity,"pendingMusicLaunch");
  check("cover_opens_bitchord_after_native_unlock_instead_of_youtube",pending!=null && pending.direct()!=null && pending.direct().getAction()==null && pending.direct().getBooleanExtra("bitchord.openPlayer",false) && MusicSources.BITCHORD.equals(pending.source()) && host.getTargetContext().getSystemService(KeyguardManager.class).isKeyguardLocked());
  shell("input keyevent KEYCODE_BACK");Thread.sleep(500);check("canceling_unlock_preserves_locked_player_without_launch",get(activity,"pendingMusicLaunch")==null && !activity.isFinishing());
  active();fixture("--ez stars true --ez favorite false --ez ack true");awaitCoverMotion();
  check("bitchord181_star_protocol_accepts_window_taps",like.isEnabled() && !surface.isLikeSelected());
  int starBefore=commands();tapWindow(like);Thread.sleep(350);check("bitchord181_real_window_tap_favorites_once",commands()==starBefore+1 && surface.isLikeSelected());
  tapWindow(like);Thread.sleep(350);check("bitchord181_second_window_tap_unfavorites_once",commands()==starBefore+2 && !surface.isLikeSelected());
  shell("wm size 2448x1848");shell("wm density 420");Thread.sleep(1000);active();Thread.sleep(800);
  check("bitchord181_heart_enabled_on_internal_display",surface.splitColumns() && like.isEnabled());
  tapWindow(like);Thread.sleep(350);check("bitchord181_internal_window_tap_reaches_heart",commands()==starBefore+3 && surface.isLikeSelected());capture("bitchord181-internal.png");
  main(()->call(activity,"openMusic"));Thread.sleep(350);shell("input keyevent KEYCODE_WAKEUP");shell("input text 1234");shell("input keyevent KEYCODE_ENTER");Thread.sleep(1000);
  check("cover_unlock_opens_bitchord_full_player",activity.isFinishing() && shell("am broadcast -n com.music.bitchord/.FixtureReceiver --ez query_open true").contains("data=\"1\""));
  SettingsActivity settings=(SettingsActivity)host.startActivitySync(new Intent(host.getTargetContext(),SettingsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
  MusicLaunch.Request warm=MusicLaunch.resolve(settings,MusicSessionService.current);boolean[] opened={false};main(()->opened[0]=MusicLaunch.open(settings,warm,MusicSessionService.current));Thread.sleep(450);
  check("warm_bitchord_receives_full_player_request_again",opened[0] && shell("am broadcast -n com.music.bitchord/.FixtureReceiver --ez query_open true").contains("data=\"2\""));main(settings::finish);
  result.putString("status","passed");
 }catch(Throwable failure){result.putString("status","failed");result.putString("error",failure.toString());}
 finally{main(()->{if(activity!=null)activity.finishAndRemoveTask();});try{shell("locksettings clear --old 1234");shell("wm size reset");shell("wm density reset");shell("am force-stop com.music.bitchord");}catch(Exception ignored){}try(var out=new FileOutputStream(new File(host.getTargetContext().getFilesDir(),"bitchord.json"))){out.write(new JSONObject().put("checks",checks).put("success","passed".equals(result.getString("status"))).put("error",result.getString("error","")).toString(2).getBytes());}catch(Exception ignored){}}
 host.finish("passed".equals(result.getString("status"))?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
 }
}
