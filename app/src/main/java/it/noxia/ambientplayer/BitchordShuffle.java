package it.noxia.ambientplayer;

/** Media3 icon codes verified in the installed official Bitchord 1.8. */
final class BitchordShuffle {
    static final String ACTION="com.music.bitchord.action.TOGGLE_SHUFFLE";
    static final int ON=0xe043, OFF=0xfe044;
    static Boolean selected(int icon){return icon==ON?Boolean.TRUE:icon==OFF?Boolean.FALSE:null;}
    private BitchordShuffle() { }
}
