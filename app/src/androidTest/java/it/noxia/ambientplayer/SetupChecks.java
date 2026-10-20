package it.noxia.ambientplayer;

import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

/** Real permission changes and lifecycle checks, restricted to disposable emulators. */
final class SetupChecks {
    private final Instrumentation host;
    private final JSONArray checks=new JSONArray();
    private SetupActivity setup;
    private SettingsActivity settings;
    private SetupChecks(Instrumentation host){this.host=host;}
    static void run(Instrumentation host){new SetupChecks(host).run();}
    private void main(Runnable action){host.runOnMainSync(action);}
    private void shell(String command)throws Exception{
        try(var fd=host.getUiAutomation().executeShellCommand(command);var in=new FileInputStream(fd.getFileDescriptor())){in.readAllBytes();}
    }
    private void music(boolean granted)throws Exception{shell("cmd notification "+(granted?"allow_listener ":"disallow_listener ")+host.getTargetContext().getPackageName()+"/it.noxia.ambientplayer.MusicSessionService");}
    private void overlay(boolean granted)throws Exception{shell("cmd appops set "+host.getTargetContext().getPackageName()+" SYSTEM_ALERT_WINDOW "+(granted?"allow":"deny"));}
    private void check(String name,boolean ok)throws Exception{if(!ok)throw new AssertionError(name);checks.put(new JSONObject().put("name",name).put("passed",true));}
    private boolean next(){return setup.findViewById(R.id.setup_next).isEnabled();}
    private void refresh(){main(setup::refresh);}
    private void clickNext(){main(()->setup.findViewById(R.id.setup_next).performClick());}
    private boolean step(int step){return ((TextView)setup.findViewById(R.id.setup_step)).getText().toString().equals(setup.getString(R.string.setup_step,step,2));}
    private void save(String name)throws Exception{
        Bitmap[] bitmap={null};main(()->{var view=setup.findViewById(R.id.setup_root);bitmap[0]=Bitmap.createBitmap(view.getWidth(),view.getHeight(),Bitmap.Config.ARGB_8888);view.draw(new Canvas(bitmap[0]));});
        try(var out=new FileOutputStream(new File(host.getTargetContext().getFilesDir(),name))){bitmap[0].compress(Bitmap.CompressFormat.PNG,100,out);}bitmap[0].recycle();
    }
    private SetupActivity waitForSetup(Instrumentation.ActivityMonitor monitor)throws Exception{
        var activity=host.waitForMonitorWithTimeout(monitor,5000);host.removeMonitor(monitor);
        if(!(activity instanceof SetupActivity))throw new AssertionError("Setup did not open");return (SetupActivity)activity;
    }
    private void run(){Bundle result=new Bundle();
        try{
            if(!Build.FINGERPRINT.contains("generic") && !Build.MODEL.contains("sdk"))throw new AssertionError("Emulator only");
            main(()->PlayerPreferences.setEnabled(host.getTargetContext(),false));music(false);overlay(false);
            var monitor=host.addMonitor(SetupActivity.class.getName(),null,false);
            main(()->host.getTargetContext().startActivity(new Intent(host.getTargetContext(),SettingsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)));
            setup=waitForSetup(monitor);Thread.sleep(250);
            check("launcher_requires_setup_when_access_is_missing",step(1) && !SetupPermissions.ready(setup));
            check("notification_step_next_is_disabled",!next());clickNext();check("programmatic_click_cannot_bypass_notification_access",step(1));
            check("settings_controls_are_not_exposed",setup.findViewById(R.id.player_enabled)==null);
            Intent[] permissionIntent={null};
            Instrumentation.ActivityMonitor blocker=new Instrumentation.ActivityMonitor(){
                @Override public Instrumentation.ActivityResult onStartActivity(Intent intent){
                    if(intent.getAction()!=null && intent.getAction().startsWith("android.settings.")){permissionIntent[0]=new Intent(intent);return new Instrumentation.ActivityResult(android.app.Activity.RESULT_CANCELED,null);}return null;
                }
            };
            host.addMonitor(blocker);
            try{main(()->setup.findViewById(R.id.setup_permission).performClick());}
            finally{host.removeMonitor(blocker);}
            check("notification_button_opens_native_permission_screen",permissionIntent[0]!=null && Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS.equals(permissionIntent[0].getAction()));
            check("permission_button_does_not_mark_access_granted",!next() && !SetupPermissions.music(setup));
            overlay(true);refresh();check("overlay_alone_cannot_skip_notification_step",step(1) && !next());overlay(false);
            save("setup-step1.png");
            music(true);refresh();check("android_grant_enables_notification_step",next());clickNext();check("continue_opens_overlay_step",step(2) && !next());
            permissionIntent[0]=null;host.addMonitor(blocker);
            try{main(()->setup.findViewById(R.id.setup_permission).performClick());}
            finally{host.removeMonitor(blocker);}
            check("overlay_button_opens_own_app_settings",permissionIntent[0]!=null && Settings.ACTION_MANAGE_OVERLAY_PERMISSION.equals(permissionIntent[0].getAction()) && ("package:"+setup.getPackageName()).equals(permissionIntent[0].getDataString()));
            overlay(true);refresh();check("both_android_grants_enable_completion",next() && SetupPermissions.ready(setup));
            overlay(false);clickNext();check("stale_enabled_button_rechecks_permission",!next() && !setup.isFinishing());
            overlay(true);refresh();music(false);refresh();check("revoked_notification_access_returns_to_first_step",step(1) && !next());
            music(true);refresh();clickNext();check("notification_restored_returns_to_overlay_step",step(2));
            var rotation=host.addMonitor(SetupActivity.class.getName(),null,false);main(setup::recreate);setup=waitForSetup(rotation);
            check("recreation_preserves_setup_step",step(2) && next());Thread.sleep(250);save("setup-step2.png");
            check("optional_accessibility_and_bluetooth_do_not_block_completion",SetupPermissions.ready(setup) && !CarBluetooth.permitted(setup));
            var settingsMonitor=host.addMonitor(SettingsActivity.class.getName(),null,false);clickNext();
            settings=(SettingsActivity)host.waitForMonitorWithTimeout(settingsMonitor,5000);host.removeMonitor(settingsMonitor);
            check("completion_opens_settings_after_real_grants",settings!=null && settings.findViewById(R.id.player_enabled)!=null && setup.isFinishing());
            overlay(false);var revoked=host.addMonitor(SetupActivity.class.getName(),null,false);main(settings::recreate);setup=waitForSetup(revoked);
            check("revocation_reopens_setup_on_next_settings_entry",step(2) && !next());
            check("setup_help_explains_arrow_and_swipe",setup.getString(R.string.unlock_body).contains(setup.getResources().getConfiguration().getLocales().get(0).getLanguage().equals("it")?"tocca la freccia":"tap the upward arrow"));
            result.putString("status","passed");
        }catch(Throwable error){result.putString("status","failed");result.putString("error",error.toString());}
        finally{
            if(setup!=null)main(setup::finish);if(settings!=null && !settings.isDestroyed())main(settings::finish);
            try{music(false);overlay(false);}catch(Exception ignored){}
            try(var out=new FileOutputStream(new File(host.getTargetContext().getFilesDir(),"setup.json"))){out.write(new JSONObject().put("checks",checks).put("success","passed".equals(result.getString("status"))).put("error",result.getString("error","")).toString(2).getBytes());}catch(Exception ignored){}
            host.finish("passed".equals(result.getString("status"))?-1:0,result);
        }
    }
}
