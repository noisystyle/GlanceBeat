package it.noxia.ambientplayer;
import org.junit.Test;
import static org.junit.Assert.*;
public class BitchordFavoriteTest {
    @Test public void supportedClientsAreExplicit(){
        assertTrue(MusicSources.supported("com.music.bitchord"));assertTrue(MusicSources.supported("com.google.android.apps.youtube.music"));assertTrue(MusicSources.supported("app.morphe.android.apps.youtube.music"));
        assertFalse(MusicSources.supported("com.music.bitchord.other"));assertFalse(MusicSources.supported(null));
    }
    @Test public void onlyBitchordUsesHearts(){assertTrue(MusicSources.hearts(MusicSources.BITCHORD));assertFalse(MusicSources.hearts(MusicSources.YOUTUBE));assertFalse(MusicSources.hearts(MusicSources.MORPHE));}
    @Test public void filledAndOutlineAreReadFromMedia3Codes(){assertEquals(Boolean.TRUE,BitchordFavorite.selected(0xfe87d));assertEquals(Boolean.FALSE,BitchordFavorite.selected(0xe87d));}
    @Test public void bitchord181StarsMapToTheSameFavoriteState(){assertEquals(Boolean.TRUE,BitchordFavorite.selected(0xfe838));assertEquals(Boolean.FALSE,BitchordFavorite.selected(0xe838));}
    @Test public void unknownIconNeverInventsASelection(){assertNull(BitchordFavorite.selected(-1));assertNull(BitchordFavorite.selected(123));}
    @Test public void olderStateCannotDescribeNewTrack(){assertFalse(BitchordFavorite.matches("old","new"));assertFalse(BitchordFavorite.matches("old",null));assertTrue(BitchordFavorite.matches("same","same"));assertTrue(BitchordFavorite.matches(null,"legacy"));}
}
