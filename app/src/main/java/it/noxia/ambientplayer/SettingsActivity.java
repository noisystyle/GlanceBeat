package it.noxia.ambientplayer;

import android.content.ComponentName;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.session.PlaybackState;
import android.os.Bundle;
import android.provider.Settings;
import android.net.Uri;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.materialswitch.MaterialSwitch;

/** The launcher requires Android access before showing settings; it never dismisses keyguard. */
public final class SettingsActivity extends AppCompatActivity {
    private MaterialSwitch enabled, driveEnabled, driveCharging;
    private CarBluetooth carBluetooth;
    private CarDevicePicker carPicker;
    private boolean bindingDriving, pendingDriveEnable, pendingPicker;
    private final androidx.activity.result.ActivityResultLauncher<String> bluetoothPermission =
        registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.RequestPermission(), granted -> {
            if (granted) {
                if (pendingDriveEnable) PlayerPreferences.store(this).edit().putBoolean(PlayerPreferences.DRIVE_ENABLED, true).apply();
                if (carBluetooth != null) carBluetooth.refresh();
                if (pendingPicker) showCarPicker();
            } else Toast.makeText(this, R.string.drive_permission_denied, Toast.LENGTH_LONG).show();
            pendingDriveEnable = false; pendingPicker = false;
            refreshDrivingStatus();
        });
    private final Runnable musicChanged = () -> runOnUiThread(this::refreshStatus);
    private final SharedPreferences.OnSharedPreferenceChangeListener preferencesChanged = (prefs, key) -> {
        if (PlayerPreferences.ENABLED.equals(key) || PlayerPreferences.INTERNAL_REVERSED.equals(key) || PlayerPreferences.drivingKey(key)) refreshStatus();
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (redirectToSetup()) return;
        if (state != null) {
            pendingDriveEnable = state.getBoolean("pending_drive_enable");
            pendingPicker = state.getBoolean("pending_car_picker");
        }
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.settings);
        View root = findViewById(R.id.settings_root);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        int surface = MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurface, 0);
        boolean light = MaterialColors.isColorLight(surface);
        WindowCompat.getInsetsController(getWindow(), root).setAppearanceLightStatusBars(light);
        WindowCompat.getInsetsController(getWindow(), root).setAppearanceLightNavigationBars(light);
        View content = findViewById(R.id.settings_content);
        FrameLayout.LayoutParams layout = (FrameLayout.LayoutParams) content.getLayoutParams();
        layout.width = Math.min(getResources().getDisplayMetrics().widthPixels,
            Math.round(640 * getResources().getDisplayMetrics().density));
        content.setLayoutParams(layout);
        carBluetooth = new CarBluetooth(this, () -> {
            refreshDrivingStatus(); if (carPicker != null) carPicker.refresh();
        });
        driveEnabled = findViewById(R.id.drive_enabled);
        driveCharging = findViewById(R.id.drive_charging);
        driveEnabled.setOnCheckedChangeListener((button, checked) -> {
            if (bindingDriving) return;
            if (checked && !CarBluetooth.permitted(this)) {
                bindingDriving = true; driveEnabled.setChecked(false); bindingDriving = false;
                requestBluetooth(true);
            } else {
                PlayerPreferences.store(this).edit().putBoolean(PlayerPreferences.DRIVE_ENABLED, checked).apply();
                if (checked && PlayerPreferences.driveAddress(this).isEmpty()) showCarPicker();
            }
        });
        driveCharging.setOnCheckedChangeListener((button, checked) -> {
            if (!bindingDriving) PlayerPreferences.store(this).edit().putBoolean(PlayerPreferences.DRIVE_CHARGING, checked).apply();
        });
        findViewById(R.id.drive_device).setOnClickListener(view -> {
            if (!CarBluetooth.permitted(this)) requestBluetooth(false); else showCarPicker();
        });
        enabled = findViewById(R.id.player_enabled);
        enabled.setChecked(PlayerPreferences.isEnabled(this));
        enabled.setOnCheckedChangeListener((button, checked) -> PlayerPreferences.setEnabled(this, checked));
        MaterialSwitch reversed=findViewById(R.id.internal_columns_reversed);
        reversed.setChecked(PlayerPreferences.internalReversed(this));
        reversed.setOnCheckedChangeListener((button,checked)->PlayerPreferences.store(this).edit().putBoolean(PlayerPreferences.INTERNAL_REVERSED,checked).apply());
        findViewById(R.id.preview_player).setOnClickListener(view ->
            startActivity(new Intent(this, PreviewActivity.class)));
        findViewById(R.id.support_project).setOnClickListener(view -> {
            try { startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://buymeacoffee.com/marcoprattv")).addCategory(Intent.CATEGORY_BROWSABLE)); }
            catch(ActivityNotFoundException unavailable){Toast.makeText(this,R.string.support_unavailable,Toast.LENGTH_LONG).show();}
        });
        TextView credits=findViewById(R.id.icon_credits);
        credits.setText(android.text.Html.fromHtml(getString(R.string.icon_credits),android.text.Html.FROM_HTML_MODE_LEGACY));
        credits.setMovementMethod(android.text.method.LinkMovementMethod.getInstance());
        findViewById(R.id.music_access).setOnClickListener(view -> openMusicAccess());
        findViewById(R.id.overlay_access).setOnClickListener(view -> {
            try { startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()))); }
            catch (ActivityNotFoundException unavailable) {
                Toast.makeText(this, R.string.access_unavailable, Toast.LENGTH_LONG).show();
            }
        });
        findViewById(R.id.continuity_access).setOnClickListener(view->{
            try{startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));}
            catch(ActivityNotFoundException unavailable){Toast.makeText(this,R.string.access_unavailable,Toast.LENGTH_LONG).show();}
        });
        PlayerPreferences.store(this).registerOnSharedPreferenceChangeListener(preferencesChanged);
        MusicSessionService.addListener(musicChanged);
        refreshStatus();
    }

    private void requestBluetooth(boolean enable) {
        pendingDriveEnable = enable; pendingPicker = true;
        boolean asked = PlayerPreferences.store(this).getBoolean(PlayerPreferences.BT_PERMISSION_ASKED, false);
        if (asked && !shouldShowRequestPermissionRationale(android.Manifest.permission.BLUETOOTH_CONNECT)) {
            pendingDriveEnable = false; pendingPicker = false;
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(R.string.drive_permission_settings).setMessage(R.string.drive_permission_settings_body)
                .setNegativeButton(R.string.drive_cancel, null)
                .setPositiveButton(R.string.drive_open_app_settings, (dialog, which) -> startActivity(
                    new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:"+getPackageName())))).show();
            return;
        }
        PlayerPreferences.store(this).edit().putBoolean(PlayerPreferences.BT_PERMISSION_ASKED, true).apply();
        bluetoothPermission.launch(android.Manifest.permission.BLUETOOTH_CONNECT);
    }
    private void showCarPicker() {
        if (carPicker != null) return;
        carBluetooth.refresh();
        carPicker = new CarDevicePicker(this, carBluetooth, () -> carPicker = null);
        carPicker.show();
    }
    private void refreshDrivingStatus() {
        if (driveEnabled == null || carBluetooth == null) return;
        bindingDriving = true;
        driveEnabled.setChecked(PlayerPreferences.driveEnabled(this));
        driveCharging.setChecked(PlayerPreferences.driveCharging(this));
        bindingDriving = false;
        String address = PlayerPreferences.driveAddress(this);
        String name = PlayerPreferences.driveName(this);
        ((TextView)findViewById(R.id.drive_device_name)).setText(name.isEmpty() ? getString(R.string.drive_none) : name);
        ((MaterialButton)findViewById(R.id.drive_device)).setText(!CarBluetooth.permitted(this) ? R.string.drive_permission_action
            : address.isEmpty() ? R.string.drive_choose : R.string.drive_change);
        int status = !PlayerPreferences.driveEnabled(this) ? R.string.drive_off
            : !PlayerPreferences.isEnabled(this) ? R.string.drive_master_off
            : !carBluetooth.permission() ? R.string.drive_permission
            : !carBluetooth.available() ? R.string.drive_unavailable
            : address.isEmpty() ? R.string.drive_needs_device
            : !carBluetooth.bluetoothOn() ? R.string.drive_bluetooth_off
            : !carBluetooth.paired(address) ? R.string.drive_unpaired
            : !carBluetooth.connected(address) ? R.string.drive_waiting
            : PlayerPreferences.driveCharging(this) && !carBluetooth.charging() ? R.string.drive_waiting_charge
            : MusicSessionService.isPlaying() ? R.string.drive_active : R.string.drive_ready;
        ((TextView)findViewById(R.id.drive_status)).setText(status);
    }

    private void openMusicAccess() {
        Intent intent = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
            .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                new ComponentName(this, MusicSessionService.class).flattenToString());
        try { startActivity(intent); }
        catch (ActivityNotFoundException unavailable) {
            try { startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)); }
            catch (ActivityNotFoundException missing) {
                Toast.makeText(this, R.string.access_unavailable, Toast.LENGTH_LONG).show();
            }
        }
    }

    private void refreshStatus() {
        if (enabled == null) return;
        MaterialSwitch reversed=findViewById(R.id.internal_columns_reversed);
        reversed.setChecked(PlayerPreferences.internalReversed(this));
        refreshDrivingStatus();
        boolean value = PlayerPreferences.isEnabled(this);
        enabled.setChecked(value);
        ((TextView) findViewById(R.id.player_status)).setText(value ? R.string.player_on : R.string.player_off);
        boolean access = NotificationManagerCompat.getEnabledListenerPackages(this).contains(getPackageName());
        int message = R.string.music_needed;
        if (access) {
            message = !value ? R.string.music_disabled : !MusicSessionService.connected
                ? R.string.music_connecting : R.string.music_ready;
            if (value && MusicSessionService.current != null) {
                PlaybackState state = MusicSessionService.current.getPlaybackState();
                message = state != null && state.getState() == PlaybackState.STATE_PLAYING
                    ? R.string.music_playing : R.string.music_paused;
            }
        }
        ((TextView) findViewById(R.id.music_status)).setText(message);
        ((MaterialButton) findViewById(R.id.music_access)).setText(access ? R.string.manage_access : R.string.grant_access);
        ((TextView)findViewById(R.id.continuity_status)).setText(PlayerContinuityService.connected()?R.string.continuity_ready:R.string.continuity_needed);
        boolean overlay = Settings.canDrawOverlays(this);
        ((TextView) findViewById(R.id.overlay_status)).setText(overlay ? R.string.overlay_ready : R.string.overlay_needed);
        ((MaterialButton) findViewById(R.id.overlay_access)).setText(overlay ? R.string.manage_access : R.string.overlay_grant);
        if (value) ((TextView) findViewById(R.id.player_status)).setText(!access ? R.string.player_needs_music
            : !overlay ? R.string.player_needs_overlay : R.string.player_ready);
    }

    @Override protected void onResume() {
        super.onResume();
        if (isFinishing() || redirectToSetup()) return;
        if (carBluetooth != null) carBluetooth.start();
        refreshStatus();
        MusicSessionService.recheckAutomatic();
    }
    private boolean redirectToSetup(){
        if(SetupPermissions.ready(this))return false;
        startActivity(new Intent(this,SetupActivity.class));finish();return true;
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        state.putBoolean("pending_drive_enable", pendingDriveEnable);
        state.putBoolean("pending_car_picker", pendingPicker);
        super.onSaveInstanceState(state);
    }
    @Override protected void onPause() {
        if (carBluetooth != null) carBluetooth.stop();
        super.onPause();
    }
    @Override protected void onDestroy() {
        if (carPicker != null) carPicker.dismiss();
        if (carBluetooth != null) carBluetooth.stop();
        PlayerPreferences.store(this).unregisterOnSharedPreferenceChangeListener(preferencesChanged);
        MusicSessionService.removeListener(musicChanged);
        super.onDestroy();
    }
}
