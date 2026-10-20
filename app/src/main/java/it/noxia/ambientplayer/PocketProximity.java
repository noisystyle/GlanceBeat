package it.noxia.ambientplayer;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

/** Public proximity plus a conservative light/orientation fallback for Samsung virtual sensors. */
final class PocketProximity implements SensorEventListener {
    private final SensorManager manager;
    private final Sensor sensor, light, acceleration;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable changed;
    private final PocketLightPolicy lightPolicy=new PocketLightPolicy();
    private boolean listening, covered, pendingCovered, proximityNear, lightCovered, checkingOrientation;
    private final Runnable commit=this::commitCover;
    private final Runnable stopOrientation=this::stopOrientationCheck;
    private final Runnable lightCommit=() -> commitLightCover();
    private void commitCover() {
        if (listening && covered!=pendingCovered) { covered=pendingCovered;changed.run(); }
    }
    private void commitLightCover() {
        if (listening && lightPolicy.candidate(SystemClock.uptimeMillis())) {
            android.util.Log.i("AmbientPocket","light_cover_confirmed");
            lightCovered=true;scheduleCover(0);
        }
    }
    PocketProximity(Context context,Runnable changed) {
        this.changed=changed;
        manager=context.getSystemService(SensorManager.class);
        Sensor normal=manager==null?null:manager.getDefaultSensor(Sensor.TYPE_PROXIMITY,false);
        sensor=normal!=null?normal:manager==null?null:manager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
        light=manager==null?null:manager.getDefaultSensor(Sensor.TYPE_LIGHT,false);
        acceleration=manager==null?null:manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER,false);
    }
    void setEnabled(boolean enabled) {
        if (!enabled) { pause();return; }
        if (listening || manager==null) return;
        if(!covered && !lightCovered)lightPolicy.reset();
        boolean registered=sensor!=null && manager.registerListener(this,sensor,SensorManager.SENSOR_DELAY_NORMAL,handler);
        if (isLightFallbackAvailable()) registered=manager.registerListener(this,light,SensorManager.SENSOR_DELAY_NORMAL,handler)||registered;
        listening=registered;
    }
    private void pause() {
        handler.removeCallbacksAndMessages(null);
        if(manager!=null)manager.unregisterListener(this);
        listening=false;checkingOrientation=false;pendingCovered=covered;
    }
    void stop() {
        pause();
        covered=false;pendingCovered=false;proximityNear=false;lightCovered=false;checkingOrientation=false;
        lightPolicy.reset();
    }
    private void scheduleCover(long delay) {
        boolean next=proximityNear || lightCovered;
        if (next==pendingCovered && handler.hasCallbacks(commit)) return;
        pendingCovered=next;handler.removeCallbacks(commit);
        if (covered!=next) handler.postDelayed(commit,delay);
    }
    private void stopOrientationCheck() {
        if (checkingOrientation) manager.unregisterListener(this,acceleration);
        checkingOrientation=false;handler.removeCallbacks(stopOrientation);
    }
    @Override public void onSensorChanged(SensorEvent event) {
        if (!listening || event.values.length==0 || !Float.isFinite(event.values[0])) return;
        long now=SystemClock.uptimeMillis();
        if (event.sensor==sensor) {
            proximityNear=event.values[0]>=0 && event.values[0]<Math.min(5f,sensor.getMaximumRange());
            scheduleCover(proximityNear?350:120);
        } else if (event.sensor==light) {
            lightPolicy.add(event.values[0],now);
            if (!lightPolicy.candidate(now)) {
                handler.removeCallbacks(lightCommit);stopOrientationCheck();
                if (lightCovered && lightPolicy.uncovered()) { lightCovered=false;scheduleCover(120); }
            } else if (!lightCovered && !checkingOrientation && !handler.hasCallbacks(lightCommit)) {
                android.util.Log.i("AmbientPocket","light_drop_checking_orientation");
                checkingOrientation=manager.registerListener(this,acceleration,SensorManager.SENSOR_DELAY_NORMAL,handler);
                if (checkingOrientation) handler.postDelayed(stopOrientation,600);
            }
        } else if (event.sensor==acceleration && event.values.length>=3 && checkingOrientation
                && PocketLightPolicy.coveredOrientation(event.values[0],event.values[1],event.values[2])) {
            stopOrientationCheck();
            android.util.Log.i("AmbientPocket","covered_orientation_confirmed");
            if (lightPolicy.candidate(now) && !handler.hasCallbacks(lightCommit)) handler.postDelayed(lightCommit,1200);
        }
    }
    void revealManually() {
        if (!canRevealManually()) return;
        lightPolicy.revealManually();handler.removeCallbacks(lightCommit);lightCovered=false;scheduleCover(0);
    }
    boolean canRevealManually() { return lightCovered && !proximityNear; }
    boolean isLightCovered() { return lightCovered; }
    boolean isLightFallbackAvailable() { return light!=null && acceleration!=null; }
    float lux() { return lightPolicy.lux(); }
    @Override public void onAccuracyChanged(Sensor sensor,int accuracy) { }
    boolean isCovered() { return covered; }
    boolean isAvailable() { return sensor!=null || isLightFallbackAvailable(); }
    boolean isListening() { return listening; }
}
