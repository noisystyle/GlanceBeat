package it.noxia.ambientplayer;

/** Conservative fallback: a recent bright-to-dark change, not darkness by itself. */
final class PocketLightPolicy {
    private float lux=Float.NaN,reference;
    private long brightAt;
    private boolean suppressed;
    void reset() { lux=Float.NaN;reference=0;brightAt=0;suppressed=false; }
    void add(float value,long now) {
        if (!Float.isFinite(value) || value<0) return;
        lux=value;
        if (value>=5) { reference=value;brightAt=now;suppressed=false; }
    }
    boolean candidate(long now) {
        return !suppressed && reference>=5 && lux<=.75f && now-brightAt<=6000;
    }
    boolean uncovered() { return Float.isFinite(lux) && lux>=Math.max(2,reference*.2f); }
    void revealManually() { suppressed=true; }
    float lux() { return lux; }
    static boolean coveredOrientation(float x,float y,float z) {
        float gravity=(float)Math.sqrt(x*x+y*y+z*z);
        return gravity>=8 && gravity<=12 && z<7;
    }
}
