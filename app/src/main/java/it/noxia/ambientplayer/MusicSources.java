package it.noxia.ambientplayer;

/** Explicit music providers; unknown/video sessions cannot take over the lockscreen. */
final class MusicSources {
    static final String YOUTUBE="com.google.android.apps.youtube.music",MORPHE="app.morphe.android.apps.youtube.music",BITCHORD="com.music.bitchord";
    static final String SPOTIFY="com.spotify.music",APPLE="com.apple.android.music",TIDAL="com.aspiro.tidal",DEEZER="deezer.android.app",AMAZON="com.amazon.mp3";
    static final String NAMIDA="com.msob7y.namida",MUSICOLET="in.krosbits.musicolet";
    private static final java.util.Map<String,String> NAMES=java.util.Map.of(YOUTUBE,"YouTube Music",MORPHE,"YouTube Music",BITCHORD,"Bitchord",SPOTIFY,"Spotify",APPLE,"Apple Music",TIDAL,"Tidal",DEEZER,"Deezer",AMAZON,"Amazon Music",NAMIDA,"Namida",MUSICOLET,"Musicolet");
    static boolean supported(String source){return source!=null && NAMES.containsKey(source);}
    static boolean youtube(String source){return YOUTUBE.equals(source) || MORPHE.equals(source);}
    static boolean hearts(String source){return BITCHORD.equals(source);}
    static String name(String source){return source==null?"Music app":NAMES.getOrDefault(source,"Music app");}
    static String name(android.content.Context context,String source){return supported(source)?name(source):context.getString(R.string.music_app_name);}
    private MusicSources() { }
}
