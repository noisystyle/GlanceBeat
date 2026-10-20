package it.noxia.ambientplayer;

import android.app.Instrumentation;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.MotionEvent;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;

/** Verifies the public edition has no accessory page, listener or alternate swipe destination. */
final class AccessoryChecks {
    private static Object field(Object object, String name) throws Exception {
        for(Class<?> type=object.getClass();type!=null;type=type.getSuperclass()) {
            try {var f=type.getDeclaredField(name);f.setAccessible(true);return f.get(object);}
            catch(NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(name);
    }
    static void run(Instrumentation host) {
        Bundle result=new Bundle();JSONArray checks=new JSONArray();PreviewActivity activity=null;
        try {
            if(!Build.FINGERPRINT.contains("generic") && !Build.MODEL.contains("sdk"))throw new AssertionError("Emulator only");
            var context=host.getTargetContext();
            check(checks,"public_application_id_is_separate",context.getPackageName().equals("it.noxia.glancebeat"));
            AccessorySupport support=new AccessorySupport(context,()->{throw new AssertionError("Unexpected accessory callback");});
            support.start();
            check(checks,"no_route_observer_is_started",!support.listening() && !support.connected() && support.name().isEmpty());
            support.stop();
            check(checks,"no_notification_battery_parser",AccessorySupport.readBattery(null,null)==null);
            check(checks,"no_special_notification_filter",!AccessorySupport.hidesNotification("other.app"));
            check(checks,"no_accessory_panel_factory",AccessorySupport.createPanel(context,0,()->{},()->{})==null);
            activity=(PreviewActivity)host.startActivitySync(new Intent(context,PreviewActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            PlayerSurface surface=(PlayerSurface)field(activity,"widgets");PlayerView scene=(PlayerView)field(activity,"player");
            check(checks,"no_accessory_view_is_inflated",field(surface,"accessory")==null);
            check(checks,"no_accessory_reading_or_connection",!scene.accessoryConnected() && scene.accessoryReading()==null);
            host.runOnMainSync(surface::openAccessory);
            check(checks,"aod_has_no_accessory_destination",!surface.accessoryOpen());
            PreviewActivity preview=activity;
            host.runOnMainSync(()->{try{
                ((PlayerModeController)field(preview,"modes")).toggle(SystemClock.uptimeMillis());
                var apply=MainActivity.class.getDeclaredMethod("applyMode");apply.setAccessible(true);apply.invoke(preview);
            }catch(Exception e){throw new RuntimeException(e);}});
            Thread.sleep(550);
            host.runOnMainSync(surface::openAccessory);
            check(checks,"active_has_no_accessory_destination",!surface.accessoryOpen());
            boolean[] intercepted={false};host.runOnMainSync(()->{
                long now=SystemClock.uptimeMillis();float y=surface.getHeight()*.78f;
                MotionEvent down=MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,surface.getWidth()*.8f,y,0);
                MotionEvent move=MotionEvent.obtain(now,now+100,MotionEvent.ACTION_MOVE,surface.getWidth()*.15f,y,0);
                surface.onInterceptTouchEvent(down);intercepted[0]=surface.onInterceptTouchEvent(move);down.recycle();move.recycle();
            });
            check(checks,"opposite_swipe_has_no_hidden_page",!intercepted[0] && !surface.auxiliaryOpen());
            host.runOnMainSync(()->{surface.closeAuxiliary(false);surface.invalidateStatus();});
            check(checks,"queue_settlement_handles_missing_panel",!surface.queueAnimating());
            check(checks,"public_settings_name",context.getString(R.string.app_name).equals("GlanceBeat"));
            result.putString("status","passed");
        }catch(Throwable error){result.putString("status","failed");result.putString("error",error.toString());}
        finally {
            if(activity!=null){PreviewActivity preview=activity;host.runOnMainSync(preview::finish);}
            try(var out=new FileOutputStream(new File(host.getTargetContext().getFilesDir(),"accessory.json"))){
                out.write(new JSONObject().put("checks",checks).put("success","passed".equals(result.getString("status"))).put("error",result.getString("error","")).toString(2).getBytes());
            }catch(Exception ignored){}
            host.finish("passed".equals(result.getString("status"))?-1:0,result);
        }
    }
    private static void check(JSONArray checks,String name,boolean ok)throws Exception{
        if(!ok)throw new AssertionError(name);checks.put(new JSONObject().put("name",name).put("passed",true));
    }
}
