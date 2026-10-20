package it.noxia.ambientplayer;

import android.content.Context;
import android.media.session.MediaController;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;

/** One compatibility subscription per supported session; no polling and no account access. */
@SuppressWarnings("deprecation")
final class SessionShuffle {
    private MediaControllerCompat controller;
    private boolean closed;
    private Boolean selected;
    private final Runnable changed;
    private final MediaControllerCompat.Callback callback=new MediaControllerCompat.Callback(){
        @Override public void onSessionReady(){update(controller.getShuffleMode());}
        @Override public void onShuffleModeChanged(int mode){update(mode);}
        @Override public void onSessionDestroyed(){update(-1);}
    };
    SessionShuffle(Context context,MediaController media,Handler handler,Runnable changed){
        this.changed=changed;
        try {
            controller=new MediaControllerCompat(context,MediaSessionCompat.Token.fromToken(media.getSessionToken()));
            controller.registerCallback(callback,handler);
            if(controller.isSessionReady())update(controller.getShuffleMode());
        }catch(RuntimeException unavailable){close();}
    }
    private void update(int mode){
        if(closed)return;
        Boolean next=ShuffleMode.selected(mode);
        if(!java.util.Objects.equals(next,selected)){selected=next;changed.run();}
    }
    Boolean selected(){return closed?null:selected;}
    static boolean offered(MediaController media,PlaybackState state){
        if(state==null)return false;
        if((state.getActions()&PlaybackStateCompat.ACTION_SET_SHUFFLE_MODE)!=0)return true;
        return media!=null && MusicSources.youtube(media.getPackageName()) && state.getCustomActions().stream().anyMatch(a->"shuffle_action".equals(a.getAction()));
    }
    boolean send(MediaController media,PlaybackState state){
        if(selected()==null || !offered(media,state))return false;
        try{controller.getTransportControls().setShuffleMode(selected?PlaybackStateCompat.SHUFFLE_MODE_NONE:PlaybackStateCompat.SHUFFLE_MODE_ALL);return true;}
        catch(RuntimeException unavailable){return false;}
    }
    void close(){closed=true;selected=null;if(controller!=null)controller.unregisterCallback(callback);}
}
