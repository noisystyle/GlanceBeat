package it.noxia.ambientplayer;

import android.app.ActivityOptions;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.net.Uri;
import android.os.Build;

/** Navigation only: never sends play/seek commands or clears the source application's task. */
final class MusicLaunch {
    record Request(String source,MediaSession.Token token,PendingIntent sessionActivity,Intent direct,Intent track){}
    static Request resolve(Context context,MediaController media){
        if(media==null || !MusicSources.supported(media.getPackageName()))return null;
        String source=media.getPackageName();Intent launcher=context.getPackageManager().getLaunchIntentForPackage(source);
        Intent direct=null;
        if(MusicSources.BITCHORD.equals(source) && launcher!=null && launcher.getComponent()!=null){
            // Same entry point as BitChord 1.8's artwork widget. ACTION_MAIN/CATEGORY_LAUNCHER
            // can merely raise an existing singleTask without delivering its player request.
            direct=new Intent().setComponent(launcher.getComponent()).putExtra("bitchord.openPlayer",true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        }
        PendingIntent activity=media.getSessionActivity();
        if(activity!=null && (!activity.isActivity() || !source.equals(activity.getCreatorPackage())))activity=null;
        MediaMetadata metadata=media.getMetadata();
        String link=metadata==null?null:MusicLinks.track(source,metadata.getString(MediaMetadata.METADATA_KEY_MEDIA_URI),metadata.getString(MediaMetadata.METADATA_KEY_MEDIA_ID));
        Intent track=link==null?null:new Intent(Intent.ACTION_VIEW,Uri.parse(link)).setPackage(source)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return direct==null && activity==null && track==null?null:new Request(source,media.getSessionToken(),activity,direct,track);
    }
    static boolean open(Context context,Request requested,MediaController current){
        if(requested==null || current==null || !requested.source().equals(current.getPackageName()))return false;
        // Authentication may outlast a song or session. Resolve the live now-playing entry
        // again, instead of opening a stale track link captured before unlock.
        Request fresh=resolve(context,current);if(fresh==null)return false;
        if(start(context,fresh.direct()))return true;
        if(fresh.sessionActivity()!=null)try{
            fresh.sessionActivity().send(context,0,null,null,null,null,options().toBundle());return true;
        }catch(PendingIntent.CanceledException|SecurityException unavailable){/* Try the same provider's track link. */}
        return start(context,fresh.track());
    }
    private static boolean start(Context context,Intent intent){
        if(intent==null)return false;
        try{context.startActivity(intent);return true;}
        catch(android.content.ActivityNotFoundException|SecurityException unavailable){return false;}
    }
    static ActivityOptions options(){
        ActivityOptions options=ActivityOptions.makeBasic();
        if(Build.VERSION.SDK_INT>=36)options.setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_IF_VISIBLE);
        else if(Build.VERSION.SDK_INT>=34)options.setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED);
        return options;
    }
    private MusicLaunch(){}
}
