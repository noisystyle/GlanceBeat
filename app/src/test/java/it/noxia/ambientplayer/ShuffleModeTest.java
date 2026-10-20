package it.noxia.ambientplayer;
import org.junit.Test;import static org.junit.Assert.*;
public class ShuffleModeTest {
 @Test public void disabledIsOff(){assertEquals(Boolean.FALSE,ShuffleMode.selected(0));}
 @Test public void allAndGroupAreOn(){assertEquals(Boolean.TRUE,ShuffleMode.selected(1));assertEquals(Boolean.TRUE,ShuffleMode.selected(2));}
 @Test public void unavailableOrUnknownNeverInventOff(){assertNull(ShuffleMode.selected(-1));assertNull(ShuffleMode.selected(3));}
}
