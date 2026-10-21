package it.noxia.ambientplayer;

import android.app.*;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.PlaybackState;
import android.os.*;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import java.io.*;
import java.lang.reflect.Field;
import java.util.function.BooleanSupplier;
import org.json.*;

/** Real installed local players, generated audio only; never creates a MediaSession. */
final class LocalProviderChecks {
    private final Instrumentation host;
    private final String provider;
    private final JSONArray checks=new JSONArray();
    private final JSONObject report=new JSONObject();
    private MainActivity activity;
    private PlayerView scene;
    private PlayerSurface surface;
    private MediaController source;
    private final JSONArray sessionEvents=new JSONArray();
    private final Runnable sessionObserver=()->{try{if(source!=null && sessionEvents.length()<100){MediaController m=MusicSessionService.current;sessionEvents.put(new JSONObject().put("present",m!=null).put("same_session",m!=null && source.getSessionToken().equals(m.getSessionToken())).put("title",m==null || m.getMetadata()==null?JSONObject.NULL:m.getMetadata().getString(MediaMetadata.METADATA_KEY_TITLE)));}}catch(Exception ignored){}};
    private LocalProviderChecks(Instrumentation h,String p){host=h;provider=p;}
    static void run(Instrumentation h,String p){new LocalProviderChecks(h,p).run();}
    private void main(Runnable r){host.runOnMainSync(r);}
    private Object get(Object o,String name){for(Class<?> c=o instanceof Class?(Class<?>)o:o.getClass();c!=null;c=c.getSuperclass())try{Field f=c.getDeclaredField(name);f.setAccessible(true);return f.get(o instanceof Class?null:o);}catch(NoSuchFieldException ignored){}catch(Exception e){throw new RuntimeException(e);}throw new RuntimeException(name);}
    private void check(String name,boolean passed)throws Exception{checks.put(new JSONObject().put("name",name).put("passed",passed));if(!passed)throw new AssertionError(name);}
    private String shell(String command)throws Exception{try(var fd=host.getUiAutomation().executeShellCommand(command);var in=new FileInputStream(fd.getFileDescriptor())){return new String(in.readAllBytes());}}
    private void await(BooleanSupplier condition)throws Exception{long until=SystemClock.uptimeMillis()+8000;boolean[] ok={false};do{main(()->ok[0]=condition.getAsBoolean());if(!ok[0])Thread.sleep(50);}while(!ok[0] && SystemClock.uptimeMillis()<until);if(!ok[0])throw new AssertionError("Real provider did not confirm expected state");}
    private void event(long down,int action,float x,float y){MotionEvent e=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,x,y,0);e.setSource(InputDevice.SOURCE_TOUCHSCREEN);if(!host.getUiAutomation().injectInputEvent(e,true))throw new AssertionError("Input injection failed");e.recycle();}
    private void tap(float x,float y){long down=SystemClock.uptimeMillis();event(down,0,x,y);SystemClock.sleep(40);event(down,1,x,y);}
    private void drag(float x,float y,float dx,float dy,long hold){long down=SystemClock.uptimeMillis();event(down,0,x,y);SystemClock.sleep(hold);for(int i=1;i<=12;i++){SystemClock.sleep(25);event(down,2,x+dx*i/12,y+dy*i/12);}event(down,1,x+dx,y+dy);}
    private float scale(){return Math.min(scene.getWidth()/400f,scene.getHeight()/650f);}
    private float y(float offset){return scene.getHeight()/2f+scene.contentOffsetY()+offset*scale();}
    private String title(){MediaMetadata m=source.getMetadata();return m==null?null:m.getString(MediaMetadata.METADATA_KEY_TITLE);}
    private Rect queueRow(AccessibilityNodeInfo node,String title){if(node==null)return null;CharSequence d=node.getContentDescription();if(node.isClickable() && d!=null && d.toString().contains(title)){Rect r=new Rect();node.getBoundsInScreen(r);return r;}for(int i=0;i<node.getChildCount();i++){Rect r=queueRow(node.getChild(i),title);if(r!=null)return r;}return null;}
    private void capture(String suffix)throws Exception{Thread.sleep(350);Bitmap b=host.getUiAutomation().takeScreenshot();try(var out=new FileOutputStream(new File(host.getTargetContext().getFilesDir(),"local-"+provider+"-"+suffix+".png"))){b.compress(Bitmap.CompressFormat.PNG,100,out);}b.recycle();}
    private void run(){Bundle status=new Bundle();boolean passed=false;try{
        if(!Build.FINGERPRINT.contains("generic") && !Build.MODEL.contains("sdk"))throw new AssertionError("Emulator only");
        check("requested_local_provider_is_allowlisted",MusicSources.NAMIDA.equals(provider) || MusicSources.MUSICOLET.equals(provider));
        main(()->PlayerPreferences.setEnabled(host.getTargetContext(),true));
        shell("cmd appops set "+host.getTargetContext().getPackageName()+" SYSTEM_ALERT_WINDOW allow");
        shell("cmd notification allow_listener "+host.getTargetContext().getPackageName()+"/it.noxia.ambientplayer.MusicSessionService");
        await(()->MusicSessionService.current!=null && provider.equals(MusicSessionService.current.getPackageName()) && MusicSessionService.isPlaying());source=MusicSessionService.current;
        main(()->MusicSessionService.addListener(sessionObserver));
        check("real_provider_session_selected",provider.equals(source.getPackageName()));
        check("generated_local_audio_only",title()!=null && title().startsWith("GlanceBeat Sample "));
        report.put("provider",provider).put("version",host.getTargetContext().getPackageManager().getPackageInfo(provider,0).versionName).put("synthetic_session",false).put("service_login",false);
        PlaybackState state=source.getPlaybackState();report.put("actions",state.getActions()).put("rating_type",source.getRatingType()).put("session_activity",source.getSessionActivity()!=null);
        JSONArray custom=new JSONArray();for(var a:state.getCustomActions())custom.put(new JSONObject().put("action",a.getAction()).put("name",a.getName()));report.put("custom_actions",custom);
        MusicQueue.Snapshot seed=MusicQueue.read();
        if(seed.current()>0 && seed.selectable(seed.entries().get(0))){MusicQueue.Entry first=seed.entries().get(0);main(()->MusicQueue.select(seed.token(),first));await(()->MusicQueue.confirmed(MusicQueue.read(),seed.token(),first.key()));source=MusicSessionService.current;Thread.sleep(500);}
        MusicQueue.Snapshot q=MusicQueue.read();JSONArray items=new JSONArray();for(var e:q.entries())items.put(new JSONObject().put("id",e.key().id()).put("media_id",e.key().mediaId()).put("title",e.title()).put("artwork_bitmap",e.artwork()!=null).put("artwork_uri",e.artworkUri()==null?JSONObject.NULL:e.artworkUri().toString()).put("selectable",q.selectable(e)));report.put("queue",items).put("current_index",q.current());
        shell("locksettings set-pin 1234");shell("input keyevent KEYCODE_SLEEP");Thread.sleep(2200);
        shell("am start -n "+host.getTargetContext().getPackageName()+"/it.noxia.ambientplayer.MainActivity");Thread.sleep(700);
        await(()->{var ref=(java.lang.ref.WeakReference<?>)get(MainActivity.class,"surface");return ref!=null && ref.get()!=null && !((MainActivity)ref.get()).isFinishing();});
        activity=(MainActivity)((java.lang.ref.WeakReference<?>)get(MainActivity.class,"surface")).get();scene=(PlayerView)get(activity,"player");surface=(PlayerSurface)get(activity,"widgets");
        check("real_music_player_shown_over_native_keyguard",!scene.isActiveTarget() && host.getTargetContext().getSystemService(KeyguardManager.class).isKeyguardLocked());
        capture("ambient");
        shell("input tap 540 400");Thread.sleep(100);shell("input tap 540 400");Thread.sleep(800);
        check("double_tap_activates_controls",scene.isActiveTarget());
        check("current_cover_loaded",get(scene,"contentArtwork")!=null);
        float x=scene.getWidth()/2f,s=scale();drag(x-90*s,y(-60),170*s,0,0);Thread.sleep(700);
        check("right_swipe_opens_queue_page",surface.queueOpen());
        check("queue_matches_real_provider",surface.queueItems()==q.entries().size()-Math.max(0,q.current()));
        capture("queue");
        if(q.entries().size()>=3 && q.current()>=0 && q.entries().stream().allMatch(q::selectable)){
            MusicQueue.Entry target=q.entries().get((q.current()+1)%q.entries().size());
            Rect row=queueRow(host.getUiAutomation().getRootInActiveWindow(),target.title());check("queue_song_has_real_clickable_row",row!=null);
            tap(row.exactCenterX(),row.exactCenterY());await(()->MusicQueue.confirmed(MusicQueue.read(),q.token(),target.key()) && !surface.queueOpen());
            check("queue_tap_starts_exact_song_and_returns_to_player",target.title().equals(title()));
            await(()->!scene.adjacentPending() && !scene.isContentTransitionRunning());
            await(()->!surface.queueAnimating() && scene.adjacentReady()>0);check("adjacent_queue_cover_available",scene.adjacentReady()>0);
            MusicQueue.Snapshot before=MusicQueue.read();
            if(before.current()>0){String previous=before.entries().get(before.current()-1).title();drag(x,y(-178),150*s,0,0);await(()->previous.equals(title()) && !scene.adjacentPending());check("artwork_swipe_previous_confirmed",previous.equals(title()));}
            before=MusicQueue.read();
            if(before.current()+1<before.entries().size()){String next=before.entries().get(before.current()+1).title();Thread.sleep(650);drag(x,y(-178),-150*s,0,0);await(()->next.equals(title()) && !scene.adjacentPending());check("artwork_swipe_next_confirmed",next.equals(title()));}
            report.put("queue_selection_verified",true);
        }else{report.put("queue_selection_verified",false);main(()->surface.closeQueue(false));}
        Thread.sleep(700);tap(x,y(58));await(()->source.getPlaybackState().getState()==PlaybackState.STATE_PAUSED);check("pause_button_acknowledged",source.getPlaybackState().getState()==PlaybackState.STATE_PAUSED);
        tap(x,y(58));await(()->source.getPlaybackState().getState()==PlaybackState.STATE_PLAYING);check("play_button_acknowledged",source.getPlaybackState().getState()==PlaybackState.STATE_PLAYING);
        report.put("queue_artwork_cached",surface.queueArtworkCached()).put("feedback_available",MusicFeedback.available(source,true)).put("shuffle_available",MusicShuffle.available(source,source.getPlaybackState()));
        capture("player");passed=true;
    }catch(Throwable failure){status.putString("error",failure.toString());try{capture("failure");report.put("error",failure.toString()).put("focused_window",shell("dumpsys window").lines().filter(l->l.contains("mCurrentFocus")).findFirst().orElse(""));}catch(Exception ignored){}}
    finally{
        final MediaController current=source;main(()->{MusicSessionService.removeListener(sessionObserver);if(current!=null)current.getTransportControls().pause();PlayerPreferences.setEnabled(host.getTargetContext(),false);if(activity!=null)activity.finishAndRemoveTask();});
        try{shell("cmd notification disallow_listener "+host.getTargetContext().getPackageName()+"/it.noxia.ambientplayer.MusicSessionService");shell("locksettings clear --old 1234");shell("locksettings set-disabled true");shell("input keyevent KEYCODE_WAKEUP");shell("input keyevent KEYCODE_MENU");shell("wm dismiss-keyguard");}catch(Exception ignored){}
        try(var out=new FileOutputStream(new File(host.getTargetContext().getFilesDir(),"local-"+provider+".json"))){report.put("success",passed).put("checks",checks).put("session_events",sessionEvents);out.write(report.toString(2).getBytes());}catch(Exception ignored){}
    }
    status.putString("status",passed?"passed":"failed");host.finish(passed?Activity.RESULT_OK:Activity.RESULT_CANCELED,status);
    }
}
