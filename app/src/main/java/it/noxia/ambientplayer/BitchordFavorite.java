package it.noxia.ambientplayer;

/** Media3 legacy favorite state: hearts in BitChord 1.8, stars in 1.8.1. */
final class BitchordFavorite {
    static final String ACTION="com.music.bitchord.action.TOGGLE_FAVORITE";
    static final String ICON="androidx.media3.session.EXTRAS_KEY_COMMAND_BUTTON_ICON_COMPAT";
    static final String MEDIA_ID="androidx.media.PlaybackStateCompat.Extras.KEY_MEDIA_ID";
    static final int FILLED=0xfe87d,OUTLINE=0xe87d,STAR_FILLED=0xfe838,STAR_OUTLINE=0xe838;
    static Boolean selected(int icon){return icon==FILLED || icon==STAR_FILLED?Boolean.TRUE:icon==OUTLINE || icon==STAR_OUTLINE?Boolean.FALSE:null;}
    static boolean matches(String stateId,String metadataId){return stateId==null || stateId.equals(metadataId);}
    private BitchordFavorite() { }
}
