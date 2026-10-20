package it.noxia.ambientplayer;

/** Recognizes split metadata/playback updates without depending on their callback order. */
final class TrackChangeTracker {
    record Change(boolean identity,boolean duration,boolean reset,boolean stalePosition) {
        boolean any() { return identity || duration || reset; }
    }
    private String identity;
    private long duration,position,stamp;
    private boolean initialized;
    Change observe(String nextIdentity,long nextDuration,long nextPosition,long nextStamp,boolean manual) {
        boolean changed=initialized && identity!=null && nextIdentity!=null && !identity.equals(nextIdentity);
        boolean resized=initialized && duration>0 && nextDuration>0 && duration!=nextDuration;
        boolean reset=initialized && nextPosition+2000<position;
        boolean stale=changed && nextPosition>2000 && nextStamp==stamp && Math.abs(nextPosition-position)<2000;
        if (nextIdentity!=null) identity=nextIdentity;
        if (nextDuration>0) duration=nextDuration;
        position=nextPosition;stamp=nextStamp;initialized=true;
        return manual?new Change(false,false,false,false):new Change(changed,resized,reset,stale);
    }
}
