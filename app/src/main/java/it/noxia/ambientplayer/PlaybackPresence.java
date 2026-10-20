package it.noxia.ambientplayer;

import java.util.Objects;

/** Only definite inactivity expires a visible player. No timer while buffering/playing. */
final class PlaybackPresence {
    enum Kind { PLAYING, TRANSIENT, PAUSED, STOPPED, MISSING }
    static final long PAUSE_GRACE_MS=5_000,SESSION_GRACE_MS=15_000;
    private Kind previous;
    private Object session;
    private long since;
    long remaining(Kind kind,Object token,long now) {
        if(kind!=previous || !Objects.equals(session,token)){previous=kind;session=token;since=now;}
        if(kind==Kind.PLAYING || kind==Kind.TRANSIENT)return -1;
        long grace=kind==Kind.MISSING?SESSION_GRACE_MS:PAUSE_GRACE_MS;
        return Math.max(0,grace-Math.max(0,now-since));
    }
}
