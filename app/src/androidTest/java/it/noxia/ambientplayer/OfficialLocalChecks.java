package it.noxia.ambientplayer;
import android.app.*;
import android.os.*;
import android.view.*;
import android.graphics.Bitmap;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.PlaybackState;
import java.io.*;
import java.lang.reflect.*;
import java.util.function.BooleanSupplier;
import org.json.*;
/** Real installed YouTube Music, emulator only. No fixture session or account is created. */
final class OfficialLocalChecks {
 private final Instrumentation host;private final JSONArray checks=new JSONArray();private MainActivity activity;private PlayerSurface surface;private PlayerView scene;
 private OfficialLocalChecks(Instrumentation h){host=h;}
 static void run(Instrumentation h){new OfficialLocalChecks(h).run();}
 private void main(Runnable r){host.runOnMainSync(r);}
 private Object get(Object o,String n){for(Class<?> c=o instanceof Class?(Class<?>)o:o.getClass();c!=null;c=c.getSuperclass())try{Field f=c.getDeclaredField(n);f.setAccessible(true);return f.get(o instanceof Class?null:o);}catch(NoSuchFieldException ignored){}catch(Exception e){throw new RuntimeException(e);}throw new RuntimeException(n);}
 private void call(Object o,String n){try{Method m=o.getClass().getDeclaredMethod(n);m.setAccessible(true);m.invoke(o);}catch(Exception e){throw new RuntimeException(e);}}
 private String shell(String s)throws Exception{try(var fd=host.getUiAutomation().executeShellCommand(s);var in=new FileInputStream(fd.getFileDescriptor())){return new String(in.readAllBytes());}}
 private void check(String n,boolean b)throws Exception{checks.put(new JSONObject().put("name",n).put("passed",b));if(!b)throw new AssertionError(n);}
 private void await(BooleanSupplier condition)throws Exception{long deadline=SystemClock.uptimeMillis()+7000;boolean[] ok={false};do{main(()->ok[0]=condition.getAsBoolean());if(!ok[0])Thread.sleep(40);}while(!ok[0] && SystemClock.uptimeMillis()<deadline);if(!ok[0])throw new AssertionError("Official provider did not acknowledge command");}
 private void settled()throws Exception{await(()->get(scene,"coverReturn")==null && !((ContentLayer)get(scene,"coverLayer")).running());main(surface::refreshSession);}
 private void capture()throws Exception{var latch=new java.util.concurrent.CountDownLatch(1);main(()->{View root=activity.getWindow().getDecorView();root.getViewTreeObserver().registerFrameCommitCallback(latch::countDown);root.invalidate();});if(!latch.await(4,java.util.concurrent.TimeUnit.SECONDS))throw new AssertionError("No screenshot frame");Bitmap b=host.getUiAutomation().takeScreenshot();try(var out=new FileOutputStream(new File(host.getTargetContext().getFilesDir(),"official-youtube-local.png"))){b.compress(Bitmap.CompressFormat.PNG,100,out);}b.recycle();}
 private String accessibility(android.view.accessibility.AccessibilityNodeInfo node){
  if(node==null)return "";StringBuilder text=new StringBuilder();if(node.getText()!=null)text.append(node.getText()).append('\n');if(node.getContentDescription()!=null)text.append(node.getContentDescription()).append('\n');for(int i=0;i<node.getChildCount();i++)text.append(accessibility(node.getChild(i)));return text.toString();
 }
 private void run(){JSONObject result=new JSONObject();Bundle status=new Bundle();try{
  if(!Build.FINGERPRINT.contains("generic") && !Build.MODEL.contains("sdk"))throw new AssertionError("Emulator only");
  main(()->PlayerPreferences.setEnabled(host.getTargetContext(),true));
  shell("cmd appops set "+host.getTargetContext().getPackageName()+" SYSTEM_ALERT_WINDOW allow");shell("cmd notification allow_listener "+host.getTargetContext().getPackageName()+"/it.noxia.ambientplayer.MusicSessionService");
  await(()->MusicSessionService.current!=null && MusicSources.YOUTUBE.equals(MusicSessionService.current.getPackageName()) && MusicSessionService.isPlaying());
  MediaController source=MusicSessionService.current;
  check("real_google_package_plays_only_generated_local_sample",source.getMetadata().getString(MediaMetadata.METADATA_KEY_TITLE).startsWith("Ambient Test "));
  result.put("provider_version",host.getTargetContext().getPackageManager().getPackageInfo(MusicSources.YOUTUBE,0).versionName);
  result.put("local_files_only",true);result.put("accounts_created",false);result.put("synthetic_media_session",false);
  JSONArray actions=new JSONArray();for(var a:source.getPlaybackState().getCustomActions())actions.put(new JSONObject().put("id",a.getAction()).put("label",a.getName().toString()));result.put("custom_actions",actions);result.put("native_actions",source.getPlaybackState().getActions());result.put("rating_type",source.getRatingType());
  shell("locksettings set-pin 1234");shell("input keyevent KEYCODE_SLEEP");Thread.sleep(2200);shell("am start -n "+host.getTargetContext().getPackageName()+"/it.noxia.ambientplayer.MainActivity");
  await(()->{var ref=(java.lang.ref.WeakReference<?>)get(MainActivity.class,"surface");return ref!=null && ref.get()!=null && !((MainActivity)ref.get()).isFinishing();});
  activity=(MainActivity)((java.lang.ref.WeakReference<?>)get(MainActivity.class,"surface")).get();surface=(PlayerSurface)get(activity,"widgets");scene=(PlayerView)get(activity,"player");
  main(()->{((PlayerModeController)get(activity,"modes")).activate(SystemClock.uptimeMillis());call(activity,"applyMode");});Thread.sleep(1000);settled();
  check("real_local_playback_opens_locked_player",MusicSources.YOUTUBE.equals(MusicSessionService.current.getPackageName()) && !activity.isFinishing());
  MusicQueue.Snapshot q=MusicQueue.read();check("official_queue_exposes_three_local_samples",q.entries().size()==3 && q.entries().stream().allMatch(e->e.title().startsWith("Ambient Test ")));
  check("official_local_queue_is_selectable",q.entries().stream().allMatch(q::selectable));
  var target=q.entries().get(1);boolean[] sent={false};main(()->sent[0]=MusicQueue.select(q.token(),target));check("queue_selection_sent_via_player",sent[0]);
  await(()->MusicQueue.confirmed(MusicQueue.read(),q.token(),target.key()));settled();check("official_player_confirms_requested_queue_song",MusicSessionService.current.getMetadata().getString(MediaMetadata.METADATA_KEY_TITLE).equals(target.title()));
  main(()->MusicSessionService.current.getTransportControls().skipToNext());await(()->MusicQueue.read().current()==2);settled();check("official_next_updates_player_and_queue",MusicQueue.read().current()==2);
  main(()->MusicSessionService.current.getTransportControls().skipToPrevious());await(()->MusicQueue.read().current()==1);settled();check("official_previous_updates_player_and_queue",MusicQueue.read().current()==1);
  main(surface::refreshFeedback);View shuffle=(View)get(surface,"shuffle");
  await(()->MusicShuffle.selected(MusicSessionService.current,MusicSessionService.playbackState(),MusicSessionService.current.getMetadata())!=null);
  check("official_shuffle_is_available_in_player",shuffle.getVisibility()==View.VISIBLE && shuffle.isEnabled());
  Boolean before=MusicShuffle.selected(MusicSessionService.current,MusicSessionService.playbackState(),MusicSessionService.current.getMetadata());
  main(shuffle::performClick);await(()->!before.equals(MusicShuffle.selected(MusicSessionService.current,MusicSessionService.playbackState(),MusicSessionService.current.getMetadata())));
  check("official_shuffle_callback_confirms_toggle",shuffle.isSelected()!=before);
  main(shuffle::performClick);await(()->before.equals(MusicShuffle.selected(MusicSessionService.current,MusicSessionService.playbackState(),MusicSessionService.current.getMetadata())));
  check("official_shuffle_restores_original_mode",shuffle.isSelected()==before);
  check("feedback_visibility_matches_published_capabilities",surface.hasLike()==MusicFeedback.available(MusicSessionService.current,true) && surface.hasDislike()==MusicFeedback.available(MusicSessionService.current,false));
  boolean originalLike=MusicFeedback.selected(MusicSessionService.current,true);
  if(surface.hasLike()){
   View like=(View)get(surface,"like");main(like::performClick);long deadline=SystemClock.uptimeMillis()+2500;
   while(MusicFeedback.selected(MusicSessionService.current,true)==originalLike && SystemClock.uptimeMillis()<deadline)Thread.sleep(40);
   boolean acknowledged=MusicFeedback.selected(MusicSessionService.current,true)!=originalLike;
   result.put("local_rating_acknowledged",acknowledged);
   main(surface::refreshFeedback);check("feedback_selection_matches_source_without_optimistic_like",surface.isLikeSelected()==MusicFeedback.selected(MusicSessionService.current,true));
   if(acknowledged){main(like::performClick);await(()->MusicFeedback.selected(MusicSessionService.current,true)==originalLike);}
  }

  main(surface::openQueue);Thread.sleep(650);check("official_queue_page_displays_current_and_following_items",surface.queueOpen() && surface.queueItems()==2);capture();
  androidx.recyclerview.widget.RecyclerView list=(androidx.recyclerview.widget.RecyclerView)get(get(surface,"queue"),"list");
  await(()->list.findViewHolderForAdapterPosition(1)!=null);
  main(()->((View)get(list.findViewHolderForAdapterPosition(1),"card")).performClick());
  await(()->MusicQueue.read().current()==2 && !surface.queueOpen());
  check("tap_on_real_queue_row_starts_song_and_returns_to_player",MusicQueue.read().current()==2 && !surface.queuePending());
  main(()->{((PlayerModeController)get(activity,"modes")).activate(SystemClock.uptimeMillis());call(activity,"applyMode");call(activity,"openMusic");});Thread.sleep(350);
  shell("input keyevent KEYCODE_WAKEUP");shell("input text 1234");shell("input keyevent KEYCODE_ENTER");Thread.sleep(1200);
  check("cover_opens_real_youtube_music_after_native_unlock",activity.isFinishing() && !host.getTargetContext().getSystemService(KeyguardManager.class).isKeyguardLocked());
  android.view.accessibility.AccessibilityNodeInfo root=host.getUiAutomation().getRootInActiveWindow();
  String ui=accessibility(root);result.put("opened_ui",ui);
  check("real_youtube_session_activity_opens_full_current_player",root!=null && MusicSources.YOUTUBE.contentEquals(root.getPackageName()) && ui.contains("Next track") && ui.contains("Up next"));
  result.put("success",true);status.putString("status","passed");
 }catch(Throwable e){try{result.put("success",false);result.put("error",e.toString());}catch(Exception ignored){}status.putString("status","failed");status.putString("error",e.toString());}
 finally{try{shell("locksettings clear --old 1234");shell("input keyevent KEYCODE_WAKEUP");}catch(Exception ignored){}main(()->{if(activity!=null)activity.finishAndRemoveTask();});try{result.put("checks",checks);try(var out=new FileOutputStream(new File(host.getTargetContext().getFilesDir(),"official-youtube-local.json"))){out.write(result.toString(2).getBytes());}}catch(Exception ignored){}}
 host.finish("passed".equals(status.getString("status"))?Activity.RESULT_OK:Activity.RESULT_CANCELED,status);
 }
}
