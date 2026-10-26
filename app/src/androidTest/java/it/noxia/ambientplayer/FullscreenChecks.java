package it.noxia.ambientplayer;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import java.io.*;
import java.lang.reflect.Field;
import org.json.*;

/** Disposable-emulator regression: check actual screen bounds on pre-edge-to-edge Android. */
final class FullscreenChecks {
    private final Instrumentation host;
    private final JSONArray checks=new JSONArray(),windows=new JSONArray();
    private int failures;
    private MainActivity activity;
    private FullscreenChecks(Instrumentation host){this.host=host;}
    static void run(Instrumentation host){new FullscreenChecks(host).run();}
    private void main(Runnable task){host.runOnMainSync(task);}
    private void shell(String command)throws Exception{
        try(var fd=host.getUiAutomation().executeShellCommand(command);var in=new FileInputStream(fd.getFileDescriptor())){
            byte[] buffer=new byte[4096];while(in.read(buffer)!=-1){}
        }
    }
    private void check(String name,boolean passed)throws Exception{
        checks.put(new JSONObject().put("name",name).put("passed",passed));
        if(!passed)failures++;
    }
    private Object field(String name)throws Exception{
        Field field=MainActivity.class.getDeclaredField(name);field.setAccessible(true);return field.get(activity);
    }
    private void inspect(String name)throws Exception{
        Thread.sleep(900);
        View root=(View)field("playerRoot");
        long readyBy=SystemClock.uptimeMillis()+5000;
        while((!activity.hasWindowFocus() || root.getWidth()==0 || root.getRootWindowInsets()==null)
                && SystemClock.uptimeMillis()<readyBy)Thread.sleep(50);
        int[] position=new int[2];Rect[] screen=new Rect[1];WindowInsets[] insets=new WindowInsets[1];
        main(()->{
            root.getLocationOnScreen(position);
            screen[0]=activity.getWindowManager().getCurrentWindowMetrics().getBounds();
            insets[0]=root.getRootWindowInsets();
        });
        windows.put(new JSONObject().put("name",name).put("screen",screen[0].toShortString())
            .put("x",position[0]).put("y",position[1]).put("width",root.getWidth()).put("height",root.getHeight())
            .put("cutout",String.valueOf(insets[0].getDisplayCutout())));
        Bitmap image=host.getUiAutomation().takeScreenshot();
        try(var out=new FileOutputStream(new File(host.getTargetContext().getFilesDir(),"fullscreen-"+name+".png"))){image.compress(Bitmap.CompressFormat.PNG,100,out);}
        image.recycle();
        check(name+"_player_is_foreground",activity.hasWindowFocus());
        check(name+"_background_reaches_all_screen_edges",position[0]==screen[0].left && position[1]==screen[0].top
            && root.getWidth()==screen[0].width() && root.getHeight()==screen[0].height());
        check(name+"_system_bars_hidden",!insets[0].isVisible(WindowInsets.Type.statusBars()) && !insets[0].isVisible(WindowInsets.Type.navigationBars()));
        PlayerView player=(PlayerView)field("player");
        check(name+"_content_retains_camera_safe_inset",player.contentSafeTop()==insets[0].getInsetsIgnoringVisibility(WindowInsets.Type.displayCutout()).top);
    }
    private void activate()throws Exception{
        PlayerModeController modes=(PlayerModeController)field("modes");
        main(()->{modes.activate(SystemClock.uptimeMillis());try{
            var apply=MainActivity.class.getDeclaredMethod("applyMode");apply.setAccessible(true);apply.invoke(activity);
        }catch(Exception failure){throw new RuntimeException(failure);}});
    }
    private void run(){
        Bundle result=new Bundle();
        try{
            if(!Build.FINGERPRINT.contains("generic") && !Build.MODEL.contains("sdk"))throw new AssertionError("Disposable emulator only");
            shell("locksettings clear --old 1234");
            main(()->PlayerPreferences.store(host.getTargetContext()).edit().putBoolean(PlayerPreferences.ENABLED,true).putBoolean(PlayerPreferences.DRIVE_ENABLED,false).commit());
            shell("locksettings set-pin 1234");shell("input keyevent KEYCODE_SLEEP");Thread.sleep(1500);
            shell("input keyevent KEYCODE_WAKEUP");Thread.sleep(500);
            long lockedBy=SystemClock.uptimeMillis()+5000;
            while(!host.getTargetContext().getSystemService(KeyguardManager.class).isKeyguardLocked()
                    && SystemClock.uptimeMillis()<lockedBy)Thread.sleep(50);
            check("native_keyguard_locked_before_launch",host.getTargetContext().getSystemService(KeyguardManager.class).isKeyguardLocked());
            activity=(MainActivity)host.startActivitySync(new Intent(host.getTargetContext(),MainActivity.class)
                .putExtra("probe",true).putExtra("wake",true).putExtra("keep",true).putExtra("finish_after_ms",0).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            inspect("locked-ambient");activate();inspect("locked-active");
            check("fullscreen_preserves_native_keyguard",host.getTargetContext().getSystemService(KeyguardManager.class).isKeyguardLocked());
            if(failures>0)throw new AssertionError(failures+" fullscreen checks failed");
            result.putString("status","passed");
        }catch(Throwable failure){result.putString("status","failed");result.putString("error",failure.toString());}
        finally{
            main(()->{if(activity!=null)activity.finishAndRemoveTask();});
            try{shell("locksettings clear --old 1234");shell("locksettings set-disabled true");
                shell("input keyevent KEYCODE_WAKEUP");shell("wm dismiss-keyguard");}catch(Exception ignored){}
            try(var out=new FileOutputStream(new File(host.getTargetContext().getFilesDir(),"fullscreen.json"))){
                out.write(new JSONObject().put("api",Build.VERSION.SDK_INT).put("checks",checks).put("windows",windows)
                    .put("success","passed".equals(result.getString("status"))).put("error",result.getString("error","")).toString(2).getBytes());
            }catch(Exception ignored){}
        }
        host.finish("passed".equals(result.getString("status"))?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
