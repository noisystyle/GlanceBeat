package it.noxia.ambientplayer;
import org.junit.Test;
import static org.junit.Assert.*;
public class PlaybackPresenceTest {
 @Test public void longBufferingDoesNotDismissListeningSession(){var p=new PlaybackPresence();assertEquals(-1,p.remaining(PlaybackPresence.Kind.PLAYING,"a",0));assertEquals(-1,p.remaining(PlaybackPresence.Kind.TRANSIENT,"a",100));assertEquals(-1,p.remaining(PlaybackPresence.Kind.TRANSIENT,"a",120000));}
 @Test public void actualStablePauseExpiresWithoutPolling(){var p=new PlaybackPresence();assertEquals(5000,p.remaining(PlaybackPresence.Kind.PAUSED,"a",100));assertEquals(0,p.remaining(PlaybackPresence.Kind.PAUSED,"a",5100));}
 @Test public void bufferCancelsPauseDeadlineAndNextPauseGetsItsOwnGrace(){var p=new PlaybackPresence();p.remaining(PlaybackPresence.Kind.PAUSED,"a",100);assertEquals(-1,p.remaining(PlaybackPresence.Kind.TRANSIENT,"a",5100));assertEquals(5000,p.remaining(PlaybackPresence.Kind.PAUSED,"a",5200));}
 @Test public void temporaryMissingSessionOrListenerHasFiniteGrace(){var p=new PlaybackPresence();assertEquals(15000,p.remaining(PlaybackPresence.Kind.MISSING,null,100));assertEquals(9000,p.remaining(PlaybackPresence.Kind.MISSING,null,6100));assertEquals(-1,p.remaining(PlaybackPresence.Kind.PLAYING,"b",6200));}
 @Test public void trueMissingSessionEventuallyCloses(){var p=new PlaybackPresence();p.remaining(PlaybackPresence.Kind.MISSING,null,100);assertEquals(0,p.remaining(PlaybackPresence.Kind.MISSING,null,15100));}
 @Test public void replacementSessionDoesNotInheritOldPauseTimeout(){var p=new PlaybackPresence();p.remaining(PlaybackPresence.Kind.PAUSED,"a",100);assertEquals(5000,p.remaining(PlaybackPresence.Kind.PAUSED,"b",5100));}
}
