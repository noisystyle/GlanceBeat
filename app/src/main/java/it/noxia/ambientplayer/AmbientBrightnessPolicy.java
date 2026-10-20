package it.noxia.ambientplayer;

import java.util.Arrays;

/** Window brightness values are normalized requests, not physical nits or panel percentages. */
final class AmbientBrightnessPolicy {
    static final float FALLBACK = 0.075f;
    static final float MIN = 0.045f, MAX = 0.28f;
    private static final float[] LUX = {0,5,50,300,1500,10000,30000};
    private static final float[] LEVEL = {MIN,0.055f,0.08f,0.12f,0.17f,0.24f,MAX};
    private final float[] samples = new float[5];
    private int size, next;
    void resetSamples() { size=0; next=0; }
    boolean add(float lux) {
        if (!Float.isFinite(lux) || lux < 0) return false;
        samples[next]=lux;next=(next+1)%samples.length;size=Math.min(samples.length,size+1);
        return true;
    }
    float medianLux() {
        if (size==0) return Float.NaN;
        float[] values=Arrays.copyOf(samples,size);Arrays.sort(values);
        return values[size/2];
    }
    float target() { return forLux(medianLux()); }
    static float forLux(float lux) {
        if (!Float.isFinite(lux) || lux < 0) return FALLBACK;
        for (int i=1;i<LUX.length;i++) if (lux<=LUX[i]) {
            double fraction=(Math.log1p(lux)-Math.log1p(LUX[i-1]))/(Math.log1p(LUX[i])-Math.log1p(LUX[i-1]));
            return (float)(LEVEL[i-1]+fraction*(LEVEL[i]-LEVEL[i-1]));
        }
        return MAX;
    }
    static boolean materiallyDifferent(float current,float target) {
        return Math.abs(target-current)>=Math.max(0.005f,current*0.10f);
    }
}
