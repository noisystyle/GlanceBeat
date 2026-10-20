package it.noxia.ambientplayer;

/** A short tap or cancelled hold never produces a seek command. */
final class SeekGesture {
    private boolean down,active;
    private long duration,position;
    boolean begin(float x,float width,long durationMs) {
        cancel();
        if (width<=0 || durationMs<=0 || !Float.isFinite(x)) return false;
        down=true;duration=durationMs;setPosition(x,width);return true;
    }
    boolean activate() { if (!down) return false;active=true;return true; }
    void move(float x,float width) { if (active && width>0 && Float.isFinite(x)) setPosition(x,width); }
    private void setPosition(float x,float width) { position=Math.round(Math.max(0,Math.min(1,x/width))*duration); }
    long release() { long result=active?position:-1;cancel();return result; }
    void cancel() { down=false;active=false; }
    boolean isDown() { return down; }
    boolean isActive() { return active; }
    long position() { return position; }
}
