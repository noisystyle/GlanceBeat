package it.noxia.ambientplayer;
import android.app.*;
import android.content.*;
import android.graphics.drawable.ColorDrawable;
import android.os.*;
import android.view.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import org.json.*;
/** Emulator-only: real touch arbitration, shared geometry, native keyguard launch gating. */
final class PlayerOpenLayoutChecks {
 private final Instrumentation host;private final JSONArray checks=new JSONArray();private PreviewActivity preview;private MainActivity locked;
 private PlayerView scene;private PlayerSurface surface;private int opens,toggles,notificationOpens,unlockRequests;
 private PlayerOpenLayoutChecks(Instrumentation h){host=h;}
 static void run(Instrumentation h){new PlayerOpenLayoutChecks(h).run();}
 private void main(Runnable r){host.runOnMainSync(r);}
 private Field field(Object o,String n)throws Exception{for(Class<?> c=o.getClass();c!=null;c=c.getSuperclass())try{Field f=c.getDeclaredField(n);f.setAccessible(true);return f;}catch(NoSuchFieldException ignored){}throw new NoSuchFieldException(n);}
 private Object get(Object o,String n){try{return field(o,n).get(o);}catch(Exception e){throw new RuntimeException(e);}}
 private void set(Object o,String n,Object v){try{field(o,n).set(o,v);}catch(Exception e){throw new RuntimeException(e);}}
 private void call(Object o,String n){try{Method m=o.getClass().getDeclaredMethod(n);m.setAccessible(true);m.invoke(o);}catch(Exception e){throw new RuntimeException(e);}}
 private void check(String n,boolean v)throws Exception{if(!v)throw new AssertionError(n);checks.put(new JSONObject().put("name",n).put("passed",true));}
 private String shell(String s)throws Exception{try(var fd=host.getUiAutomation().executeShellCommand(s);var in=new FileInputStream(fd.getFileDescriptor())){return new String(in.readAllBytes());}}
 private float usefulShift(){return (scene.contentSafeTop()-scene.contentSafeBottom())/2f;}
 private float scale(){return Math.min(scene.getWidth()/400f,scene.getHeight()/650f);}
 private void touch(long down,long now,int a,float x,float y){main(()->{MotionEvent e=MotionEvent.obtain(down,now,a,x,y,0);scene.onTouchEvent(e);e.recycle();});}
 private void tap()throws Exception{long t=SystemClock.uptimeMillis();float x=scene.getWidth()/2f,y=scene.getHeight()/2f+scene.contentOffsetY()-178*scale();touch(t,t,0,x,y);touch(t,t+40,1,x,y);Thread.sleep(450);}
 private void swipeUp()throws Exception{long t=SystemClock.uptimeMillis();float x=scene.getWidth()/2f,y=scene.getHeight()/2f+scene.contentOffsetY()+155*scale();touch(t,t,0,x,y);for(int i=1;i<=5;i++){Thread.sleep(30);touch(t,SystemClock.uptimeMillis(),2,x,y-i*30*scale());}Thread.sleep(30);touch(t,SystemClock.uptimeMillis(),1,x,y-160*scale());Thread.sleep(450);}
 private void notifications(boolean awake,boolean ambient){main(()->{var visible=new NotificationIcons.Snapshot(List.of(new ColorDrawable(-1)),1);set(scene,"activeNotifications",awake?visible:NotificationIcons.Snapshot.EMPTY);set(scene,"ambientNotifications",ambient?visible:NotificationIcons.Snapshot.EMPTY);call(scene,"updateNotificationLayout");});}
 private void activate(){main(()->{((PlayerModeController)get(locked,"modes")).activate(SystemClock.uptimeMillis());call(locked,"applyMode");});}
 private void run(){Bundle result=new Bundle();
  try{
   if(!Build.FINGERPRINT.contains("generic") && !Build.MODEL.contains("sdk"))throw new AssertionError("Emulator only");
   preview=(PreviewActivity)host.startActivitySync(new Intent(host.getTargetContext(),PreviewActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
   main(()->{scene=new PlayerView(preview,true,new PlayerView.Actions(){public void onActivate(){}public void onToggle(){toggles++;scene.setActive(!scene.isActiveTarget());}public void onInteract(){}public void onUnlock(){unlockRequests++;}public void onQueue(){}public void onAccessory(){}public void onOpenMusic(){opens++;}public void onOpenNotification(PendingIntent action){notificationOpens++;}public void onVisualFrame(){if(surface!=null)surface.setVisualProgress(scene.progress());}public void onVisualSettled(){}public void onStatusChanged(){}});surface=new PlayerSurface(preview,scene,()->{},true,()->{});preview.setContentView(surface);scene.setActive(true);});
   Thread.sleep(600);check("empty_active_block_centered",Math.abs(scene.contentOffsetY()-(4*scale()+usefulShift()))<.01f);
   View seek=(View)get(surface,"seekTouch");check("seek_tracks_shared_content_offset",Math.abs(seek.getTranslationY()-scene.contentOffsetY())<.01f);
   check("cover_hit_area_tracks_recenter",scene.coverBandHit(scene.getWidth()/2f,scene.getHeight()/2f+scene.contentOffsetY()-178*scale()));
   tap();check("confirmed_center_tap_opens_once",opens==1);
   long t=SystemClock.uptimeMillis();float x=scene.getWidth()/2f,y=scene.getHeight()/2f+scene.contentOffsetY()-178*scale();
   touch(t,t,0,x,y);touch(t,t+30,1,x,y);touch(t+100,t+100,0,x,y);touch(t+100,t+130,1,x,y);Thread.sleep(500);
   check("double_tap_toggles_without_opening_music",toggles==1 && opens==1 && !scene.isActiveTarget());
   check("empty_aod_block_centered",Math.abs(scene.contentOffsetY()-(63*scale()+usefulShift()))<.01f);tap();check("aod_single_tap_never_opens_music",opens==1);
   notifications(true,false);Thread.sleep(450);check("ambient_suppressed_notification_does_not_reserve_space",Math.abs(scene.contentOffsetY()-(63*scale()+usefulShift()))<.01f);
   main(()->scene.setActive(true));Thread.sleep(450);check("active_notification_restores_original_layout",Math.abs(scene.contentOffsetY())<.01f);
   PendingIntent action=PendingIntent.getActivity(preview,100,new Intent().setComponent(new ComponentName("app.morphe.android.apps.youtube.music","app.morphe.android.apps.youtube.music.FixtureActivity")),PendingIntent.FLAG_IMMUTABLE);
   NotificationIcons adapter=new NotificationIcons();
   PendingIntent older=PendingIntent.getActivity(preview,99,new Intent().setComponent(new ComponentName("app.morphe.android.apps.youtube.music","app.morphe.android.apps.youtube.music.FixtureActivity")),PendingIntent.FLAG_IMMUTABLE);
   Notification oldNotification=new Notification.Builder(preview,"test").setSmallIcon(R.drawable.ic_launcher).setContentIntent(older).build();
   Notification newNotification=new Notification.Builder(preview,"test").setSmallIcon(R.drawable.ic_launcher).setContentIntent(action).build();
   var oldStatus=new android.service.notification.StatusBarNotification("fixture.app","fixture.app",1,null,12345,0,0,oldNotification,android.os.Process.myUserHandle(),1);
   var newStatus=new android.service.notification.StatusBarNotification("fixture.app","fixture.app",2,null,12345,0,0,newNotification,android.os.Process.myUserHandle(),2);
   adapter.update(preview,new android.service.notification.StatusBarNotification[]{oldStatus,newStatus},null);
   check("grouped_icon_uses_most_recent_notification_destination",adapter.snapshot(false).icons().size()==1 && adapter.snapshot(false).actions().get(0).equals(action));
   check("removing_latest_updates_action_even_with_same_icon",adapter.update(preview,new android.service.notification.StatusBarNotification[]{oldStatus},null) && adapter.snapshot(false).actions().get(0).equals(older));
   oldNotification.contentIntent=null;adapter.update(preview,new android.service.notification.StatusBarNotification[]{oldStatus},null);
   check("notification_without_destination_keeps_icon_without_fake_action",adapter.snapshot(false).icons().size()==1 && adapter.snapshot(false).actions().get(0)==null);
   adapter.clear();check("clearing_notifications_releases_launch_actions",adapter.snapshot(false).actions().isEmpty());
   main(()->set(scene,"activeNotifications",new NotificationIcons.Snapshot(List.of(new ColorDrawable(-1)),1,List.of(action))));
   long nt=SystemClock.uptimeMillis();float nx=scene.getWidth()/2f,ny=scene.getHeight()/2f+scene.contentOffsetY()+258*scale();
   touch(nt,nt,0,nx,ny);touch(nt,nt+40,1,nx,ny);Thread.sleep(450);check("notification_icon_tap_uses_original_action",notificationOpens==1 && opens==1);
   nt=SystemClock.uptimeMillis();touch(nt,nt,0,nx,ny);touch(nt,nt+40,1,nx,ny);
   main(()->set(scene,"activeNotifications",NotificationIcons.Snapshot.EMPTY));Thread.sleep(450);check("removed_notification_is_not_opened_by_delayed_tap",notificationOpens==1);
   notifications(false,false);Thread.sleep(90);float mid=scene.contentOffsetY();check("notification_removal_animates_position",mid>0 && mid<4*scale()+usefulShift() && scene.isContentTransitionRunning());Thread.sleep(450);
   check("notification_layout_finishes_without_idle_loop",!scene.isContentTransitionRunning() && Math.abs(scene.contentOffsetY()-(4*scale()+usefulShift()))<.01f);
   t=SystemClock.uptimeMillis();y=scene.getHeight()/2f+scene.contentOffsetY()-178*scale();touch(t,t,0,x,y);touch(t,t+60,2,x-80*scale(),y);touch(t,t+160,1,x-110*scale(),y);Thread.sleep(500);check("cover_swipe_does_not_open_music",opens==1);
   swipeUp();check("active_upward_swipe_requests_native_unlock",unlockRequests==1);
   t=SystemClock.uptimeMillis();y=scene.getHeight()/2f+scene.contentOffsetY()+218*scale();touch(t,t,0,x,y);touch(t,t+40,1,x,y);Thread.sleep(450);check("active_arrow_tap_requests_same_native_unlock",unlockRequests==2);
   main(()->scene.setActive(false));Thread.sleep(450);swipeUp();check("aod_upward_swipe_does_not_request_unlock",unlockRequests==2);main(()->scene.setActive(true));Thread.sleep(450);
   notifications(true,true);main(()->scene.setVisibility(View.INVISIBLE));check("hidden_notification_layout_settles",!scene.isContentTransitionRunning());main(preview::finish);Thread.sleep(250);
   main(()->PlayerPreferences.setEnabled(host.getTargetContext(),true));
   shell("cmd appops set "+host.getTargetContext().getPackageName()+" SYSTEM_ALERT_WINDOW deny");
   shell("cmd notification allow_listener "+host.getTargetContext().getPackageName()+"/it.noxia.ambientplayer.MusicSessionService");
   shell("am broadcast -n app.morphe.android.apps.youtube.music/.FixtureReceiver --ez playing true");Thread.sleep(700);
   check("music_source_is_morphe_with_regular_youtube_also_installed",MusicSessionService.current!=null && "app.morphe.android.apps.youtube.music".equals(MusicSessionService.current.getPackageName()));
   shell("locksettings set-pin 1234");shell("input keyevent KEYCODE_SLEEP");Thread.sleep(500);shell("input keyevent KEYCODE_WAKEUP");Thread.sleep(600);
   locked=(MainActivity)host.startActivitySync(new Intent(host.getTargetContext(),MainActivity.class).putExtra("probe",true).putExtra("finish_after_ms",0).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));activate();Thread.sleep(400);
   check("native_keyguard_is_still_locked",host.getTargetContext().getSystemService(KeyguardManager.class).isKeyguardLocked());
   main(()->call(locked,"openMusic"));Thread.sleep(600);
   MusicLaunch.Request pending=(MusicLaunch.Request)get(locked,"pendingMusicLaunch");check("tap_resolves_morphe_session_player_and_waits_for_unlock",pending!=null && pending.sessionActivity()!=null && "app.morphe.android.apps.youtube.music".equals(pending.source()));
   main(()->call(locked,"closeIfUnlocked"));check("locked_callbacks_cannot_launch_or_close_player",!locked.isFinishing() && get(locked,"pendingMusicLaunch")!=null);
   shell("input keyevent KEYCODE_BACK");Thread.sleep(700);check("cancelling_native_unlock_clears_pending_launch",get(locked,"pendingMusicLaunch")==null && !locked.isFinishing());
   activate();main(()->call(locked,"openMusic"));Thread.sleep(600);
   shell("input text 1234");shell("input keyevent KEYCODE_ENTER");Thread.sleep(1200);
   check("successful_system_unlock_consumes_launch_and_closes_player",!host.getTargetContext().getSystemService(KeyguardManager.class).isKeyguardLocked() && locked.isFinishing() && get(locked,"pendingMusicLaunch")==null);
   check("youtube_music_fixture_is_foreground_after_unlock",shell("dumpsys activity activities").lines().anyMatch(l->l.contains("topResumedActivity") && l.contains("FixtureActivity")));
   main(()->{try{Method m=MainActivity.class.getDeclaredMethod("completeUnlock",String.class);m.setAccessible(true);m.invoke(locked,"duplicate_test_callback");}catch(Exception e){throw new RuntimeException(e);}});
   check("late_unlock_callback_cannot_reopen_music",get(locked,"pendingMusicLaunch")==null && locked.isFinishing());
   shell("input keyevent KEYCODE_SLEEP");Thread.sleep(600);shell("input keyevent KEYCODE_WAKEUP");Thread.sleep(600);
   locked=(MainActivity)host.startActivitySync(new Intent(host.getTargetContext(),MainActivity.class).putExtra("probe",true).putExtra("finish_after_ms",0).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));activate();Thread.sleep(400);
   main(()->{try{Field f=MusicSessionService.class.getDeclaredField("notificationIcons");f.setAccessible(true);set(f.get(null),"active",new NotificationIcons.Snapshot(List.of(new ColorDrawable(-1)),1,List.of(action)));Method m=MainActivity.class.getDeclaredMethod("openNotification",PendingIntent.class);m.setAccessible(true);m.invoke(locked,action);}catch(Exception e){throw new RuntimeException(e);}});Thread.sleep(600);
   check("notification_waits_for_native_unlock",get(locked,"pendingNotificationLaunch")!=null && get(locked,"pendingMusicLaunch")==null && host.getTargetContext().getSystemService(KeyguardManager.class).isKeyguardLocked());
   shell("input text 1234");shell("input keyevent KEYCODE_ENTER");Thread.sleep(1200);
   check("notification_opens_its_exact_destination_after_unlock",get(locked,"pendingNotificationLaunch")==null && locked.isFinishing() && shell("dumpsys activity activities").lines().anyMatch(l->l.contains("topResumedActivity") && l.contains("FixtureActivity")));
   result.putString("status","passed");
  }catch(Throwable failure){result.putString("status","failed");result.putString("error",failure.toString());}
  finally{
   main(()->{if(preview!=null)preview.finish();if(locked!=null)locked.finishAndRemoveTask();});
   try{shell("cmd notification disallow_listener "+host.getTargetContext().getPackageName()+"/it.noxia.ambientplayer.MusicSessionService");shell("cmd appops set "+host.getTargetContext().getPackageName()+" SYSTEM_ALERT_WINDOW allow");shell("locksettings clear --old 1234");}catch(Exception ignored){}
   try(var out=new FileOutputStream(new File(host.getTargetContext().getFilesDir(),"open-layout.json"))){out.write(new JSONObject().put("checks",checks).put("success","passed".equals(result.getString("status"))).put("error",result.getString("error","")).toString(2).getBytes());}catch(Exception ignored){}
  }
  host.finish("passed".equals(result.getString("status"))?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
 }
}
