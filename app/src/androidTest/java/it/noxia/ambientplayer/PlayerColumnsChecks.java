package it.noxia.ambientplayer;
import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import androidx.recyclerview.widget.RecyclerView;
import java.io.*;
import java.lang.reflect.*;
import org.json.*;
/** Emulator-only: window resizing, real queue selection and touch arbitration in both columns. */
final class PlayerColumnsChecks {
 private final Instrumentation host;private final JSONArray checks=new JSONArray();private MainActivity activity;
 private PlayerSurface surface;private PlayerView scene;private android.animation.ValueAnimator motion;private String originalAnimatorScale;
 private PlayerColumnsChecks(Instrumentation h){host=h;}
 static void run(Instrumentation h){new PlayerColumnsChecks(h).run();}
 private void main(Runnable r){host.runOnMainSync(r);}
 private Object get(Object o,String n){for(Class<?> c=o instanceof Class?(Class<?>)o:o.getClass();c!=null;c=c.getSuperclass())try{Field f=c.getDeclaredField(n);f.setAccessible(true);return f.get(o instanceof Class?null:o);}catch(NoSuchFieldException ignored){}catch(Exception e){throw new RuntimeException(e);}throw new RuntimeException(n);}
 private void call(Object o,String n){try{Method m=o.getClass().getDeclaredMethod(n);m.setAccessible(true);m.invoke(o);}catch(Exception e){throw new RuntimeException(e);}}
 private void shell(String s)throws Exception{try(var fd=host.getUiAutomation().executeShellCommand(s);var in=new FileInputStream(fd.getFileDescriptor())){in.readAllBytes();}}
 private void check(String n,boolean b)throws Exception{if(!b)throw new AssertionError(n);checks.put(new JSONObject().put("name",n).put("passed",true));}
 private MainActivity current(){return (MainActivity)((java.lang.ref.WeakReference<?>)get(MainActivity.class,"surface")).get();}
 private QueuePanel queue(){return (QueuePanel)get(surface,"queue");}
 private void active(){main(()->{((PlayerModeController)get(activity,"modes")).activate(SystemClock.uptimeMillis());call(activity,"applyMode");});}
 private void capture(String name)throws Exception{
  var committed=new java.util.concurrent.CountDownLatch(1);
  main(()->{View root=activity.getWindow().getDecorView();root.getViewTreeObserver().registerFrameCommitCallback(committed::countDown);root.invalidate();});
  if(!committed.await(4,java.util.concurrent.TimeUnit.SECONDS))throw new AssertionError("Emulator did not commit screenshot frame");Thread.sleep(100);var b=host.getUiAutomation().takeScreenshot();
  if(name.equals("columns-transition-queue.png")){
   float scale=Math.min(queue().getWidth()/400f,scene.getHeight()/650f);
   int pixel=b.getPixel(Math.round(scene.contentCenterX()),Math.round(scene.getHeight()/2f+scene.contentOffsetY()-178*scale));
   check("rendered_cover_reaches_column_before_queue_reveal",Math.abs(android.graphics.Color.red(pixel)-203)+Math.abs(android.graphics.Color.green(pixel)-102)+Math.abs(android.graphics.Color.blue(pixel)-110)<70);
  }try(var out=new FileOutputStream(new File(host.getTargetContext().getFilesDir(),name))){b.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}b.recycle();}
 private void touch(long t,int action,float x,float y){main(()->{MotionEvent e=MotionEvent.obtain(t,SystemClock.uptimeMillis(),action,x,y,0);surface.dispatchTouchEvent(e);e.recycle();});}
 private void resize(String size)throws Exception{shell("wm size "+size);Thread.sleep(1000);}
 private void run(){Bundle result=new Bundle();
  try{
   if(!Build.FINGERPRINT.contains("generic") && !Build.MODEL.contains("sdk"))throw new AssertionError("Emulator only");
   originalAnimatorScale=android.provider.Settings.Global.getString(host.getTargetContext().getContentResolver(),android.provider.Settings.Global.ANIMATOR_DURATION_SCALE);
   shell("wm density 420");resize("2448x1848");
   main(()->{PlayerPreferences.setEnabled(host.getTargetContext(),true);PlayerPreferences.store(host.getTargetContext()).edit().putBoolean(PlayerPreferences.INTERNAL_REVERSED,false).apply();});
   shell("am force-stop app.morphe.android.apps.youtube.music");
   shell("cmd appops set "+host.getTargetContext().getPackageName()+" SYSTEM_ALERT_WINDOW allow");shell("cmd notification allow_listener "+host.getTargetContext().getPackageName()+"/it.noxia.ambientplayer.MusicSessionService");Thread.sleep(600);
   shell("am broadcast -n app.morphe.android.apps.youtube.music/.FixtureReceiver --ez playing true --ei queue_count 16 --el queue_current 100");
   shell("locksettings set-pin 1234");shell("input keyevent KEYCODE_SLEEP");Thread.sleep(2600);
   activity=current();check("internal_display_automatically_shows_locked_player",activity!=null && !activity.isFinishing());
   surface=(PlayerSurface)get(activity,"widgets");scene=(PlayerView)get(activity,"player");
   active();main(()->{motion=(android.animation.ValueAnimator)get(scene,"transition");motion.pause();motion.setCurrentPlayTime(200);});
   check("wake_keeps_queue_hidden_while_player_moves_to_its_column",scene.progress()>0 && scene.columnProgress()>0 && scene.columnProgress()<1 && scene.queueReveal()==0 && queue().getAlpha()==0);
   check("moving_queue_does_not_intercept_player_touches",!(boolean)get(queue(),"interactive") && !queue().findViewById(R.id.queue_swap_columns).isEnabled());
   capture("columns-transition-moving.png");
   main(()->motion.setCurrentPlayTime(600));
   check("queue_fades_in_only_after_player_reaches_its_column",scene.columnProgress()==1 && scene.queueReveal()>0 && scene.queueReveal()<1 && queue().getAlpha()>0 && queue().getAlpha()<1);
   View movingSeek=(View)get(surface,"seekTouch");check("native_widgets_share_the_animated_player_center",Math.abs(movingSeek.getX()+movingSeek.getWidth()/2f-scene.contentCenterX())<2);
   capture("columns-transition-queue.png");main(motion::end);Thread.sleep(150);
   check("internal_display_has_persistent_left_queue_and_right_player",surface.splitColumns() && queue().getVisibility()==View.VISIBLE && queue().getLeft()==0 && scene.contentCenterX()>surface.getWidth()/2f && !surface.auxiliaryOpen());
   check("queue_has_direct_swap_button_without_back_button",surface.queueItems()==16 && queue().findViewById(R.id.queue_close).getVisibility()==View.GONE && queue().findViewById(R.id.queue_swap_columns).getVisibility()==View.VISIBLE && queue().findViewById(R.id.queue_swap_columns).isEnabled());
   View seek=(View)get(surface,"seekTouch");check("native_controls_stay_inside_player_column",seek.getLeft()>surface.getWidth()/2 && seek.getRight()<surface.getWidth());
   float s=Math.min(queue().getWidth()/400f,scene.getHeight()/650f),y=scene.getHeight()/2f+scene.contentOffsetY()-178*s;
   check("cover_gesture_is_reserved_only_in_player_column",scene.coverBandHit(scene.contentCenterX(),y) && !scene.coverBandHit(queue().getWidth()/2f,y));
   RecyclerView initial=(RecyclerView)queue().findViewById(R.id.queue_list);
   check("initial_queue_rows_are_visible_after_column_reveal",initial.getChildCount()>0 && initial.getChildAt(0).getAlpha()>.9f);
   capture("columns-default.png");
   RecyclerView list=(RecyclerView)queue().findViewById(R.id.queue_list);main(()->((android.view.ViewGroup)list.findViewHolderForAdapterPosition(1).itemView).getChildAt(1).performClick());Thread.sleep(1000);
   check("queue_selection_starts_real_fixture_track_without_hiding_column",MusicSessionService.playbackState().getActiveQueueItemId()==101 && !queue().pending() && queue().getVisibility()==View.VISIBLE && surface.queueItems()==15 && !surface.auxiliaryOpen());
   long t=SystemClock.uptimeMillis();float x=scene.contentCenterX();touch(t,0,x,y);Thread.sleep(40);touch(t,2,x-100*s,y);
   check("cover_row_moves_during_drag_without_opening_queue",scene.coverTravel()!=0 && !surface.queueOpen());touch(t,1,x-130*s,y);Thread.sleep(1000);
   check("cover_swipe_selects_next_track_beside_persistent_queue",MusicSessionService.playbackState().getActiveQueueItemId()==102 && !surface.queueOpen() && queue().getVisibility()==View.VISIBLE);
   main(()->queue().findViewById(R.id.queue_swap_columns).performClick());Thread.sleep(350);
   check("header_swap_saves_order_without_sending_music_commands",PlayerPreferences.internalReversed(activity) && MusicSessionService.playbackState().getActiveQueueItemId()==102 && !surface.queuePending());
   check("reversing_columns_moves_queue_and_native_controls_together",PlayerPreferences.internalReversed(activity) && queue().getLeft()>0 && scene.contentCenterX()<surface.getWidth()/2f && seek.getRight()<surface.getWidth()/2);
   capture("columns-reversed.png");
   float awakeScale=Math.min(queue().getWidth()/400f,scene.getHeight()/650f);
   main(()->{((PlayerModeController)get(activity,"modes")).toggle(SystemClock.uptimeMillis());call(activity,"applyMode");});Thread.sleep(700);
   check("aod_hides_queue_and_stops_artwork_observation",queue().getVisibility()==View.INVISIBLE && !(boolean)get(queue(),"accepting"));
   check("aod_recenters_player_without_resizing_elements",Math.abs(scene.contentCenterX()-surface.getWidth()/2f)<1 && Math.abs(awakeScale-Math.min(queue().getWidth()/400f,scene.getHeight()/650f))<.01f);capture("columns-aod.png");
   active();main(()->{motion=(android.animation.ValueAnimator)get(scene,"transition");motion.pause();motion.setCurrentPlayTime(250);});
   float interruptedCenter=scene.contentCenterX(),interruptedProgress=scene.progress();
   main(()->{((PlayerModeController)get(activity,"modes")).toggle(SystemClock.uptimeMillis());call(activity,"applyMode");motion=(android.animation.ValueAnimator)get(scene,"transition");motion.pause();});
   check("reversing_wake_mid_motion_preserves_position_and_brightness",Math.abs(scene.contentCenterX()-interruptedCenter)<1 && Math.abs(scene.progress()-interruptedProgress)<.01f);
   main(motion::end);Thread.sleep(100);check("reversed_motion_finishes_in_centered_idle_aod",scene.progress()==0 && scene.queueReveal()==0 && !scene.isTransitionRunning() && !surface.progressTickScheduled());
   active();Thread.sleep(850);resize("1848x2448");
   check("rotating_internal_display_preserves_same_active_player_and_columns",current()==activity && !activity.isFinishing() && scene.isActiveTarget() && surface.splitColumns() && queue().getVisibility()==View.VISIBLE);
   check("native_seek_remains_aligned_after_window_resize",Math.abs(seek.getX()+seek.getWidth()/2f-scene.contentCenterX())<2);
   capture("columns-portrait.png");resize("1248x1972");
   check("folding_restores_single_player_without_leaving_queue_over_it",current()==activity && scene.isActiveTarget() && !surface.splitColumns() && queue().getVisibility()==View.INVISIBLE);
   main(surface::openQueue);Thread.sleep(500);check("cover_display_retains_original_queue_page_gesture",surface.queueOpen() && queue().findViewById(R.id.queue_close).getVisibility()==View.VISIBLE && queue().findViewById(R.id.queue_swap_columns).getVisibility()==View.GONE);main(()->surface.closeQueue(false));
   resize("2448x1848");check("unfolding_restores_saved_column_order",current()==activity && surface.splitColumns() && queue().getLeft()>0 && scene.contentCenterX()<surface.getWidth()/2f);
   main(()->{((PlayerModeController)get(activity,"modes")).toggle(SystemClock.uptimeMillis());call(activity,"applyMode");android.animation.ValueAnimator a=(android.animation.ValueAnimator)get(scene,"transition");a.end();});
   shell("settings put global animator_duration_scale 0");long until=SystemClock.uptimeMillis()+2500;while(android.animation.ValueAnimator.areAnimatorsEnabled() && SystemClock.uptimeMillis()<until)Thread.sleep(30);
   active();
   check("disabled_system_animations_settle_columns_and_queue_immediately",scene.progress()==1 && scene.columnProgress()==1 && scene.queueReveal()==1 && !scene.isTransitionRunning());
   shell("settings put global animator_duration_scale "+(originalAnimatorScale==null?"1":originalAnimatorScale));
   result.putString("status","passed");
  }catch(Throwable failure){result.putString("status","failed");result.putString("error",failure.toString());}
  finally{main(()->{if(activity!=null)activity.finishAndRemoveTask();PlayerPreferences.store(host.getTargetContext()).edit().remove(PlayerPreferences.INTERNAL_REVERSED).apply();});try{shell("settings put global animator_duration_scale "+(originalAnimatorScale==null?"1":originalAnimatorScale));shell("locksettings clear --old 1234");shell("wm size reset");shell("wm density reset");}catch(Exception ignored){}try(var out=new FileOutputStream(new File(host.getTargetContext().getFilesDir(),"columns.json"))){out.write(new JSONObject().put("checks",checks).put("success","passed".equals(result.getString("status"))).put("error",result.getString("error","")).toString(2).getBytes());}catch(Exception ignored){}}
  host.finish("passed".equals(result.getString("status"))?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
 }
}
