package it.noxia.ambientplayer;
import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.regex.*;
import org.json.*;
/** Emulator only: YouTube compatibility shuffle bridge against an actual silent cross-process session. */
final class YoutubeShuffleChecks {
 private final Instrumentation host;private final JSONArray checks=new JSONArray();private MainActivity activity;private PlayerSurface surface;private PlayerView scene;
 private YoutubeShuffleChecks(Instrumentation h){host=h;}
 static void run(Instrumentation h){new YoutubeShuffleChecks(h).run();}
 private void main(Runnable r){host.runOnMainSync(r);}
 private Object get(Object o,String n){for(Class<?> c=o instanceof Class?(Class<?>)o:o.getClass();c!=null;c=c.getSuperclass())try{Field f=c.getDeclaredField(n);f.setAccessible(true);return f.get(o instanceof Class?null:o);}catch(NoSuchFieldException ignored){}catch(Exception e){throw new RuntimeException(e);}throw new RuntimeException(n);}
 private void call(Object o,String n){try{Method m=o.getClass().getDeclaredMethod(n);m.setAccessible(true);m.invoke(o);}catch(Exception e){throw new RuntimeException(e);}}
 private String shell(String s)throws Exception{try(var fd=host.getUiAutomation().executeShellCommand(s);var in=new FileInputStream(fd.getFileDescriptor())){return new String(in.readAllBytes());}}
 private void fixture(String args)throws Exception{shell("am broadcast -n app.morphe.android.apps.youtube.music/.FixtureReceiver "+args);Thread.sleep(200);}
 private void check(String n,boolean b)throws Exception{if(!b)throw new AssertionError(n);checks.put(new JSONObject().put("name",n).put("passed",true));}
 private int commandCount()throws Exception{Matcher m=Pattern.compile("data=\"(\\d+):(-?\\d+)\"").matcher(shell("am broadcast -n app.morphe.android.apps.youtube.music/.FixtureReceiver --ez query_shuffle true"));if(!m.find())throw new AssertionError("Missing shuffle counter");return Integer.parseInt(m.group(1));}
 private void active(){main(()->{((PlayerModeController)get(activity,"modes")).activate(SystemClock.uptimeMillis());call(activity,"applyMode");});}
 private void capture(String name)throws Exception{
  var committed=new java.util.concurrent.CountDownLatch(1);
  main(()->{View root=activity.getWindow().getDecorView();root.getViewTreeObserver().registerFrameCommitCallback(committed::countDown);root.invalidate();});
  if(!committed.await(4,java.util.concurrent.TimeUnit.SECONDS))throw new AssertionError("Emulator did not commit screenshot frame");
  Thread.sleep(100);var image=host.getUiAutomation().takeScreenshot();
  try(var out=new FileOutputStream(new File(host.getTargetContext().getFilesDir(),name))){image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}image.recycle();
 }
 private void run(){Bundle result=new Bundle();try{
  if(!Build.FINGERPRINT.contains("generic") && !Build.MODEL.contains("sdk"))throw new AssertionError("Emulator only");
  shell("am force-stop com.music.bitchord");shell("am force-stop app.morphe.android.apps.youtube.music");shell("cmd appops set "+host.getTargetContext().getPackageName()+" SYSTEM_ALERT_WINDOW allow");
  main(()->PlayerPreferences.setEnabled(host.getTargetContext(),true));shell("cmd notification allow_listener "+host.getTargetContext().getPackageName()+"/it.noxia.ambientplayer.MusicSessionService");Thread.sleep(500);
  fixture("--ez playing true --ez shuffle_supported true --ei shuffle_mode 0 --ez shuffle_ack false");
  shell("locksettings set-pin 1234");shell("input keyevent KEYCODE_SLEEP");Thread.sleep(2500);
  activity=(MainActivity)((java.lang.ref.WeakReference<?>)get(MainActivity.class,"surface")).get();check("youtube_automatically_opens_locked_player",activity!=null && !activity.isFinishing());
  surface=(PlayerSurface)get(activity,"widgets");scene=(PlayerView)get(activity,"player");active();Thread.sleep(900);
  View like=(View)get(surface,"like"),dislike=(View)get(surface,"dislike"),shuffle=(View)get(surface,"shuffle");
  check("youtube_has_like_dislike_and_shuffle",like.getVisibility()==View.VISIBLE && dislike.getVisibility()==View.VISIBLE && shuffle.getVisibility()==View.VISIBLE);
  check("three_buttons_are_balanced_without_overlap",like.getRight()<dislike.getLeft() && dislike.getRight()<shuffle.getLeft() && Math.abs(dislike.getX()+dislike.getWidth()/2f-scene.contentCenterX())<2 && like.getWidth()==shuffle.getWidth());
  check("compat_handshake_reads_initial_shuffle_off",shuffle.isEnabled() && !shuffle.isSelected());
  main(shuffle::performClick);Thread.sleep(250);check("standard_shuffle_command_once_without_optimistic_selection",commandCount()==1 && !shuffle.isSelected());
  fixture("--ei shuffle_mode 1 --ez shuffle_only true");check("shuffle_only_callback_updates_without_metadata_event",shuffle.isSelected());
  fixture("--ez playing true --ez shuffle_ack true");main(shuffle::performClick);Thread.sleep(300);check("second_tap_sends_off_and_waits_for_source",commandCount()==2 && !shuffle.isSelected());
  fixture("--ei shuffle_mode 2 --ez shuffle_only true");check("group_shuffle_is_displayed_as_on",shuffle.isSelected());
  fixture("--ei shuffle_mode -1 --ez shuffle_only true");check("unavailable_shuffle_disables_without_guessing",!shuffle.isSelected() && !shuffle.isEnabled());
  fixture("--ez playing true --ez shuffle_supported false --ei shuffle_mode 0");Thread.sleep(250);check("missing_capability_hides_shuffle_and_restores_thumb_pair",shuffle.getVisibility()==View.INVISIBLE && Math.abs((like.getX()+like.getWidth()/2f+dislike.getX()+dislike.getWidth()/2f)/2-scene.contentCenterX())<2);
  fixture("--ez playing true --ez shuffle_supported true --ei shuffle_mode 0");Thread.sleep(250);
  long down=SystemClock.uptimeMillis();main(()->{MotionEvent e=MotionEvent.obtain(down,down,MotionEvent.ACTION_DOWN,shuffle.getWidth()/2f,shuffle.getHeight()/2f,0);shuffle.dispatchTouchEvent(e);e.recycle();});
  fixture("--ei shuffle_mode 1 --ez shuffle_only true");main(()->{MotionEvent e=MotionEvent.obtain(down,SystemClock.uptimeMillis(),MotionEvent.ACTION_UP,shuffle.getWidth()/2f,shuffle.getHeight()/2f,0);shuffle.dispatchTouchEvent(e);e.recycle();});Thread.sleep(200);check("state_changed_during_press_cancels_toggle",commandCount()==2 && shuffle.isSelected());
  capture("youtube-shuffle.png");active();shell("wm size 2448x1848");shell("wm density 420");Thread.sleep(1100);
  check("internal_player_column_keeps_three_buttons_balanced",surface.splitColumns() && Math.abs(dislike.getX()+dislike.getWidth()/2f-scene.contentCenterX())<2 && shuffle.getRight()<=surface.getWidth());capture("youtube-internal-shuffle.png");
  main(()->{((PlayerModeController)get(activity,"modes")).toggle(SystemClock.uptimeMillis());call(activity,"applyMode");});Thread.sleep(700);check("aod_hides_all_three_buttons",like.getVisibility()==View.INVISIBLE && dislike.getVisibility()==View.INVISIBLE && shuffle.getVisibility()==View.INVISIBLE);
  result.putString("status","passed");
 }catch(Throwable failure){result.putString("status","failed");result.putString("error",failure.toString());}
 finally{main(()->{if(activity!=null)activity.finishAndRemoveTask();});try{shell("locksettings clear --old 1234");shell("wm size reset");shell("wm density reset");shell("am force-stop app.morphe.android.apps.youtube.music");}catch(Exception ignored){}try(var out=new FileOutputStream(new File(host.getTargetContext().getFilesDir(),"youtube-shuffle.json"))){out.write(new JSONObject().put("checks",checks).put("success","passed".equals(result.getString("status"))).put("error",result.getString("error","")).toString(2).getBytes());}catch(Exception ignored){}}
 host.finish("passed".equals(result.getString("status"))?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
 }
}
