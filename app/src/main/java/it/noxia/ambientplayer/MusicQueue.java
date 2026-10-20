package it.noxia.ambientplayer;

import android.graphics.Bitmap;
import android.media.MediaDescription;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import java.util.List;
import java.util.ArrayList;
import java.util.Objects;

/** Only public session data and transport commands; no network or queue reconstruction. */
final class MusicQueue {
    record Entry(QueueSelection.Item key,String title,String artist,Bitmap artwork,android.net.Uri artworkUri) {
        String title(android.content.Context context) { return title.isBlank()?context.getString(R.string.untitled_track):title; }
    }
    record Snapshot(MediaSession.Token token,List<Entry> entries,int current,boolean skip,boolean legacySkip,boolean fromId,boolean playing,String mediaId) {
        static final Snapshot EMPTY=new Snapshot(null,List.of(),-1,false,false,false,false,null);
        boolean selectable(Entry entry) {
            return skip || legacySkip || fromId && entry.key.mediaId()!=null && !entry.key.mediaId().trim().isEmpty();
        }
        List<QueueSelection.Item> keys() { return entries.stream().map(Entry::key).collect(java.util.stream.Collectors.toList()); }
    }
    static Snapshot read() {
        MediaController media=MusicSessionService.current;
        if(media==null)return Snapshot.EMPTY;
        try {
            List<MediaSession.QueueItem> queue=media.getQueue();
            PlaybackState state=MusicSessionService.playbackState();
            MediaMetadata metadata=media.getMetadata();
            String mediaId=metadata==null?null:metadata.getString(MediaMetadata.METADATA_KEY_MEDIA_ID);
            long active=state==null?MediaSession.QueueItem.UNKNOWN_ID:state.getActiveQueueItemId();
            var entries=new ArrayList<Entry>();int current=-1;
            if(queue!=null)for(var item:queue) {
                if(item==null || item.getQueueId()==MediaSession.QueueItem.UNKNOWN_ID)continue;
                MediaDescription d=item.getDescription();
                String title=d.getTitle()==null?"":d.getTitle().toString();
                String artist=d.getSubtitle()==null?"":d.getSubtitle().toString();
                if(item.getQueueId()==active)current=entries.size();
                entries.add(new Entry(new QueueSelection.Item(item.getQueueId(),d.getMediaId()),title,artist,d.getIconBitmap(),d.getIconUri()));
            }
            if(current<0 && mediaId!=null) {
                int found=-1;
                for(int i=0;i<entries.size();i++)if(Objects.equals(entries.get(i).key.mediaId(),mediaId)) {
                    if(found>=0){found=-1;break;}found=i;
                }
                current=found;
            }
            long actions=state==null?0:state.getActions();
            return new Snapshot(media.getSessionToken(),List.copyOf(entries),current,
                (actions&PlaybackState.ACTION_SKIP_TO_QUEUE_ITEM)!=0,
                // YouTube Music implements the public queue callback but may omit its action flag.
                // Retain strict session/id validation and require actual playback confirmation.
                !entries.isEmpty() && java.util.Set.of("com.google.android.apps.youtube.music","app.morphe.android.apps.youtube.music").contains(media.getPackageName()),
                (actions&PlaybackState.ACTION_PLAY_FROM_MEDIA_ID)!=0,
                state!=null && state.getState()==PlaybackState.STATE_PLAYING,mediaId);
        } catch(SecurityException|IllegalStateException unavailable) { return Snapshot.EMPTY; }
    }
    static boolean select(MediaSession.Token shownToken,Entry selected) {
        Snapshot fresh=read();MediaController media=MusicSessionService.current;
        QueueSelection.Command command=QueueSelection.command(selected.key,fresh.keys(),
            shownToken!=null && shownToken.equals(fresh.token),fresh.skip || fresh.legacySkip,fresh.fromId);
        boolean sameDescription=selected.key.mediaId()!=null || fresh.entries.stream().anyMatch(e->e.key.id()==selected.key.id()
            && e.title.equals(selected.title) && e.artist.equals(selected.artist));
        if(media==null || command==QueueSelection.Command.NONE || !sameDescription)return false;
        try {
            if(command==QueueSelection.Command.QUEUE_ID)media.getTransportControls().skipToQueueItem(selected.key.id());
            else media.getTransportControls().playFromMediaId(selected.key.mediaId(),null);
            android.util.Log.i("AmbientQueue","selection_sent command="+command+" queueId="+selected.key.id());
            return true;
        } catch(SecurityException|IllegalStateException unavailable) { return false; }
    }
    static boolean previousSupported(Snapshot fresh,Entry target) {
        PlaybackState state=MusicSessionService.playbackState();
        return state!=null && (state.getActions()&PlaybackState.ACTION_SKIP_TO_PREVIOUS)!=0 || fresh.fromId() && target.key().mediaId()!=null && !target.key().mediaId().isBlank();
    }
    static boolean selectObservedPrevious(MediaSession.Token shownToken,QueueSelection.Item shownCurrent,Entry selected) {
        Snapshot fresh=read();MediaController media=MusicSessionService.current;Entry previous=MusicHistory.previous(fresh);
        if(media==null || shownToken==null || !shownToken.equals(fresh.token()) || fresh.current()<0
            || !fresh.entries().get(fresh.current()).key().equals(shownCurrent) || previous==null
            || !previous.key().equals(selected.key()) || !previous.title().equals(selected.title()) || !previous.artist().equals(selected.artist()))return false;
        if(fresh.entries().stream().anyMatch(e->e.key().equals(selected.key())))return select(shownToken,selected);
        try {
            if(fresh.fromId() && selected.key().mediaId()!=null && !selected.key().mediaId().isBlank())media.getTransportControls().playFromMediaId(selected.key().mediaId(),null);
            else {
                PlaybackState state=MusicSessionService.playbackState();
                if(state==null || (state.getActions()&PlaybackState.ACTION_SKIP_TO_PREVIOUS)==0)return false;
                media.getTransportControls().skipToPrevious();
            }
            android.util.Log.i("AmbientCovers","observed_previous_sent queueId="+selected.key().id());return true;
        }catch(SecurityException|IllegalStateException unavailable){return false;}
    }
    static boolean confirmed(Snapshot fresh,MediaSession.Token token,QueueSelection.Item selected) {
        long active=fresh.current<0?MediaSession.QueueItem.UNKNOWN_ID:fresh.entries.get(fresh.current).key.id();
        return token!=null && token.equals(fresh.token) && QueueSelection.confirmed(selected,active,fresh.mediaId,fresh.playing);
    }
    private MusicQueue() { }
}
