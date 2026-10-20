package it.noxia.ambientplayer;

import android.app.Activity;
import android.content.Intent;
import android.provider.Settings;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.core.view.ViewCompat;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.radiobutton.MaterialRadioButton;

/** A single local selection; the actual Bluetooth pairing UI stays with Android. */
final class CarDevicePicker {
    private final Activity activity;
    private final CarBluetooth bluetooth;
    private final BottomSheetDialog dialog;
    private final View content;
    CarDevicePicker(Activity activity, CarBluetooth bluetooth, Runnable dismissed) {
        this.activity = activity; this.bluetooth = bluetooth;
        dialog = new BottomSheetDialog(activity);
        content = activity.getLayoutInflater().inflate(R.layout.car_device_picker, new android.widget.FrameLayout(dialog.getContext()), false);
        dialog.setContentView(content);
        dialog.setOnDismissListener(ignored -> dismissed.run());
        content.findViewById(R.id.car_picker_manage).setOnClickListener(v -> {
            dialog.dismiss(); activity.startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS));
        });
        content.findViewById(R.id.car_picker_clear).setOnClickListener(v -> {
            PlayerPreferences.selectCar(activity, "", ""); dialog.dismiss();
        });
        refresh();
    }
    void show() { dialog.show(); dialog.getBehavior().setPeekHeight(activity.getResources().getDisplayMetrics().heightPixels); }
    void dismiss() { dialog.dismiss(); }
    private int dp(float value) { return Math.round(value * activity.getResources().getDisplayMetrics().density); }
    private int color(int attr) { return MaterialColors.getColor(content, attr); }
    void refresh() {
        TextView status = content.findViewById(R.id.car_picker_status);
        int message = !bluetooth.permission() ? R.string.drive_permission : !bluetooth.available() ? R.string.drive_unavailable
            : !bluetooth.bluetoothOn() ? R.string.drive_bluetooth_off : R.string.drive_no_devices;
        status.setText(message);
        status.setVisibility(bluetooth.devices().isEmpty() ? View.VISIBLE : View.GONE);
        content.findViewById(R.id.car_picker_clear).setVisibility(PlayerPreferences.driveAddress(activity).isEmpty() ? View.GONE : View.VISIBLE);
        LinearLayout rows = content.findViewById(R.id.car_picker_devices);
        rows.removeAllViews();
        for (CarBluetooth.Device device : bluetooth.devices()) {
            boolean selected = device.address().equals(PlayerPreferences.driveAddress(activity));
            MaterialCardView card = new MaterialCardView(dialog.getContext());
            card.setRadius(dp(24)); card.setCardElevation(0);
            card.setCardBackgroundColor(color(selected ? com.google.android.material.R.attr.colorSecondaryContainer : com.google.android.material.R.attr.colorSurfaceContainerHigh));
            card.setStrokeWidth(selected ? dp(1) : 0); card.setStrokeColor(color(androidx.appcompat.R.attr.colorPrimary));
            LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(-1, -2);cardParams.bottomMargin = dp(8);
            rows.addView(card, cardParams);
            LinearLayout body = new LinearLayout(dialog.getContext());body.setGravity(android.view.Gravity.CENTER_VERTICAL);
            body.setPadding(dp(16), dp(12), dp(16), dp(12));body.setMinimumHeight(dp(80));
            card.addView(body);
            LinearLayout labels = new LinearLayout(dialog.getContext());labels.setOrientation(LinearLayout.VERTICAL);
            body.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
            TextView name = new TextView(dialog.getContext());name.setTextSize(18);name.setTextColor(color(com.google.android.material.R.attr.colorOnSurface));
            boolean duplicate = bluetooth.devices().stream().filter(d -> d.name().equals(device.name())).count() > 1;
            name.setText(duplicate ? device.name() + " · " + device.address().substring(Math.max(0, device.address().length()-5)) : device.name());
            labels.addView(name);
            TextView connection = new TextView(dialog.getContext());connection.setTextSize(13);connection.setPadding(0,dp(4),0,0);
            connection.setTextColor(color(com.google.android.material.R.attr.colorOnSurfaceVariant));
            String state = activity.getString(device.connected() ? R.string.drive_connected : R.string.drive_disconnected);
            connection.setText(selected ? activity.getString(R.string.drive_device_selected, state) : state);labels.addView(connection);
            MaterialRadioButton radio = new MaterialRadioButton(dialog.getContext());radio.setChecked(selected);radio.setClickable(false);radio.setFocusable(false);
            radio.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);body.addView(radio);
            // One accessible target per row, including the name, state and selected state.
            card.setFocusable(true);card.setClickable(true);card.setContentDescription(name.getText()+", "+connection.getText());
            card.setSelected(selected);ViewCompat.setStateDescription(card, selected ? activity.getString(R.string.drive_device_selected, state) : state);
            body.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
            card.setOnClickListener(v -> { PlayerPreferences.selectCar(activity, device.address(), device.name());dialog.dismiss(); });
        }
        View scroll = content.findViewById(R.id.car_picker_scroll);
        scroll.getLayoutParams().height = Math.min(dp(88)*bluetooth.devices().size(), Math.round(activity.getResources().getDisplayMetrics().heightPixels*.36f));
        scroll.requestLayout();
    }
}
