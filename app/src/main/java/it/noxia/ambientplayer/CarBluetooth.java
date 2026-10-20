package it.noxia.ambientplayer;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.BatteryManager;
import android.os.Build;
import java.util.*;

/** Event-based observation only. Never scans, pairs, connects or changes the adapter. */
final class CarBluetooth {
    record Device(String address, String name, boolean connected) { }
    private final Context context;
    private final Runnable changed;
    private final Map<Integer, BluetoothProfile> proxies = new HashMap<>();
    private final Set<Integer> requested = new HashSet<>();
    private List<Device> devices = List.of();
    private BluetoothAdapter adapter;
    private BluetoothProfile.ServiceListener profileListener;
    private boolean started, registered, permission, available, bluetoothOn, charging;
    private int generation;

    CarBluetooth(Context context, Runnable changed) {
        this.context = context;
        this.changed = changed;
        BluetoothManager manager = context.getSystemService(BluetoothManager.class);
        adapter = manager == null ? null : manager.getAdapter();
    }
    static boolean permitted(Context context) {
        return context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
    }
    List<Device> devices() { return devices; }
    boolean permission() { return permission; }
    boolean available() { return available; }
    boolean bluetoothOn() { return bluetoothOn; }
    boolean charging() { return charging; }
    boolean listening() { return registered; }
    boolean connected(String address) {
        return devices.stream().anyMatch(d -> d.address().equals(address) && d.connected());
    }
    boolean paired(String address) { return devices.stream().anyMatch(d -> d.address().equals(address)); }
    private final BroadcastReceiver events = new BroadcastReceiver() {
        @Override public void onReceive(Context ignored, Intent intent) {
            // Protected platform broadcasts are triggers; query the actual stack instead of trusting extras.
            refresh();
        }
    };
    private final BroadcastReceiver power = new BroadcastReceiver() {
        @Override public void onReceive(Context ignored, Intent intent) { refresh(); }
    };
    void start() {
        if (started) { refresh(); return; }
        started = true;
        int epoch = ++generation;
        profileListener = new BluetoothProfile.ServiceListener() {
            @Override public void onServiceConnected(int profile, BluetoothProfile proxy) {
                if (!started || epoch != generation) { closeProxy(profile, proxy); return; }
                BluetoothProfile previous = proxies.put(profile, proxy);
                if (previous != null && previous != proxy) closeProxy(profile, previous);
                refresh();
            }
            @Override public void onServiceDisconnected(int profile) {
                if (!started || epoch != generation) return;
                proxies.remove(profile);
                refresh();
            }
        };
        IntentFilter bt = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        bt.addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED);
        bt.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        bt.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        bt.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        bt.addAction(BluetoothDevice.ACTION_NAME_CHANGED);
        bt.addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED);
        bt.addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED);
        bt.addAction(BluetoothLeAudio.ACTION_LE_AUDIO_CONNECTION_STATE_CHANGED);
        bt.addAction(BluetoothHearingAid.ACTION_CONNECTION_STATE_CHANGED);
        // Bluetooth lives in a privileged UID separate from system_server.
        if (Build.VERSION.SDK_INT >= 33) context.registerReceiver(events, bt, Context.RECEIVER_EXPORTED);
        else context.registerReceiver(events, bt);
        IntentFilter battery = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        if (Build.VERSION.SDK_INT >= 33) context.registerReceiver(power, battery, Context.RECEIVER_NOT_EXPORTED);
        else context.registerReceiver(power, battery);
        registered = true;
        refresh();
    }
    @SuppressLint("MissingPermission") // Every Bluetooth read is gated by runtime permission, with revocation caught.
    void refresh() {
        if (!started) return;
        permission = permitted(context);
        available = adapter != null;
        bluetoothOn = false;
        Intent battery = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        charging = battery != null && battery.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0;
        List<Device> next = new ArrayList<>();
        try {
            if (permission && available) {
                bluetoothOn = adapter.isEnabled();
                if (bluetoothOn) {
                    Set<String> connected = new HashSet<>();
                    if (Build.VERSION.SDK_INT < 37) {
                        requestProfiles();
                        for (BluetoothProfile proxy : proxies.values())
                            for (BluetoothDevice d : proxy.getConnectedDevices()) connected.add(d.getAddress());
                    }
                    for (BluetoothDevice d : adapter.getBondedDevices()) {
                        boolean online = Build.VERSION.SDK_INT >= 37
                            ? d.isConnected(BluetoothDevice.TRANSPORT_BREDR) || d.isConnected(BluetoothDevice.TRANSPORT_LE)
                            : connected.contains(d.getAddress());
                        String name = d.getAlias();
                        if (name == null || name.isBlank()) name = d.getName();
                        if (name == null || name.isBlank()) name = context.getString(R.string.drive_unnamed);
                        next.add(new Device(d.getAddress(), name, online));
                    }
                } else releaseProfiles();
            } else releaseProfiles();
        } catch (SecurityException revoked) {
            permission = false; bluetoothOn = false; next.clear(); releaseProfiles();
        } catch (IllegalStateException unavailable) {
            bluetoothOn = false; next.clear(); releaseProfiles();
        }
        next.sort(Comparator.comparing(Device::connected).reversed().thenComparing(Device::name, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(Device::address));
        devices = List.copyOf(next);
        changed.run();
    }
    @SuppressLint("MissingPermission")
    private void requestProfiles() {
        int[] profiles = Build.VERSION.SDK_INT >= 33
            ? new int[]{BluetoothProfile.A2DP, BluetoothProfile.HEADSET, BluetoothProfile.LE_AUDIO, BluetoothProfile.HEARING_AID}
            : new int[]{BluetoothProfile.A2DP, BluetoothProfile.HEADSET, BluetoothProfile.HEARING_AID};
        for (int profile : profiles) {
            if (!requested.add(profile)) continue;
            adapter.getProfileProxy(context, profileListener, profile);
        }
    }
    private void closeProxy(int profile, BluetoothProfile proxy) {
        try { if (adapter != null) adapter.closeProfileProxy(profile, proxy); }
        catch (SecurityException | IllegalStateException ignored) { }
    }
    private void releaseProfiles() {
        for (var entry : proxies.entrySet()) closeProxy(entry.getKey(), entry.getValue());
        proxies.clear(); requested.clear();
    }
    void stop() {
        started = false; generation++;
        if (registered) { context.unregisterReceiver(events); context.unregisterReceiver(power); registered = false; }
        releaseProfiles(); devices = List.of(); bluetoothOn = false;
    }
}
