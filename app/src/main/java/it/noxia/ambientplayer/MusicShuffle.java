package it.noxia.ambientplayer;

import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.PlaybackState;

/** Event-driven shuffle control using only the action and state offered by the selected music client. */
final class MusicShuffle {
    static PlaybackState.CustomAction action(PlaybackState state){
        if(state!=null)for(var action:state.getCustomActions())if(BitchordShuffle.ACTION.equals(action.getAction()))return action;
        return null;
    }
    static boolean available(MediaController media,PlaybackState state){
        return media!=null && (MusicSources.hearts(media.getPackageName())?action(state)!=null:MusicSources.supported(media.getPackageName()) && SessionShuffle.offered(media,state));
    }
    static Boolean selected(MediaController media,PlaybackState state,MediaMetadata metadata){
        if(media!=null && !MusicSources.hearts(media.getPackageName())){var bridge=MusicSessionService.sessionShuffle(media);return bridge==null?null:bridge.selected();}
        var action=action(state);
        if(action==null || action.getExtras()==null || metadata==null)return null;
        String id=state.getExtras()==null?null:state.getExtras().getString(BitchordFavorite.MEDIA_ID);
        if(!BitchordFavorite.matches(id,metadata.getString(MediaMetadata.METADATA_KEY_MEDIA_ID)))return null;
        return BitchordShuffle.selected(action.getExtras().getInt(BitchordFavorite.ICON,-1));
    }
    static boolean send(MediaController media,MediaMetadata metadata){
        var state=media==null?null:media.getPlaybackState();
        if(!available(media,state) || selected(media,state,metadata)==null)return false;
        if(!MusicSources.hearts(media.getPackageName())){var bridge=MusicSessionService.sessionShuffle(media);return bridge!=null && bridge.send(media,state);}
        media.getTransportControls().sendCustomAction(action(state).getAction(),null);
        return true;
    }
    private MusicShuffle() { }
}
