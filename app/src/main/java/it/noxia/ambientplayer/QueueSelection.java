package it.noxia.ambientplayer;

import java.util.List;
import java.util.Objects;

/** Queue ids are scoped to a session and must still identify the row the user saw. */
final class QueueSelection {
    record Item(long id,String mediaId) { }
    enum Command { NONE, QUEUE_ID, MEDIA_ID }
    static Command command(Item selected,List<Item> fresh,boolean sameSession,boolean skip,boolean playFromId) {
        if(!sameSession || selected==null || fresh.stream().noneMatch(item->item.id()==selected.id() && Objects.equals(item.mediaId(),selected.mediaId())))return Command.NONE;
        if(skip)return Command.QUEUE_ID;
        return playFromId && selected.mediaId()!=null && !selected.mediaId().trim().isEmpty()?Command.MEDIA_ID:Command.NONE;
    }
    static boolean confirmed(Item selected,long activeId,String mediaId,boolean playing) {
        return selected!=null && playing && (selected.id()==activeId
            || selected.mediaId()!=null && !selected.mediaId().trim().isEmpty() && Objects.equals(selected.mediaId(),mediaId));
    }
    private QueueSelection() { }
}
