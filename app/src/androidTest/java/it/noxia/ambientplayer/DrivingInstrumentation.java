package it.noxia.ambientplayer;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import android.graphics.Bitmap;
import org.json.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;

/** Emulator-only UI and window integration. Synthetic Bluetooth never enters the shipped app. */
public final class DrivingInstrumentation extends Instrumentation {
    private final JSONArray checks = new JSONArray();
    private boolean frostOnly,localizationOnly,setupOnly;
    private boolean accessoryOnly,commitOnly,openLayoutOnly,entranceOnly,stabilityOnly,continuityOnly,columnsOnly,bitchordOnly;
    private final String car = "12:34:56:78:9A:BC";
    private boolean youtubeShuffleOnly,catalogOnly,officialLocalOnly,musicLaunchOnly;
    private MainActivity player;
    private SettingsActivity settings;
    private CarBluetooth monitor;
    private final List<String> errors = new ArrayList<>();
    @Override public void onCreate(Bundle args) { super.onCreate(args);setupOnly=args!=null && "true".equals(args.getString("setup"));localizationOnly=args!=null && "true".equals(args.getString("localization"));musicLaunchOnly=args!=null && "true".equals(args.getString("music_launch"));officialLocalOnly=args!=null && "true".equals(args.getString("official_local"));catalogOnly=args!=null && "true".equals(args.getString("catalog"));youtubeShuffleOnly=args!=null && "true".equals(args.getString("youtube_shuffle")); bitchordOnly=args!=null && "true".equals(args.getString("bitchord"));columnsOnly=args!=null && "true".equals(args.getString("columns"));continuityOnly=args!=null && "true".equals(args.getString("continuity"));stabilityOnly=args!=null && "true".equals(args.getString("stability"));entranceOnly=args!=null && "true".equals(args.getString("entrance"));frostOnly=args!=null && "true".equals(args.getString("carousel_frost")); accessoryOnly=args!=null && "true".equals(args.getString("accessory"));openLayoutOnly=args!=null && "true".equals(args.getString("open_layout"));commitOnly=args!=null && "true".equals(args.getString("carousel_commit")); start(); }
    private void check(String name, boolean ok) throws Exception {
        if (!ok) throw new AssertionError(name);
        checks.put(new JSONObject().put("name",name).put("passed",true));
        android.util.Log.i("DrivingTest",name+": PASS");
    }
    private void main(Runnable task) { runOnMainSync(task); }
    private Object field(Object object,String name) {
        try { Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object); }
        catch(Exception e) { throw new RuntimeException(e); }
    }
    private void set(Object object,String name,Object value) {
        try { Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);f.set(object,value); }
        catch(Exception e) { throw new RuntimeException(e); }
    }
    private void call(Object object,String method) {
        try { Method m=object.getClass().getDeclaredMethod(method);m.setAccessible(true);m.invoke(object); }
        catch(Exception e) { throw new RuntimeException(e); }
    }
    private void shell(String command) throws Exception {
        try (var fd=getUiAutomation().executeShellCommand(command);var in=new FileInputStream(fd.getFileDescriptor())) { in.readAllBytes(); }
    }
    private void capture(String name) throws Exception {
        Thread.sleep(350);
        Bitmap image=getUiAutomation().takeScreenshot();
        try (var out=new FileOutputStream(new File(getTargetContext().getFilesDir(),name))) { image.compress(Bitmap.CompressFormat.PNG,100,out); }
        image.recycle();
    }
    private PlayerModeController modes() { return (PlayerModeController)field(player,"modes"); }
    private void snapshot(boolean connected, boolean charging) {
        main(() -> {
            monitor.stop();
            set(monitor,"permission",true);set(monitor,"available",true);set(monitor,"bluetoothOn",true);set(monitor,"charging",charging);
            set(monitor,"devices",List.of(new CarBluetooth.Device(car,"Auto di prova",connected)));
            call(player,"updateDrivingMode");
        });
    }
    private void pref(String key,boolean value) { main(() -> PlayerPreferences.store(getTargetContext()).edit().putBoolean(key,value).commit()); }
    private boolean screenKept() { return (player.getWindow().getAttributes().flags & WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)!=0; }
    @Override public void onStart() {
        if(setupOnly){SetupChecks.run(this);return;}
        if(localizationOnly){LocalizationChecks.run(this);return;}
        if(musicLaunchOnly){MusicLaunchChecks.run(this);return;}
        if(officialLocalOnly){OfficialLocalChecks.run(this);return;}
        if(catalogOnly){MusicCatalogChecks.run(this);return;}
        if(youtubeShuffleOnly){YoutubeShuffleChecks.run(this);return;}
        if(bitchordOnly){BitchordChecks.run(this);return;}
        if(columnsOnly){PlayerColumnsChecks.run(this);return;}
        if(continuityOnly){ContinuityChecks.run(this);return;}
        if(stabilityOnly){StabilityChecks.run(this);return;}
        if(entranceOnly){WindowEntranceChecks.run(this);return;}
        if(openLayoutOnly){PlayerOpenLayoutChecks.run(this);return;}
        if(commitOnly){CarouselCommitChecks.run(this);return;}
        if(frostOnly){CarouselFrostChecks.run(this);return;}
        if(accessoryOnly){AccessoryChecks.run(this);return;}
        Bundle result = new Bundle();
        try {
            if (!Build.FINGERPRINT.contains("generic") && !Build.MODEL.contains("sdk")) throw new AssertionError("Emulator only");
            shell("locksettings clear --old 1234");shell("input keyevent KEYCODE_WAKEUP");shell("wm dismiss-keyguard");
            main(() -> PlayerPreferences.store(getTargetContext()).edit().putBoolean(PlayerPreferences.ENABLED,false)
                .putBoolean(PlayerPreferences.DRIVE_ENABLED,false).remove(PlayerPreferences.DRIVE_ADDRESS).remove(PlayerPreferences.DRIVE_NAME)
                .putBoolean(PlayerPreferences.DRIVE_CHARGING,false).remove(PlayerPreferences.BT_PERMISSION_ASKED).commit());
            shell("cmd appops set "+getTargetContext().getPackageName()+" SYSTEM_ALERT_WINDOW allow");
            shell("cmd notification allow_listener "+getTargetContext().getPackageName()+"/it.noxia.ambientplayer.MusicSessionService");
            settings=(SettingsActivity)startActivitySync(new Intent(getTargetContext(),SettingsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            check("settings_has_driving_controls",settings.findViewById(R.id.drive_enabled)!=null && settings.findViewById(R.id.drive_device)!=null);
            check("driving_defaults_off",!PlayerPreferences.driveEnabled(settings) && PlayerPreferences.driveAddress(settings).isEmpty());
            CarBluetooth pickerSource=(CarBluetooth)field(settings,"carBluetooth");
            final CarDevicePicker[] picker=new CarDevicePicker[1];
            main(() -> {
                pickerSource.stop();set(pickerSource,"permission",true);set(pickerSource,"available",true);set(pickerSource,"bluetoothOn",true);
                set(pickerSource,"devices",List.of(new CarBluetooth.Device(car,"Auto di prova",true),new CarBluetooth.Device("AA:BB:CC:DD:EE:FF","Cuffie di prova",false)));
                picker[0]=new CarDevicePicker(settings,pickerSource,()->{});picker[0].show();
            });
            capture("driving-picker.png");
            View pickerContent=(View)field(picker[0],"content");
            check("picker_shows_paired_names_and_connection",((LinearLayout)pickerContent.findViewById(R.id.car_picker_devices)).getChildCount()==2);
            main(() -> ((LinearLayout)pickerContent.findViewById(R.id.car_picker_devices)).getChildAt(0).performClick());
            check("picker_persists_selected_identity",PlayerPreferences.driveAddress(settings).equals(car) && PlayerPreferences.driveName(settings).equals("Auto di prova"));
            main(() -> { ((com.google.android.material.materialswitch.MaterialSwitch)settings.findViewById(R.id.drive_enabled)).setChecked(true);
                ((com.google.android.material.materialswitch.MaterialSwitch)settings.findViewById(R.id.drive_charging)).setChecked(true); });
            check("settings_persists_switches",PlayerPreferences.driveEnabled(settings) && PlayerPreferences.driveCharging(settings));
            main(() -> { View section=settings.findViewById(R.id.drive_enabled);((androidx.core.widget.NestedScrollView)settings.findViewById(R.id.settings_root)).smoothScrollTo(0,((View)section.getParent().getParent()).getTop()-30); });
            capture("driving-settings.png");
            main(settings::finish);
            pref(PlayerPreferences.DRIVE_ENABLED,false);pref(PlayerPreferences.DRIVE_CHARGING,false);
            main(() -> PlayerPreferences.setEnabled(getTargetContext(),true));
            shell("locksettings set-pin 1234");shell("input keyevent KEYCODE_SLEEP");Thread.sleep(1800);
            long until=SystemClock.uptimeMillis()+5000;
            while(!MainActivity.hasSurface() && SystemClock.uptimeMillis()<until)Thread.sleep(100);
            Field surface=MainActivity.class.getDeclaredField("surface");surface.setAccessible(true);
            player=(MainActivity)((java.lang.ref.WeakReference<?>)surface.get(null)).get();
            if(player==null)player=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).putExtra("wake",true).putExtra("keep",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            Thread.sleep(600);
            check("real_silent_fixture_is_playing",MusicSessionService.isPlaying());
            check("player_stays_locked",getTargetContext().getSystemService(KeyguardManager.class).isKeyguardLocked());
            monitor=(CarBluetooth)field(player,"carBluetooth");
            pref(PlayerPreferences.DRIVE_ENABLED,true);snapshot(true,false);Thread.sleep(600);
            check("chosen_car_activates_illuminated_player",modes().driving() && modes().mode()==PlayerModeController.Mode.ACTIVE && screenKept());
            check("driving_has_no_dim_timer",!((Handler)field(player,"handler")).hasCallbacks((Runnable)field(player,"dim")));
            Thread.sleep(10_500);
            check("driving_survives_normal_timeout",modes().mode()==PlayerModeController.Mode.ACTIVE && screenKept());
            check("idle_driving_does_not_hold_high_refresh",player.getWindow().getAttributes().preferredRefreshRate==60f);
            main(() -> { modes().toggle(SystemClock.uptimeMillis());call(player,"applyMode"); });snapshot(true,false);
            check("manual_double_tap_keeps_ambient_despite_connection_updates",modes().mode()==PlayerModeController.Mode.AMBIENT);
            main(() -> { modes().toggle(SystemClock.uptimeMillis());call(player,"applyMode"); });
            check("second_double_tap_resumes_driving_hold",modes().mode()==PlayerModeController.Mode.ACTIVE && !modes().hasActiveTimeout());
            snapshot(false,false);
            check("disconnect_restores_normal_timeout",!modes().driving() && modes().hasActiveTimeout());
            Thread.sleep(10_500);
            check("disconnected_player_returns_to_aod",modes().mode()==PlayerModeController.Mode.AMBIENT);
            pref(PlayerPreferences.DRIVE_CHARGING,true);snapshot(true,false);
            check("charging_only_blocks_unplugged_car",!modes().driving());
            snapshot(true,true);Thread.sleep(500);
            check("plugging_in_activates_driving",modes().driving() && modes().mode()==PlayerModeController.Mode.ACTIVE);
            snapshot(true,false);
            check("unplugging_releases_driving_hold",!modes().driving() && modes().hasActiveTimeout());
            pref(PlayerPreferences.DRIVE_CHARGING,false);snapshot(true,false);
            main(() -> { set(player,"pocketed",true);call(player,"updateDrivingMode");modes().toggle(SystemClock.uptimeMillis());call(player,"applyMode"); });
            check("pocket_overrides_driving",!modes().driving() && modes().mode()==PlayerModeController.Mode.AMBIENT && !((PlayerSurface)field(player,"widgets")).progressTickScheduled());
            main(() -> { set(player,"pocketed",false);call(player,"updateDrivingMode");call(player,"applyMode"); });
            check("uncovering_restores_driving",modes().driving() && modes().mode()==PlayerModeController.Mode.ACTIVE);
            main(() -> { modes().beginAuthentication();call(player,"applyMode"); });snapshot(false,false);snapshot(true,false);
            check("bluetooth_cannot_replace_native_authentication",modes().mode()==PlayerModeController.Mode.AUTHENTICATING);
            main(() -> { modes().cancelAuthentication();call(player,"applyMode"); });
            check("cancelled_authentication_restores_driving",modes().mode()==PlayerModeController.Mode.ACTIVE);
            shell("am broadcast -n app.morphe.android.apps.youtube.music/.FixtureReceiver --ez playing false");Thread.sleep(300);snapshot(true,false);
            check("paused_music_releases_driving_hold",!modes().driving());
            shell("am broadcast -n app.morphe.android.apps.youtube.music/.FixtureReceiver --ez playing true");Thread.sleep(300);snapshot(true,false);
            check("resumed_music_restores_driving",modes().driving());
            main(() -> { modes().unlocked();call(player,"updateDrivingMode"); });
            check("late_connection_cannot_reopen_closed_player",modes().mode()==PlayerModeController.Mode.CLOSED);
            result.putString("stream","Driving integration: "+checks.length()+" checks PASS\n");
        } catch(Throwable failure) {
            errors.add(failure.toString());android.util.Log.e("DrivingTest","failure",failure);result.putString("stream",failure.toString());
        } finally {
            try {
                main(() -> { if(player!=null)player.finishAndRemoveTask();if(settings!=null)settings.finish();
                    PlayerPreferences.store(getTargetContext()).edit().putBoolean(PlayerPreferences.DRIVE_ENABLED,false)
                    .putBoolean(PlayerPreferences.DRIVE_CHARGING,false).remove(PlayerPreferences.DRIVE_ADDRESS).remove(PlayerPreferences.DRIVE_NAME).commit(); });
                shell("locksettings clear --old 1234");shell("input keyevent KEYCODE_WAKEUP");shell("wm dismiss-keyguard");
                JSONObject report=new JSONObject().put("version","0.19.0").put("emulator_only",true).put("synthetic_bluetooth",true)
                    .put("checks",checks).put("errors",new JSONArray(errors));
                try(var out=new FileWriter(new File(getTargetContext().getFilesDir(),"driving-integration.json"))) { out.write(report.toString(2)); }
            } catch(Throwable cleanup) { result.putString("cleanup_error",cleanup.toString()); }
            finish(errors.isEmpty()?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
        }
    }
}
