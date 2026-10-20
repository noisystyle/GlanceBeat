package it.noxia.ambientplayer;
import org.junit.Test;
import static org.junit.Assert.*;
public class BitchordShuffleTest {
 @Test public void enabledUsesOnIconRatherThanActionLabel(){assertEquals(Boolean.TRUE,BitchordShuffle.selected(0xe043));}
 @Test public void disabledUsesOffIcon(){assertEquals(Boolean.FALSE,BitchordShuffle.selected(0xfe044));}
 @Test public void favoriteIconsCannotSelectShuffle(){assertNull(BitchordShuffle.selected(BitchordFavorite.FILLED));assertNull(BitchordShuffle.selected(BitchordFavorite.OUTLINE));}
 @Test public void unsupportedIconsRemainUnknown(){assertNull(BitchordShuffle.selected(-1));assertNull(BitchordShuffle.selected(123));}
}
