package it.noxia.ambientplayer;
import org.junit.Test;
import static org.junit.Assert.*;
public class CarouselIdentityTest {
 @Test public void repeatedTitleDoesNotConfirmAnotherMediaId(){assertFalse(CarouselIdentity.matches(new QueueSelection.Item(12,"b"),"Titolo","Artista","a","Titolo","Artista"));}
 @Test public void matchingMediaIdAllowsUpdatedSpelling(){assertTrue(CarouselIdentity.matches(new QueueSelection.Item(12,"b"),"Titolo coda","Artista","b","Titolo aggiornato","Altro artista"));}
 @Test public void missingMediaIdDoesNotMatchNamedTarget(){assertFalse(CarouselIdentity.matches(new QueueSelection.Item(12,"b"),"Titolo","Artista",null,"Titolo","Artista"));}
 @Test public void IdlessMetadataNeedsArtistAsWellAsTitle(){assertFalse(CarouselIdentity.matches(new QueueSelection.Item(12,null),"Titolo","Primo",null,"Titolo","Secondo"));assertTrue(CarouselIdentity.matches(new QueueSelection.Item(12,null),"Titolo","Primo",null,"Titolo","Primo"));}
 @Test public void unknownArtistIsNormalizedButUnknownTitleIsNotConfirmation(){assertTrue(CarouselIdentity.matches(new QueueSelection.Item(12,null),"Titolo","",null,"Titolo",null));assertFalse(CarouselIdentity.matches(new QueueSelection.Item(12,null),null,null,null,null,null));}
}
