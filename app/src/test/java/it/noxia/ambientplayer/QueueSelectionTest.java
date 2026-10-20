package it.noxia.ambientplayer;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;
public class QueueSelectionTest {
 private final QueueSelection.Item a=new QueueSelection.Item(7,"video-a");
 @Test public void usesQueueIdForPublishedRow(){assertEquals(QueueSelection.Command.QUEUE_ID,QueueSelection.command(a,List.of(a),true,true,false));}
 @Test public void usesMediaIdWhenDirectSelectionIsUnavailable(){assertEquals(QueueSelection.Command.MEDIA_ID,QueueSelection.command(a,List.of(a),true,false,true));}
 @Test public void neverInventsMediaId(){var row=new QueueSelection.Item(7,null);assertEquals(QueueSelection.Command.NONE,QueueSelection.command(row,List.of(row),true,false,true));}
 @Test public void acceptsPublicQueueIdWithoutMediaId(){var row=new QueueSelection.Item(7,null);assertEquals(QueueSelection.Command.QUEUE_ID,QueueSelection.command(row,List.of(row),true,true,false));}
 @Test public void rejectsReplacedSession(){assertEquals(QueueSelection.Command.NONE,QueueSelection.command(a,List.of(a),false,true,true));}
 @Test public void rejectsRemovedRow(){assertEquals(QueueSelection.Command.NONE,QueueSelection.command(a,List.of(),true,true,true));}
 @Test public void rejectsReusedIdForDifferentTrack(){assertEquals(QueueSelection.Command.NONE,QueueSelection.command(a,List.of(new QueueSelection.Item(7,"video-b")),true,true,true));}
 @Test public void needsPlaybackConfirmation(){assertFalse(QueueSelection.confirmed(a,7,"video-a",false));assertTrue(QueueSelection.confirmed(a,7,null,true));}
 @Test public void acceptsMetadataConfirmationWhenQueueIdIsDelayed(){assertTrue(QueueSelection.confirmed(a,-1,"video-a",true));assertFalse(QueueSelection.confirmed(a,-1,"video-b",true));}
 @Test public void absentMetadataDoesNotConfirmUnknownItem(){assertFalse(QueueSelection.confirmed(new QueueSelection.Item(7,null),-1,null,true));}
}
