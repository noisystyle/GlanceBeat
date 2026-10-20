package it.noxia.ambientplayer;

/** Interaction policy, independent of Android and of biometric availability. */
final class PlayerModeController {
    enum Mode { AMBIENT, ACTIVE, AUTHENTICATING, CLOSED }
    static final long ACTIVE_DURATION_MS = 10_000;
    private Mode mode = Mode.AMBIENT;
    private long activeUntil;
    private boolean driving;
    private boolean drivingSuppressed;

    boolean driving() { return driving; }
    boolean hasActiveTimeout() { return mode == Mode.ACTIVE && !(driving && !drivingSuppressed); }
    void setDriving(boolean enabled, long now) {
        if (enabled == driving) return;
        driving = enabled;
        drivingSuppressed = false;
        if (enabled) activate(now);
        else if (mode == Mode.ACTIVE) activeUntil = now + ACTIVE_DURATION_MS;
    }

    Mode mode() { return mode; }
    long activeUntil() { return activeUntil; }
    void toggle(long now) {
        if (mode == Mode.ACTIVE) {
            mode = Mode.AMBIENT;
            activeUntil = 0;
            drivingSuppressed = driving;
        } else activate(now);
    }
    void activate(long now) {
        if (mode == Mode.CLOSED || mode == Mode.AUTHENTICATING) return;
        drivingSuppressed = false;
        mode = Mode.ACTIVE;
        activeUntil = now + ACTIVE_DURATION_MS;
    }
    void interact(long now) {
        if (mode == Mode.ACTIVE) activeUntil = now + ACTIVE_DURATION_MS;
    }
    void expire(long now) {
        if (hasActiveTimeout() && now >= activeUntil) mode = Mode.AMBIENT;
    }
    boolean beginAuthentication() {
        if (mode == Mode.CLOSED || mode == Mode.AUTHENTICATING) return false;
        mode = Mode.AUTHENTICATING;
        return true;
    }
    void cancelAuthentication() {
        if (mode == Mode.AUTHENTICATING)
            mode = driving && !drivingSuppressed ? Mode.ACTIVE : Mode.AMBIENT;
    }
    void unlocked() { mode = Mode.CLOSED; }
}
