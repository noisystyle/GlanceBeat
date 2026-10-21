package it.noxia.ambientplayer;

import android.app.*;
import android.content.Intent;
import android.graphics.*;
import android.media.*;
import android.media.session.*;
import android.os.*;
import android.view.*;
import org.json.*;
import java.io.*;
import java.lang.reflect.Field;
import java.util.*;

/** Inject through Android's input dispatcher, including the native controls above the canvas. */
final class SwipeInputChecks {
    private final Instrumentation host;
    private final JSONArray checks=new JSONArray();
    private final List<String> failures=new ArrayList<>();
    private final List<Long> requests=new ArrayList<>();
    private PreviewActivity activity;
    private PlayerView scene;
    private PlayerSurface surface;
    private MediaSession session;
    private boolean publishedQueue=true;
    private boolean acknowledge=true;
    private boolean queueGap,gapRetained;
    private long offered=PlaybackState.ACTION_SKIP_TO_NEXT|PlaybackState.ACTION_SKIP_TO_PREVIOUS|PlaybackState.ACTION_SKIP_TO_QUEUE_ITEM|PlaybackState.ACTION_PAUSE|PlaybackState.ACTION_SET_RATING;
    private long track=101;
    private int unlocks,opens,ratings;
    private final Bitmap[] covers={art(Color.BLUE),art(Color.RED),art(Color.GREEN)};
    private SwipeInputChecks(Instrumentation host){this.host=host;}
    static void run(Instrumentation host){new SwipeInputChecks(host).run();}
    private static Bitmap art(int color){Bitmap b=Bitmap.createBitmap(64,64,Bitmap.Config.ARGB_8888);b.eraseColor(color);return b;}
    private void main(Runnable r){host.runOnMainSync(r);}
    private void setStatic(String name,Object value){try{Field f=MusicSessionService.class.getDeclaredField(name);f.setAccessible(true);f.set(null,value);}catch(Exception e){throw new RuntimeException(e);}}
    private String shell(String command)throws Exception{try(var fd=host.getUiAutomation().executeShellCommand(command);var in=new FileInputStream(fd.getFileDescriptor())){return new String(in.readAllBytes());}}
    private void check(String name,boolean passed)throws Exception{checks.put(new JSONObject().put("name",name).put("passed",passed));if(!passed)failures.add(name);}
    private float scale(){return Math.min(scene.getWidth()/400f,scene.getHeight()/650f);}
    private float x(){return scene.getWidth()/2f;}
    private float coverY(){return scene.getHeight()/2f+scene.contentOffsetY()-178*scale();}
    private float blankY(){return scene.getHeight()/2f+scene.contentOffsetY()-60*scale();}
    private void event(long down,int action,float x,float y){
        MotionEvent e=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,x,y,0);e.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        if(!host.getUiAutomation().injectInputEvent(e,true))throw new AssertionError("input injection failed");e.recycle();
    }
    private void drag(float x,float y,float dx,float dy,long duration,long hold,boolean cancel)throws Exception{
        long down=SystemClock.uptimeMillis();event(down,MotionEvent.ACTION_DOWN,x,y);
        int steps=Math.max(8,(int)(duration/25));
        for(int i=1;i<=steps;i++){SystemClock.sleep(Math.max(1,duration/steps));event(down,MotionEvent.ACTION_MOVE,x+dx*i/steps,y+dy*i/steps);}
        SystemClock.sleep(hold);event(down,cancel?MotionEvent.ACTION_CANCEL:MotionEvent.ACTION_UP,x+dx,y+dy);Thread.sleep(750);
    }
    private void queue(boolean enabled){
        publishedQueue=enabled;var items=new ArrayList<MediaSession.QueueItem>();
        if(enabled)for(int i=0;i<3;i++)items.add(new MediaSession.QueueItem(new MediaDescription.Builder().setMediaId("swipe-"+(100+i)).setTitle("Track "+(100+i)).setSubtitle("Silent fixture").setIconBitmap(covers[i]).build(),100+i));
        session.setQueue(items);publish(track);
    }
    private void publish(long id){
        track=id;session.setMetadata(new MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_MEDIA_ID,"swipe-"+id).putString(MediaMetadata.METADATA_KEY_TITLE,"Track "+id).putString(MediaMetadata.METADATA_KEY_ARTIST,"Silent fixture").putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART,covers[(int)Math.floorMod(id-100,3)]).putLong(MediaMetadata.METADATA_KEY_DURATION,240000).putRating(MediaMetadata.METADATA_KEY_USER_RATING,Rating.newHeartRating(false)).build());
        PlaybackState state=new PlaybackState.Builder().setState(PlaybackState.STATE_PLAYING,0,1).setActiveQueueItemId(publishedQueue?id:MediaSession.QueueItem.UNKNOWN_ID).setActions(offered).build();
        session.setPlaybackState(state);setStatic("playbackSnapshot",state);
        if(scene!=null){MusicHistory.observe(MusicQueue.read());scene.refreshMedia();surface.refreshSession();}
    }
    private void tap(float x,float y)throws Exception{long d=SystemClock.uptimeMillis();event(d,0,x,y);SystemClock.sleep(45);event(d,1,x,y);}
    private void active(boolean enabled)throws Exception{main(()->scene.setActive(enabled));Thread.sleep(550);}
    private void run(){Bundle result=new Bundle();
        try{
            if(!Build.FINGERPRINT.contains("generic") && !Build.MODEL.contains("sdk"))throw new AssertionError("Emulator only");
            shell("cmd notification disallow_listener "+host.getTargetContext().getPackageName()+"/it.noxia.ambientplayer.MusicSessionService");
            activity=(PreviewActivity)host.startActivitySync(new Intent(host.getTargetContext(),PreviewActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            main(()->{
                session=new MediaSession(activity,"Silent swipe input fixture");
                session.setCallback(new MediaSession.Callback(){
                    @Override public void onSkipToQueueItem(long id){
                        requests.add(id);
                        if(!queueGap){publish(id);return;}
                        setStatic("current",null);setStatic("playbackSnapshot",null);scene.refreshMedia();surface.refreshSession();
                        gapRetained=surface.queueOpen() && surface.queuePending();
                        new Handler(Looper.getMainLooper()).postDelayed(()->{setStatic("current",new MediaController(activity,session.getSessionToken()));publish(id);},450);
                    }
                    @Override public void onSkipToNext(){requests.add(track+1);if(acknowledge)publish(track+1);}
                    @Override public void onSkipToPrevious(){requests.add(track-1);if(acknowledge)publish(track-1);}
                    @Override public void onSetRating(Rating rating){ratings++;}
                },new Handler(Looper.getMainLooper()));session.setRatingType(Rating.RATING_HEART);session.setActive(true);
                setStatic("current",new MediaController(activity,session.getSessionToken()));queue(true);
                scene=new PlayerView(activity,false,new PlayerView.Actions(){
                    public void onActivate(){}public void onToggle(){scene.setActive(!scene.isActiveTarget());}public void onInteract(){}
                    public void onUnlock(){unlocks++;}public void onQueue(){surface.openQueue();}public void onAccessory(){}
                    public void onOpenMusic(){opens++;}public void onOpenNotification(PendingIntent p){}
                    public void onVisualFrame(){if(surface!=null)surface.setVisualProgress(scene.progress());}public void onVisualSettled(){}public void onStatusChanged(){}
                });
                surface=new PlayerSurface(activity,scene,()->{},false,()->{});activity.setContentView(surface);scene.setActive(true);
            });Thread.sleep(1600);
            float s=scale(),x=x();
            drag(x,coverY(),-150*s,0,350,0,false);
            check("queued_cover_next_exactly_once",requests.equals(List.of(102L)));
            check("cover_swipe_never_opens_queue_or_music",!surface.queueOpen() && opens==0);
            drag(x,coverY(),150*s,0,1400,0,false);
            check("slow_queued_cover_previous_exactly_once",requests.equals(List.of(102L,101L)));
            drag(x-90*s,blankY(),170*s,0,1500,0,false);
            check("slow_right_swipe_opens_queue",surface.queueOpen());main(()->surface.closeQueue(false));Thread.sleep(150);
            drag(x,coverY(),-20*s,0,300,0,false);
            check("short_cover_drag_does_not_skip",requests.size()==2);
            drag(x,coverY(),-140*s,0,350,0,true);
            check("cancelled_cover_drag_does_not_skip",requests.size()==2);
            drag(x,scene.getHeight()-90*s,0,-150*s,350,300,false);
            check("upward_swipe_with_pause_requests_unlock",unlocks==1);
            drag(x,scene.getHeight()-90*s,0,-150*s,3200,0,false);
            check("slow_upward_swipe_requests_unlock",unlocks==2);
            main(()->queue(false));Thread.sleep(600);
            drag(x,coverY(),-150*s,0,400,0,false);
            check("no_queue_next_uses_transport_once",requests.equals(List.of(102L,101L,102L)));
            check("no_queue_next_metadata_updates",track==102 && "Track 102".equals(sceneTitle()));
            drag(x,coverY(),150*s,0,1300,0,false);
            check("no_queue_previous_uses_transport_once",requests.equals(List.of(102L,101L,102L,101L)));
            check("no_queue_swipes_do_not_open_queue",!surface.queueOpen());
            long down=SystemClock.uptimeMillis();float y=coverY();event(down,0,x,y);SystemClock.sleep(50);event(down,2,x-150*s,y);
            main(()->publish(102));SystemClock.sleep(80);event(down,1,x-150*s,y);Thread.sleep(850);
            check("track_changed_during_drag_does_not_skip_new_track",requests.size()==4);
            main(()->{offered=0;publish(track);});
            drag(x,coverY(),-150*s,0,400,0,false);check("unsupported_transport_does_not_send_command",requests.size()==4);
            main(()->{offered=PlaybackState.ACTION_SKIP_TO_NEXT|PlaybackState.ACTION_SKIP_TO_PREVIOUS|PlaybackState.ACTION_SET_RATING;publish(track);});
            Thread.sleep(300);View like=widget("like");int[] position=new int[2];main(()->like.getLocationOnScreen(position));
            check("native_like_button_is_visible_and_enabled",like.isShown() && like.isEnabled());
            drag(position[0]+like.getWidth()/2f,position[1]+like.getHeight()/2f,0,-130*s,800,250,false);
            check("swipe_from_native_button_unlocks_without_rating",unlocks==3 && ratings==0);
            main(()->acknowledge=false);drag(x,coverY(),-150*s,0,350,0,false);
            check("unconfirmed_transport_keeps_transaction_pending",scene.adjacentPending() && requests.size()==5);
            drag(x,coverY(),-150*s,0,350,0,false);
            check("pending_transport_suppresses_duplicate_swipe",requests.size()==5);
            Thread.sleep(5000);check("unconfirmed_transport_recovers_without_stuck_row",!scene.adjacentPending() && !scene.isContentTransitionRunning());
            drag(x,coverY(),-150*s,0,350,0,false);main(()->scene.setVisibility(View.INVISIBLE));Thread.sleep(150);
            check("hiding_clears_pending_transport_and_animation",!scene.adjacentPending() && !scene.isContentTransitionRunning());
            main(()->{acknowledge=true;scene.setVisibility(View.VISIBLE);});Thread.sleep(400);
            active(false);int before=requests.size(),unlocksBefore=unlocks;
            drag(x,coverY(),-150*s,0,350,0,false);drag(x-90*s,blankY(),170*s,0,350,0,false);
            drag(x,scene.getHeight()-90*s,0,-150*s,350,0,false);
            check("aod_suppresses_all_swipes",requests.size()==before && unlocks==unlocksBefore && !surface.queueOpen());
            tap(x,240);SystemClock.sleep(80);tap(x,240);Thread.sleep(600);
            check("real_double_tap_activates_controls",scene.isActiveTarget());
            check("no_stuck_carousel_after_swipes",!scene.adjacentPending() && !scene.isContentTransitionRunning());
            main(()->{queueGap=true;offered|=PlaybackState.ACTION_SKIP_TO_QUEUE_ITEM;queue(true);publish(101);requests.clear();});Thread.sleep(600);
            drag(x-90*s,blankY(),170*s,0,350,0,false);
            android.graphics.Rect row=queueRow(host.getUiAutomation().getRootInActiveWindow(),"Track 102");
            check("queue_gap_target_is_clickable",row!=null);tap(row.exactCenterX(),row.exactCenterY());Thread.sleep(150);
            check("brief_session_gap_keeps_selection_pending",gapRetained && surface.queueOpen() && surface.queuePending());
            Thread.sleep(1000);check("same_session_return_confirms_and_closes_queue",track==102 && !surface.queueOpen() && !surface.queuePending());
            check("session_gap_does_not_duplicate_queue_command",requests.equals(List.of(102L)));
            main(()->{publish(101);requests.clear();});Thread.sleep(600);
            drag(x-90*s,blankY(),170*s,0,350,0,false);
            row=queueRow(host.getUiAutomation().getRootInActiveWindow(),"Track 102");
            if(row==null)throw new AssertionError("Missing replacement-session queue row");
            tap(row.exactCenterX(),row.exactCenterY());Thread.sleep(120);
            MediaSession[] replacement={null};main(()->{
                replacement[0]=new MediaSession(activity,"Different live session");replacement[0].setActive(true);
                setStatic("current",new MediaController(activity,replacement[0].getSessionToken()));
                scene.refreshMedia();surface.refreshSession();
            });
            check("different_live_session_cancels_pending_queue",surface.queueOpen() && !surface.queuePending());
            Thread.sleep(850);check("returning_original_session_cannot_confirm_cancelled_request",surface.queueOpen() && !surface.queuePending() && requests.equals(List.of(102L)));
            main(()->replacement[0].release());
        }catch(Throwable failure){failures.add(failure.toString());}
        finally{
            main(()->{if(activity!=null)activity.finish();if(session!=null)session.release();setStatic("current",null);setStatic("playbackSnapshot",null);});
            result.putString("status",failures.isEmpty()?"passed":"failed");result.putString("error",failures.toString());
            try(var out=new FileOutputStream(new File(host.getTargetContext().getFilesDir(),"swipes.json"))){out.write(new JSONObject().put("success",failures.isEmpty()).put("checks",checks).put("failures",new JSONArray(failures)).toString(2).getBytes());}catch(Exception ignored){}
        }
        host.finish(failures.isEmpty()?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
    private String sceneTitle(){try{Field f=PlayerView.class.getDeclaredField("contentTitle");f.setAccessible(true);return (String)f.get(scene);}catch(Exception e){throw new RuntimeException(e);}}
    private View widget(String name){try{Field f=PlayerSurface.class.getDeclaredField(name);f.setAccessible(true);return (View)f.get(surface);}catch(Exception e){throw new RuntimeException(e);}}
    private android.graphics.Rect queueRow(android.view.accessibility.AccessibilityNodeInfo node,String title){
        if(node==null)return null;CharSequence text=node.getContentDescription();
        if(node.isClickable() && node.isEnabled() && text!=null && text.toString().contains(title)){var bounds=new android.graphics.Rect();node.getBoundsInScreen(bounds);return bounds;}
        for(int i=0;i<node.getChildCount();i++){var found=queueRow(node.getChild(i),title);if(found!=null)return found;}return null;
    }
}
