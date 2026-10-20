package it.noxia.ambientplayer;

import android.app.*;
import android.content.Intent;
import android.graphics.*;
import android.media.*;
import android.media.session.*;
import android.os.*;
import android.view.View;
import org.json.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;

/** Silent emulator-only, deliberately inconsistent metadata/state/artwork publication. */
final class CarouselCommitChecks {
 private final Instrumentation host;private final JSONArray checks=new JSONArray();
 private PreviewActivity activity;private PlayerView player;private MediaSession session;
 private final Bitmap red=art(Color.RED),green=art(Color.GREEN),blue=art(Color.BLUE),yellow=art(Color.YELLOW);
 private final List<Long> requests=new ArrayList<>();private boolean repeatedTitles;private int nextButtons,previousButtons;private boolean restartPrevious;
 private CarouselCommitChecks(Instrumentation h){host=h;}
 static void run(Instrumentation h){new CarouselCommitChecks(h).run();}
 private static Bitmap art(int color){Bitmap b=Bitmap.createBitmap(256,256,Bitmap.Config.ARGB_8888);b.eraseColor(color);return b;}
 private Field field(Object o,String name)throws Exception{for(Class<?> t=o instanceof Class<?>?(Class<?>)o:o.getClass();t!=null;t=t.getSuperclass())try{Field f=t.getDeclaredField(name);f.setAccessible(true);return f;}catch(NoSuchFieldException ignored){}throw new NoSuchFieldException(name);}
 private Object get(Object o,String name){try{return field(o,name).get(o instanceof Class<?>?null:o);}catch(Exception e){throw new RuntimeException(e);}}
 private void set(Object o,String name,Object v){try{field(o,name).set(o instanceof Class<?>?null:o,v);}catch(Exception e){throw new RuntimeException(e);}}
 private void call(Object o,String name){try{Method m=o.getClass().getDeclaredMethod(name);m.setAccessible(true);m.invoke(o);}catch(Exception e){throw new RuntimeException(e);}}
 private void check(String name,boolean passed)throws Exception{if(!passed)throw new AssertionError(name);checks.put(new JSONObject().put("name",name).put("passed",true));}
 private String title(long id){return repeatedTitles?"Titolo ripetuto":"Traccia "+id;}
 private void metadata(long id,Bitmap artwork){session.setMetadata(new MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_MEDIA_ID,"id-"+id).putString(MediaMetadata.METADATA_KEY_TITLE,title(id)).putString(MediaMetadata.METADATA_KEY_ARTIST,"Artista "+id).putLong(MediaMetadata.METADATA_KEY_DURATION,240000).putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART,artwork).build());}
 private void state(long id,int mode){PlaybackState p=new PlaybackState.Builder().setState(mode,0,mode==PlaybackState.STATE_PLAYING?1:0).setActions(PlaybackState.ACTION_SKIP_TO_QUEUE_ITEM|PlaybackState.ACTION_SKIP_TO_PREVIOUS|PlaybackState.ACTION_SKIP_TO_NEXT).setActiveQueueItemId(id).build();session.setPlaybackState(p);set(MusicSessionService.class,"playbackSnapshot",p);}
 private void refresh(){host.runOnMainSync(()->{MusicHistory.observe(MusicQueue.read());player.refreshMedia();});}
 private void publish(long metadataId,Bitmap artwork,long activeId,int mode){host.runOnMainSync(()->{metadata(metadataId,artwork);state(activeId,mode);});refresh();}
 private void start(boolean sameTitles)throws Exception{
  host.runOnMainSync(()->{
   if(player!=null)activity.setContentView(new View(activity));
   repeatedTitles=sameTitles;var queue=new ArrayList<MediaSession.QueueItem>();Bitmap[] arts={blue,red,green,yellow};
   for(int i=0;i<4;i++){long id=99+i;queue.add(new MediaSession.QueueItem(new MediaDescription.Builder().setMediaId("id-"+id).setTitle(title(id)).setSubtitle("Artista "+id).setIconBitmap(arts[i]).build(),id));}
   session.setQueue(queue);metadata(100,red);state(100,PlaybackState.STATE_PLAYING);set(MusicSessionService.class,"current",new MediaController(activity,session.getSessionToken()));MusicHistory.observe(MusicQueue.read());
   player=new PlayerView(activity);activity.setContentView(player);player.setActive(true);
  });Thread.sleep(450);refresh();
  long deadline=SystemClock.uptimeMillis()+4000;while(player.adjacentReady()!=2 && SystemClock.uptimeMillis()<deadline)Thread.sleep(30);
  Thread.sleep(850);requests.clear();
  check("neighbors_ready_"+(sameTitles?"repeated":"distinct"),player.adjacentReady()==2);
 }
 private boolean navigate(boolean previous){boolean[] result={false};host.runOnMainSync(()->{try{Method m=PlayerView.class.getDeclaredMethod("navigateCover",boolean.class);m.setAccessible(true);result[0]=(boolean)m.invoke(player,previous);}catch(Exception e){throw new RuntimeException(e);}});return result[0];}
 private float travel(){float[] v={0};host.runOnMainSync(()->v[0]=player.coverTravel());return v[0];}
 private int center(){int[] color={0};host.runOnMainSync(()->{Bitmap b=Bitmap.createBitmap(player.getWidth(),player.getHeight(),Bitmap.Config.ARGB_8888);player.draw(new Canvas(b));float scale=Math.min(player.getWidth()/400f,player.getHeight()/650f);color[0]=b.getPixel(player.getWidth()/2,Math.round(player.getHeight()/2f-178*scale));b.recycle();});return color[0];}
 private boolean isGreen(){int c=center();return Color.green(c)>220 && Color.red(c)<35 && Color.blue(c)<35;}
 private boolean isRed(){int c=center();return Color.red(c)>220 && Color.green(c)<35 && Color.blue(c)<35;}
 private boolean committed(){return (int)get(player,"coverCommitDirection")==0 && get(player,"frozenRow")==null;}
 private void pressTransport(boolean previous){host.runOnMainSync(()->{float scale=Math.min(player.getWidth()/400f,player.getHeight()/650f);android.view.MotionEvent e=android.view.MotionEvent.obtain(SystemClock.uptimeMillis(),SystemClock.uptimeMillis(),android.view.MotionEvent.ACTION_UP,player.contentCenterX()+(previous?-88:88)*scale,player.getHeight()/2f+player.contentOffsetY()+58*scale,0);try{Method m=PlayerView.class.getDeclaredMethod("handleSingleTap",android.view.MotionEvent.class);m.setAccessible(true);m.invoke(player,e);}catch(Exception failure){throw new RuntimeException(failure);}finally{e.recycle();}});}
 private void awaitCoverMotion()throws Exception{
  long deadline=SystemClock.uptimeMillis()+2500;boolean[] moving={true};
  do{host.runOnMainSync(()->moving[0]=get(player,"coverReturn")!=null || ((ContentLayer)get(player,"coverLayer")).running());if(moving[0])Thread.sleep(20);}while(moving[0] && SystemClock.uptimeMillis()<deadline);
  if(moving[0])throw new AssertionError("Cover motion did not settle");
 }
 private void run(){Bundle result=new Bundle();
  try{
   if(!Build.FINGERPRINT.contains("generic") && !Build.MODEL.contains("sdk"))throw new AssertionError("Emulator only");
   activity=(PreviewActivity)host.startActivitySync(new Intent(host.getTargetContext(),PreviewActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
   host.runOnMainSync(()->{session=new MediaSession(activity,"Silent carousel race fixture");session.setCallback(new MediaSession.Callback(){@Override public void onSkipToQueueItem(long id){requests.add(id);}
    @Override public void onSkipToNext(){nextButtons++;long id=session.getController().getPlaybackState().getActiveQueueItemId()+1;metadata(id,id==101?green:yellow);state(id,PlaybackState.STATE_PLAYING);}
    @Override public void onSkipToPrevious(){previousButtons++;long id=session.getController().getPlaybackState().getActiveQueueItemId()-(restartPrevious?0:1);metadata(id,id==100?red:blue);state(id,PlaybackState.STATE_PLAYING);}},new Handler(Looper.getMainLooper()));session.setActive(true);});
   start(false);boolean[] missingAnchor={true};host.runOnMainSync(()->{try{Method m=PlayerView.class.getDeclaredMethod("navigateCover",boolean.class,AdjacentCovers.Anchor.class,boolean.class);m.setAccessible(true);missingAnchor[0]=(boolean)m.invoke(player,false,null,true);}catch(Exception e){throw new RuntimeException(e);}});check("missing_gesture_anchor_does_not_send_or_freeze",!missingAnchor[0] && committed() && requests.isEmpty());check("next_command_accepted",navigate(false));Thread.sleep(60);float before=travel();
   host.runOnMainSync(()->state(101,PlaybackState.STATE_PLAYING));refresh();Thread.sleep(50);
   host.runOnMainSync(()->state(100,PlaybackState.STATE_BUFFERING));refresh();
   boolean monotonic=true;float prior=before;for(int i=0;i<32;i++){Thread.sleep(20);float v=travel();if(v>prior+.5f)monotonic=false;prior=v;}
   check("confirmed_slide_never_rewinds_on_stale_or_buffering_state",monotonic && prior< -150 && !committed());
   check("frozen_selected_cover_stays_centered_awaiting_metadata",isGreen());
   publish(101,red,101,PlaybackState.STATE_PLAYING);Thread.sleep(40);
   check("target_metadata_with_old_artwork_hands_off_to_selected_cover",committed() && isGreen());
   check("single_transport_command_through_callback_race",requests.equals(List.of(101L)));
   publish(101,red,101,PlaybackState.STATE_PLAYING);Thread.sleep(40);check("duplicate_old_artwork_never_restores_departed_cover",isGreen());
   publish(101,null,101,PlaybackState.STATE_BUFFERING);Thread.sleep(40);check("missing_artwork_and_buffering_keep_selected_cover",isGreen());
   publish(100,red,101,PlaybackState.STATE_PLAYING);Thread.sleep(40);check("late_origin_metadata_is_ignored_while_queue_points_to_target",isGreen() && "Traccia 101".equals(get(player,"contentTitle")));
   check("late_origin_callback_does_not_start_a_new_sideways_transition",!(boolean)get(get(player,"coverLayer"),"horizontal"));
   publish(101,green,101,PlaybackState.STATE_PLAYING);Thread.sleep(550);check("actual_target_artwork_replaces_fallback_without_old_cover",isGreen() && get(player,"carouselFallback")==null);
   Thread.sleep(900);check("reverse_command_after_handoff_is_accepted",navigate(true));Thread.sleep(60);publish(100,green,100,PlaybackState.STATE_PLAYING);awaitCoverMotion();
   check("reverse_handoff_uses_previous_cover_even_if_artwork_lags",committed() && isRed());publish(100,red,100,PlaybackState.STATE_PLAYING);Thread.sleep(550);
   check("opposite_commands_are_exactly_once",requests.equals(List.of(101L,100L)));
   start(true);check("repeated_title_next_command_accepted",navigate(false));host.runOnMainSync(()->state(101,PlaybackState.STATE_PLAYING));refresh();awaitCoverMotion();
   check("same_title_does_not_release_before_target_media_id",!committed() && isGreen());publish(101,red,101,PlaybackState.STATE_PLAYING);Thread.sleep(40);
   check("repeated_title_correct_id_completes_without_rewind",committed() && isGreen());
   start(false);check("metadata_first_next_command_accepted",navigate(false));publish(101,red,100,PlaybackState.STATE_PLAYING);awaitCoverMotion();
   check("metadata_before_active_queue_id_keeps_selected_frame",!committed() && isGreen());publish(101,green,101,PlaybackState.STATE_PLAYING);Thread.sleep(550);check("coherent_queue_and_metadata_complete_once",committed() && isGreen());
   start(false);check("missing_destination_next_command_accepted",navigate(false));
   host.runOnMainSync(()->{CarouselFrame row=(CarouselFrame)get(player,"frozenRow");CarouselFrame.Card card=(CarouselFrame.Card)get(row,"next");if(card!=null)card.release();set(row,"next",null);MusicQueue.Entry target=(MusicQueue.Entry)get(player,"coverCommitEntry");set(player,"coverCommitEntry",new MusicQueue.Entry(target.key(),target.title(),target.artist(),null,null));});
   publish(101,red,101,PlaybackState.STATE_PLAYING);awaitCoverMotion();check("missing_destination_art_never_reuses_departed_cover",committed() && !isRed());publish(101,red,101,PlaybackState.STATE_PLAYING);Thread.sleep(40);check("old_artwork_does_not_overwrite_unknown_destination",!isRed());publish(101,green,101,PlaybackState.STATE_PLAYING);Thread.sleep(550);check("unknown_destination_resolves_when_real_cover_arrives",isGreen());
   start(false);check("slow_metadata_next_command_accepted",navigate(false));host.runOnMainSync(()->state(101,PlaybackState.STATE_PLAYING));refresh();awaitCoverMotion();
   host.runOnMainSync(()->((Runnable)get(player,"coverRecovery")).run());check("confirmed_deadline_releases_navigation_with_selected_cover",committed() && isGreen());refresh();Thread.sleep(40);check("late_metadata_deadline_does_not_restore_origin_artwork",isGreen());publish(101,green,101,PlaybackState.STATE_PLAYING);Thread.sleep(550);
   check("metadata_after_deadline_resolves_to_real_target",committed() && isGreen());
   start(false);check("unconfirmed_next_command_accepted",navigate(false));awaitCoverMotion();host.runOnMainSync(()->call(get(player,"adjacent"),"timedOut"));awaitCoverMotion();
   check("real_unconfirmed_timeout_rolls_back_to_current",committed() && isRed());
   check("no_animation_loop_after_rollback",!player.isContentTransitionRunning());
   start(false);navigate(false);Thread.sleep(50);host.runOnMainSync(()->player.setVisibility(View.INVISIBLE));
   check("hiding_player_discards_frozen_row_and_motion",committed() && !player.isContentTransitionRunning());
   start(false);publish(101,green,101,PlaybackState.STATE_PLAYING);Thread.sleep(80);
   check("automatic_next_moves_frozen_row_forward_without_transport",travel()<0 && (int)get(player,"coverCommitDirection")==-1 && requests.isEmpty());awaitCoverMotion();check("automatic_next_finishes_on_new_cover",committed() && isGreen());
   publish(100,red,100,PlaybackState.STATE_PLAYING);Thread.sleep(80);check("external_previous_moves_frozen_row_backward",travel()>0 && (int)get(player,"coverCommitDirection")==1);awaitCoverMotion();check("external_previous_finishes_on_previous_cover",committed() && isRed());
   start(false);host.runOnMainSync(()->state(101,PlaybackState.STATE_PLAYING));refresh();awaitCoverMotion();check("automatic_state_first_keeps_destination_while_metadata_lags",!committed() && isGreen());publish(101,red,101,PlaybackState.STATE_PLAYING);Thread.sleep(100);check("automatic_state_first_handoff_never_flashes_old_art",committed() && isGreen());
   start(false);publish(101,green,100,PlaybackState.STATE_PLAYING);awaitCoverMotion();check("automatic_metadata_first_waits_for_active_queue_id",!committed() && isGreen());publish(101,green,101,PlaybackState.STATE_PLAYING);Thread.sleep(150);check("automatic_metadata_first_completes_without_rewind",committed() && isGreen());
   start(false);pressTransport(false);Thread.sleep(120);refresh();Thread.sleep(50);check("next_button_uses_forward_carousel_once",nextButtons==1 && (int)get(player,"coverCommitDirection")==-1 && requests.isEmpty());awaitCoverMotion();check("next_button_ends_on_correct_cover",committed() && isGreen());
   pressTransport(true);Thread.sleep(120);refresh();Thread.sleep(50);check("previous_button_uses_reverse_carousel_once",previousButtons==1 && (int)get(player,"coverCommitDirection")==1);awaitCoverMotion();check("previous_button_ends_on_correct_cover",committed() && isRed());
   restartPrevious=true;pressTransport(true);Thread.sleep(120);refresh();check("previous_that_restarts_current_song_does_not_fake_track_change",committed() && isRed());restartPrevious=false;
   start(false);host.runOnMainSync(()->player.setActive(false));Thread.sleep(650);publish(101,green,101,PlaybackState.STATE_PLAYING);Thread.sleep(80);check("aod_track_change_is_finite_forward_motion",(int)get(player,"coverCommitDirection")==-1);long idleDeadline=SystemClock.uptimeMillis()+3000;while(player.isContentTransitionRunning() && SystemClock.uptimeMillis()<idleDeadline)Thread.sleep(30);check("aod_track_change_stops_without_background_animation_loop",committed() && !player.isContentTransitionRunning());
   start(false);host.runOnMainSync(()->session.setQueue(List.of()));refresh();publish(101,green,101,PlaybackState.STATE_PLAYING);Thread.sleep(50);
   ContentLayer layer=(ContentLayer)get(player,"coverLayer");check("missing_queue_automatic_change_still_slides_forward",(boolean)get(layer,"horizontal") && (float)get(layer,"travel")>0 && layer.running());awaitCoverMotion();
   pressTransport(true);Thread.sleep(120);refresh();Thread.sleep(50);check("missing_queue_previous_button_keeps_reverse_direction",(boolean)get(layer,"horizontal") && (float)get(layer,"travel")<0 && layer.running());awaitCoverMotion();
   publish(101,red,101,PlaybackState.STATE_PLAYING);Thread.sleep(50);check("different_song_with_identical_artwork_still_has_directional_motion",(boolean)get(layer,"horizontal") && (float)get(layer,"travel")>0 && layer.running());awaitCoverMotion();
   result.putString("status","passed");
  }catch(Throwable failure){result.putString("status","failed");result.putString("error",failure.toString());}
  finally{
   if(activity!=null)host.runOnMainSync(()->{activity.finish();session.release();set(MusicSessionService.class,"current",null);set(MusicSessionService.class,"playbackSnapshot",null);MusicHistory.clear();});
   try(var out=new FileOutputStream(new File(host.getTargetContext().getFilesDir(),"carousel-commit.json"))){out.write(new JSONObject().put("checks",checks).put("success","passed".equals(result.getString("status"))).put("error",result.getString("error","")).toString(2).getBytes());}catch(Exception ignored){}
  }
  host.finish("passed".equals(result.getString("status"))?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
 }
}
