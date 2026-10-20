package it.noxia.ambientplayer;

/** Prevents duplicate launches and reopening immediately after the user dismisses the player. */
final class AutoPlayerGate {
    private boolean suppressed;
    private boolean requested;

    boolean request(boolean enabled, boolean permission, boolean playing, boolean locked, boolean surfaceOpen) {
        if (!enabled || !permission || !playing || !locked || surfaceOpen || suppressed || requested) return false;
        requested = true;
        return true;
    }
    void newLockCycle(boolean playerWasOpen) { suppressed = playerWasOpen; requested = false; }
    void newPlayback() { suppressed = false; }
    void userWokeScreen() { suppressed = false; }
    void dismissed() { suppressed = true; requested = false; }
    void opened() { requested = false; }
    boolean canRetry() { return !suppressed; }
    void requestExpired() { requested = false; }
}
