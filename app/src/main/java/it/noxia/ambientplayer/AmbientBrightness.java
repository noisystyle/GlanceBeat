package it.noxia.ambientplayer;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

/** Non-wakeup light sensor, active only for a visible settled ambient player. */
final class AmbientBrightness implements SensorEventListener {
    private final SensorManager manager;
    private final Sensor sensor;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final AmbientBrightnessPolicy policy=new AmbientBrightnessPolicy();
    private final Runnable changed;
    private boolean listening;
    private float brightness=AmbientBrightnessPolicy.FALLBACK;
    private long lastApplied;
    private long lastSample;
    private float latestLux;
    private final Runnable apply=new Runnable() {
        @Override public void run() {
            if (!listening) return;
            // TYPE_LIGHT may be on-change: a steady final value need not arrive repeatedly.
            float target=SystemClock.uptimeMillis()-lastSample>=900
                ? AmbientBrightnessPolicy.forLux(latestLux):policy.target();
            if (!AmbientBrightnessPolicy.materiallyDifferent(brightness,target)) return;
            brightness=target;lastApplied=SystemClock.uptimeMillis();changed.run();
        }
    };
    AmbientBrightness(Context context,Runnable changed) {
        this.changed=changed;
        manager=context.getSystemService(SensorManager.class);
        sensor=manager==null?null:manager.getDefaultSensor(Sensor.TYPE_LIGHT,false);
    }
    void setEnabled(boolean enabled) {
        if (!enabled) { stop(); return; }
        if (listening || sensor==null) return;
        policy.resetSamples();
        listening=manager.registerListener(this,sensor,SensorManager.SENSOR_DELAY_NORMAL,handler);
    }
    void stop() {
        handler.removeCallbacks(apply);
        if (listening) manager.unregisterListener(this);
        listening=false;
    }
    @Override public void onSensorChanged(SensorEvent event) {
        if (!listening || event.values.length==0 || !policy.add(event.values[0])) return;
        latestLux=event.values[0];lastSample=SystemClock.uptimeMillis();
        if (!handler.hasCallbacks(apply)) {
            long stability=policy.target()<brightness?2_500:900;
            long cooldown=Math.max(0,2_000-(SystemClock.uptimeMillis()-lastApplied));
            handler.postDelayed(apply,Math.max(stability,cooldown));
        }
    }
    @Override public void onAccuracyChanged(Sensor sensor,int accuracy) { }
    float brightness() { return brightness; }
    float lux() { return policy.medianLux(); }
    boolean isAvailable() { return sensor!=null; }
    boolean isListening() { return listening; }
}
