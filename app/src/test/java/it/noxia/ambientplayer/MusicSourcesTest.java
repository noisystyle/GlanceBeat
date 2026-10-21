package it.noxia.ambientplayer;
import org.junit.Test;import static org.junit.Assert.*;
public class MusicSourcesTest {
 @Test public void allRequestedProvidersAreRecognized(){for(String source:new String[]{MusicSources.SPOTIFY,MusicSources.YOUTUBE,MusicSources.MORPHE,MusicSources.APPLE,MusicSources.TIDAL,MusicSources.DEEZER,MusicSources.AMAZON,MusicSources.BITCHORD,MusicSources.NAMIDA,MusicSources.MUSICOLET})assertTrue(source,MusicSources.supported(source));}
 @Test public void unrelatedAndImpersonatingPackagesCannotTriggerPlayer(){for(String source:new String[]{"com.netflix.mediaclient","com.spotify.music.fake","com.apple.android.music.beta","com.google.android.youtube","com.msob7y.namida.fake","in.krosbits.musicolet.fake",""})assertFalse(source,MusicSources.supported(source));assertFalse(MusicSources.supported(null));}
 @Test public void openingTheCurrentProviderUsesItsOwnName(){assertEquals("Spotify",MusicSources.name(MusicSources.SPOTIFY));assertEquals("Apple Music",MusicSources.name(MusicSources.APPLE));assertEquals("Amazon Music",MusicSources.name(MusicSources.AMAZON));assertEquals("Tidal",MusicSources.name(MusicSources.TIDAL));assertEquals("Deezer",MusicSources.name(MusicSources.DEEZER));assertEquals("Namida",MusicSources.name(MusicSources.NAMIDA));assertEquals("Musicolet",MusicSources.name(MusicSources.MUSICOLET));}
}
