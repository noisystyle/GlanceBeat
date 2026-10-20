package it.noxia.ambientplayer;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import org.json.*;

/** Emulator-only: verify the composed window, not just alpha on foreground widgets. */
final class WindowEntranceChecks {
 private final Instrumentation host;private final JSONArray checks=new JSONArray(),frames=new JSONArray();
 private PreviewActivity behind;private MainActivity player;private WindowEntrance synthetic;private String originalScale;
 private WindowEntranceChecks(Instrumentation h){host=h;}
 static void run(Instrumentation h){new WindowEntranceChecks(h).run();}
 private void main(Runnable r){host.runOnMainSync(r);}
 private Object get(Object o,String name){for(Class<?> c=o.getClass();c!=null;c=c.getSuperclass())try{Field f=c.getDeclaredField(name);f.setAccessible(true);return f.get(o);}catch(NoSuchFieldException ignored){}catch(Exception e){throw new RuntimeException(e);}throw new RuntimeException(name);}
 private void check(String n,boolean v)throws Exception{if(!v)throw new AssertionError(n);checks.put(new JSONObject().put("name",n).put("passed",true));}
 private String shell(String command)throws Exception{try(var fd=host.getUiAutomation().executeShellCommand(command);var in=new FileInputStream(fd.getFileDescriptor())){return new String(in.readAllBytes());}}
 private WindowEntrance entrance(){return (WindowEntrance)get(player,"entrance");}
 private int screenshot(String name)throws Exception{
  Bitmap b=host.getUiAutomation().takeScreenshot();int color=b.getPixel(30,b.getHeight()/2);
  try(var out=new FileOutputStream(new File(host.getTargetContext().getFilesDir(),name))){b.compress(Bitmap.CompressFormat.PNG,100,out);}b.recycle();return color;
 }
 private void run(){Bundle result=new Bundle();
  try{
   if(!Build.FINGERPRINT.contains("generic") && !Build.MODEL.contains("sdk"))throw new AssertionError("Emulator only");
   originalScale=shell("settings get global animator_duration_scale").trim();shell("settings put global animator_duration_scale 3");Thread.sleep(200);
   main(()->PlayerPreferences.store(host.getTargetContext()).edit().putBoolean(PlayerPreferences.ENABLED,true).putBoolean(PlayerPreferences.DRIVE_ENABLED,false).commit());
   behind=(PreviewActivity)host.startActivitySync(new Intent(host.getTargetContext(),PreviewActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
   main(()->{behind.unregisterReceiver((BroadcastReceiver)get(behind,"lockReceiver"));try{Field f=MainActivity.class.getDeclaredField("receiverRegistered");f.setAccessible(true);f.set(behind,false);}catch(Exception e){throw new RuntimeException(e);}behind.setShowWhenLocked(true);behind.setContentView(new View(behind){@Override protected void onDraw(Canvas c){c.drawColor(Color.rgb(180,100,45));}});});Thread.sleep(150);
   check("preview_does_not_acquire_entrance",get(behind,"entrance")==null);
   shell("locksettings set-pin 1234");shell("input keyevent KEYCODE_SLEEP");Thread.sleep(300);shell("input keyevent KEYCODE_WAKEUP");Thread.sleep(300);
   check("neutral_underlay_is_visible",Color.red(screenshot("entrance-before.png"))>140);
   player=(MainActivity)host.startActivitySync(new Intent(host.getTargetContext(),MainActivity.class).putExtra("probe",true).putExtra("wake",true).putExtra("keep",true).putExtra("finish_after_ms",0).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
   float first=player.getWindow().getAttributes().alpha;
   check("new_player_window_starts_transparent",first<1 && entrance().pending());
   check("entrance_temporarily_requests_smooth_refresh",player.getWindow().getAttributes().preferredRefreshRate==60);
   int early=screenshot("entrance-during.png");frames.put(new JSONObject().put("screenshot_pixel_red",Color.red(early)).put("alpha_after_screenshot",player.getWindow().getAttributes().alpha));
   check("underlay_is_composed_through_entire_player_window",Color.red(early)>2 && Color.red(early)<180);
   float previous=0;boolean monotonic=true;long until=SystemClock.uptimeMillis()+3500;
   while(entrance().pending() && SystemClock.uptimeMillis()<until){float a=player.getWindow().getAttributes().alpha;if(a+0.001f<previous)monotonic=false;previous=a;frames.put(a);Thread.sleep(25);}
   check("compositor_alpha_never_moves_backwards",monotonic);
   check("entry_finishes_with_full_opacity",!entrance().pending() && !entrance().running() && player.getWindow().getAttributes().alpha==1);
   check("activity_converts_to_opaque_at_rest",entrance().opaque());
   check("settled_aod_returns_to_one_hz",player.getWindow().getAttributes().preferredRefreshRate==1);
   check("keyguard_is_never_dismissed_by_entry",host.getTargetContext().getSystemService(KeyguardManager.class).isKeyguardLocked());
   check("opaque_background_has_no_underlay_leak",Color.red(screenshot("entrance-after.png"))<2);
   main(()->{player.setTranslucent(true);synthetic=new WindowEntrance(player,()->{});synthetic.attach(new View(player));});Thread.sleep(1700);
   check("missing_first_draw_cannot_leave_an_invisible_window",!synthetic.pending() && synthetic.opaque() && player.getWindow().getAttributes().alpha==1);
   main(()->{player.setTranslucent(true);synthetic=new WindowEntrance(player,()->{});synthetic.attach(new View(player));synthetic.finish();synthetic.finish();});
   check("early_finish_is_idempotent_and_opaque",!synthetic.pending() && synthetic.opaque() && player.getWindow().getAttributes().alpha==1);
   main(()->{synthetic=new WindowEntrance(player,()->{});synthetic.attach(new View(player));synthetic.release();});
   check("destroyed_entry_has_no_animation_or_pending_fallback",!synthetic.pending() && !synthetic.running() && !((Handler)get(synthetic,"handler")).hasCallbacks((Runnable)get(synthetic,"fallback")));
   result.putString("status","passed");
  }catch(Throwable failure){result.putString("status","failed");result.putString("error",failure.toString());}
  finally{
   main(()->{if(synthetic!=null)synthetic.release();if(player!=null)player.finishAndRemoveTask();if(behind!=null)behind.finish();});
   try{shell("settings put global animator_duration_scale "+(originalScale==null || originalScale.equals("null")?"1":originalScale));shell("locksettings clear --old 1234");}catch(Exception ignored){}
   try(var out=new FileOutputStream(new File(host.getTargetContext().getFilesDir(),"entrance.json"))){out.write(new JSONObject().put("checks",checks).put("window_alpha_samples",frames).put("success","passed".equals(result.getString("status"))).put("error",result.getString("error","")).toString(2).getBytes());}catch(Exception ignored){}
  }
  host.finish("passed".equals(result.getString("status"))?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
 }
}
