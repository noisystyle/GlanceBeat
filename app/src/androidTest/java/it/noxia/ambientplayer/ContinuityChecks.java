package it.noxia.ambientplayer;
import android.app.*;
import android.content.*;
import android.os.*;
import android.graphics.*;
import android.view.*;
import android.view.accessibility.AccessibilityEvent;
import java.io.*;
import java.lang.reflect.*;
import org.json.*;
/** Emulator-only trust-layer continuity test with a transparent interruption fixture. */
final class ContinuityChecks {
 private final Instrumentation host;private final JSONArray checks=new JSONArray();private MainActivity activity;private String priorServices;
 private ContinuityChecks(Instrumentation h){host=h;}
 static void run(Instrumentation h){new ContinuityChecks(h).run();}
 private void main(Runnable r){host.runOnMainSync(r);}
 private Object get(Object o,String n){for(Class<?> c=o instanceof Class?(Class<?>)o:o.getClass();c!=null;c=c.getSuperclass())try{Field f=c.getDeclaredField(n);f.setAccessible(true);return f.get(o instanceof Class?null:o);}catch(NoSuchFieldException ignored){}catch(Exception e){throw new RuntimeException(e);}throw new RuntimeException(n);}
 private void check(String n,boolean b)throws Exception{if(!b)throw new AssertionError(n);checks.put(new JSONObject().put("name",n).put("passed",true));}
 private String shell(String s)throws Exception{try(var fd=host.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES).executeShellCommand(s);var in=new FileInputStream(fd.getFileDescriptor())){return new String(in.readAllBytes());}}
 private MainActivity current(){return (MainActivity)((java.lang.ref.WeakReference<?>)get(MainActivity.class,"surface")).get();}
 private void call(Object o,String n){try{Method m=o.getClass().getDeclaredMethod(n);m.setAccessible(true);m.invoke(o);}catch(Exception e){throw new RuntimeException(e);}}
 private void event(PlayerContinuityService service,String pkg){main(()->{AccessibilityEvent e=AccessibilityEvent.obtain(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED);e.setPackageName(pkg);e.setClassName("android.app.Activity");service.onAccessibilityEvent(e);e.recycle();});}
 private void run(){Bundle result=new Bundle();Bitmap before=null,during=null;
  try{
   if(!Build.FINGERPRINT.contains("generic") && !Build.MODEL.contains("sdk"))throw new AssertionError("Emulator only");
   priorServices=shell("settings get secure enabled_accessibility_services").trim();
   String ours=""+host.getTargetContext().getPackageName()+"/it.noxia.ambientplayer.PlayerContinuityService";
   String enabled=priorServices.equals("null") || priorServices.isEmpty()?ours:priorServices+":"+ours;
   shell("settings put secure enabled_accessibility_services "+enabled);shell("settings put secure accessibility_enabled 1");
   long until=SystemClock.uptimeMillis()+5000;while(!PlayerContinuityService.connected() && SystemClock.uptimeMillis()<until)Thread.sleep(50);
   check("continuity_service_connected_on_emulator",PlayerContinuityService.connected());
   PlayerContinuityService service=(PlayerContinuityService)((java.lang.ref.WeakReference<?>)get(PlayerContinuityService.class,"binding")).get();
   check("service_has_no_screen_content_gesture_or_key_filter_capability",service.getServiceInfo().getCapabilities()==0);
   main(()->PlayerPreferences.setEnabled(host.getTargetContext(),true));shell("cmd appops set "+host.getTargetContext().getPackageName()+" SYSTEM_ALERT_WINDOW allow");shell("cmd notification allow_listener "+host.getTargetContext().getPackageName()+"/it.noxia.ambientplayer.MusicSessionService");Thread.sleep(700);
   shell("am broadcast -n app.morphe.android.apps.youtube.music/.FixtureReceiver --ez playing true");shell("locksettings set-pin 1234");shell("input keyevent KEYCODE_SLEEP");Thread.sleep(2400);
   activity=current();check("locked_player_automatically_created",activity!=null && !activity.isFinishing());
   main(activity::prepareContinuity);Thread.sleep(300);
   WindowManager.LayoutParams params=(WindowManager.LayoutParams)get(service,"params");
   check("prewarmed_trusted_window_is_invisible_and_not_touchable",params!=null && params.type==WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY && params.alpha==0 && (params.flags&WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)!=0);
   before=host.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES).takeScreenshot();
   shell("am start -n it.noxia.ambientplayer.stabilityfixture/.TransparentActivity");Thread.sleep(400);
   check("transparent_external_activity_keeps_same_player_under_continuity",PlayerContinuityService.covering(activity) && current()==activity && !activity.isFinishing());
   params=(WindowManager.LayoutParams)get(service,"params");check("continuity_does_not_take_focus_from_copy_activity",(params.flags&WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)!=0);
   during=host.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES).takeScreenshot();
   long difference=0;int count=0;for(int y=before.getHeight()/3;y<before.getHeight()*2/3;y+=8)for(int x=before.getWidth()/4;x<before.getWidth()*3/4;x+=8){int a=before.getPixel(x,y),b=during.getPixel(x,y);difference+=Math.abs(Color.red(a)-Color.red(b))+Math.abs(Color.green(a)-Color.green(b))+Math.abs(Color.blue(a)-Color.blue(b));count+=3;}
   check("interruption_keeps_player_pixels_instead_of_native_keyguard",difference/(double)count<3);
   check("continuity_never_unlocks_keyguard",host.getTargetContext().getSystemService(KeyguardManager.class).isKeyguardLocked());
   Thread.sleep(2500);until=SystemClock.uptimeMillis()+2500;while(PlayerContinuityService.covering(activity) && SystemClock.uptimeMillis()<until)Thread.sleep(30);check("same_player_returns_and_continuity_releases_after_frame_commit",current()==activity && !activity.isFinishing() && !PlayerContinuityService.covering(activity));
   shell("am start -n it.noxia.ambientplayer.stabilityfixture/.TransparentActivity");Thread.sleep(350);check("repeated_interruptions_can_be_covered",PlayerContinuityService.covering(activity));
   event(service,"com.samsung.android.incallui");check("incoming_call_window_takes_precedence",!PlayerContinuityService.covering(activity));Thread.sleep(2600);
   main(()->{((PlayerModeController)get(activity,"modes")).activate(SystemClock.uptimeMillis());call(activity,"applyMode");call(activity,"requestSystemUnlock");});Thread.sleep(500);
   check("native_authentication_has_no_continuity_window",!PlayerContinuityService.covering(activity) && get(service,"image")==null);
   check("native_authentication_keeps_pin_security",host.getTargetContext().getSystemService(KeyguardManager.class).isKeyguardLocked());
   result.putString("status","passed");
  }catch(Throwable failure){result.putString("status","failed");result.putString("error",failure.toString());}
  finally{
   if(before!=null)before.recycle();if(during!=null)during.recycle();
   main(()->{if(activity!=null)activity.finishAndRemoveTask();});
   try{shell("settings put secure enabled_accessibility_services "+(priorServices==null || priorServices.equals("null")?"''":priorServices));shell("locksettings clear --old 1234");}catch(Exception ignored){}
   try(var out=new FileOutputStream(new File(host.getTargetContext().getFilesDir(),"continuity.json"))){out.write(new JSONObject().put("checks",checks).put("success","passed".equals(result.getString("status"))).put("error",result.getString("error","")).toString(2).getBytes());}catch(Exception ignored){}
  }
  host.finish("passed".equals(result.getString("status"))?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
 }
}
