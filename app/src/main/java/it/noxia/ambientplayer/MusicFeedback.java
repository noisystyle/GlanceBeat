package it.noxia.ambientplayer;

import android.media.MediaMetadata;
import android.media.Rating;
import android.media.session.MediaController;
import android.media.session.PlaybackState;
import java.util.Locale;

/** Use only commands and state published by the current client's media session. */
final class MusicFeedback {
    static boolean bitchord(MediaController media){return media!=null && MusicSources.hearts(media.getPackageName());}
    static boolean hearts(MediaController media){return media!=null && (bitchord(media) || media.getRatingType()==Rating.RATING_HEART);}
    static PlaybackState.CustomAction favoriteAction(PlaybackState state){
        if(state!=null)for(PlaybackState.CustomAction action:state.getCustomActions())if(BitchordFavorite.ACTION.equals(action.getAction()))return action;
        return null;
    }
    static Boolean favoriteState(PlaybackState state,MediaMetadata metadata){
        PlaybackState.CustomAction action=favoriteAction(state);
        if(action==null || action.getExtras()==null || metadata==null)return null;
        String stateId=state.getExtras()==null?null:state.getExtras().getString(BitchordFavorite.MEDIA_ID);
        if(!BitchordFavorite.matches(stateId,metadata.getString(MediaMetadata.METADATA_KEY_MEDIA_ID)))return null;
        return BitchordFavorite.selected(action.getExtras().getInt(BitchordFavorite.ICON,-1));
    }
    static boolean ready(MediaController media,PlaybackState state,MediaMetadata metadata){return !bitchord(media) || favoriteState(state,metadata)!=null;}
    static PlaybackState.CustomAction action(MediaController media, boolean like) {
        PlaybackState state = media == null ? null : media.getPlaybackState();
        if (state == null) return null;
        if(bitchord(media))return like?favoriteAction(state):null;
        if(media==null || !MusicSources.youtube(media.getPackageName()))return null;
        for (PlaybackState.CustomAction item : state.getCustomActions()) {
            String id = item.getAction().toLowerCase(Locale.ROOT);
            boolean down = id.contains("thumbs_down") || id.contains("thumb_down") || id.contains("dislike");
            boolean up = !down && (id.contains("thumbs_up") || id.contains("thumb_up") || id.contains("like"));
            if (like ? up : down) return item;
        }
        return null;
    }
    static boolean available(MediaController media, boolean like) {
        if(bitchord(media))return like && favoriteAction(media.getPlaybackState())!=null;
        if (action(media, like) != null) return true;
        PlaybackState state = media == null ? null : media.getPlaybackState();
        return state != null && (state.getActions() & PlaybackState.ACTION_SET_RATING) != 0
            && (media.getRatingType() == Rating.RATING_THUMB_UP_DOWN || like && media.getRatingType()==Rating.RATING_HEART);
    }
    record Target(android.media.session.MediaSession.Token token,String identity) { }
    static String identity(MediaMetadata metadata) {
        if(metadata==null)return null;
        String id=metadata.getString(MediaMetadata.METADATA_KEY_MEDIA_ID);
        if(id!=null && !id.isBlank())return "id:"+id;
        String title=metadata.getString(MediaMetadata.METADATA_KEY_TITLE),artist=metadata.getString(MediaMetadata.METADATA_KEY_ARTIST);
        return title==null?null:"text:"+title+"\u0000"+(artist==null?"":artist);
    }
    static Target target(MediaController media,MediaMetadata metadata){return media==null || identity(metadata)==null?null:new Target(media.getSessionToken(),identity(metadata));}
    static boolean selected(MediaController media, boolean like) {return selected(media,media==null?null:media.getPlaybackState(),media==null?null:media.getMetadata(),like);}
    static boolean selected(MediaController media,PlaybackState state,MediaMetadata metadata,boolean like){
        return bitchord(media)?like && Boolean.TRUE.equals(favoriteState(state,metadata)):selected(metadata,like);
    }
    static boolean selected(MediaMetadata metadata, boolean like) {
        Rating rating = metadata == null ? null : metadata.getRating(MediaMetadata.METADATA_KEY_USER_RATING);
        return rating!=null && rating.isRated() && (rating.getRatingStyle()==Rating.RATING_HEART?like && rating.hasHeart():rating.getRatingStyle()==Rating.RATING_THUMB_UP_DOWN && rating.isThumbUp()==like);
    }
    static boolean send(MediaController media, boolean like) {return send(media,like,media==null?null:media.getMetadata());}
    static boolean send(MediaController media,boolean like,MediaMetadata metadata) {
        if (media == null) return false;
        if(bitchord(media) && (!like || !ready(media,media.getPlaybackState(),metadata)))return false;
        PlaybackState.CustomAction offered = action(media, like);
        if (offered != null) {
            media.getTransportControls().sendCustomAction(offered.getAction(), null);
            return true;
        }
        if (!available(media, like)) return false;
        if(hearts(media)){media.getTransportControls().setRating(Rating.newHeartRating(!selected(metadata,true)));return true;}
        media.getTransportControls().setRating(selected(metadata, like)
            ? Rating.newUnratedRating(Rating.RATING_THUMB_UP_DOWN) : Rating.newThumbRating(like));
        return true;
    }
    private MusicFeedback() { }
}
