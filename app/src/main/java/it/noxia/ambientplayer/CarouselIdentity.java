package it.noxia.ambientplayer;

import java.util.Objects;

/** A repeated title is not a track identity; ID-less sessions need both title and artist. */
final class CarouselIdentity {
    static boolean matches(QueueSelection.Item target,String queueTitle,String queueArtist,String mediaId,String title,String artist){
        if(target==null)return false;
        if(target.mediaId()!=null && !target.mediaId().isBlank())return Objects.equals(target.mediaId(),mediaId);
        return queueTitle!=null && Objects.equals(queueTitle,title) && Objects.equals(queueArtist==null?"":queueArtist,artist==null?"":artist);
    }
    private CarouselIdentity(){}
}
