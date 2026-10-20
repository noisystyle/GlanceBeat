package it.noxia.ambientplayer;

import android.app.Instrumentation;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;

/** Emulator-only pixel verification of the requested spatial blur, using a symmetric test texture. */
final class CarouselFrostChecks {
    private final Instrumentation host;
    private CarouselFrostChecks(Instrumentation host){this.host=host;}
    static void run(Instrumentation host){new CarouselFrostChecks(host).run();}
    private final JSONArray checks=new JSONArray();
    private void check(String name,boolean value) throws Exception {
        if(!value)throw new AssertionError(name);
        checks.put(new JSONObject().put("name",name).put("passed",true));
    }
    private double contrast(Bitmap image,int from,int to) {
        double sum=0,squares=0;int count=0;
        for(int x=from;x<to;x++)for(int y=40;y<216;y++) {
            int brightness=(image.getPixel(x,y)>>16)&255;sum+=brightness;squares+=brightness*brightness;count++;
        }
        return squares/count-Math.pow(sum/count,2);
    }
    private Bitmap render(AdjacentCovers covers) {
        Bitmap[] result=new Bitmap[1];host.runOnMainSync(()->{
            result[0]=Bitmap.createBitmap(120,120,Bitmap.Config.ARGB_8888);
            covers.draw(new android.graphics.Canvas(result[0]),0,72,1,0);
        });return result[0];
    }
    private int alpha(AdjacentCovers covers) {Bitmap frame=render(covers);int alpha=android.graphics.Color.alpha(frame.getPixel(60,60));frame.recycle();return alpha;}
    private boolean running(AdjacentCovers covers) {boolean[] state=new boolean[1];host.runOnMainSync(()->state[0]=covers.running());return state[0];}
    private void verifyReveal(Bitmap texture) throws Exception {
        AdjacentCovers[] holder=new AdjacentCovers[1];
        android.media.session.MediaSession session=new android.media.session.MediaSession(host.getTargetContext(),"Silent reveal verification");
        MusicQueue.Snapshot fresh=new MusicQueue.Snapshot(session.getSessionToken(),java.util.List.of(
            new MusicQueue.Entry(new QueueSelection.Item(100,"current"),"Current","Artist",texture,null),
            new MusicQueue.Entry(new QueueSelection.Item(101,"next"),"Next","Artist",texture,null)),0,true,false,false,true,"current");
        try {
            host.runOnMainSync(()->{
                holder[0]=new AdjacentCovers(host.getTargetContext(),()->{},()->{});
                holder[0].presentationBlocked(true);holder[0].visible(true);holder[0].refresh(fresh);
            });
            AdjacentCovers covers=holder[0];int[] ready=new int[1];
            long deadline=android.os.SystemClock.uptimeMillis()+5000;
            do {Thread.sleep(30);host.runOnMainSync(()->ready[0]=covers.readyCount());}while(ready[0]!=1 && android.os.SystemClock.uptimeMillis()<deadline);
            check("prepared_neighbor_waits_while_carousel_hides_it",ready[0]==1 && !running(covers));
            Thread.sleep(900);
            check("hidden_neighbor_does_not_consume_its_fade_behind_carousel",alpha(covers)==0 && !running(covers));
            int[] initial=new int[1];host.runOnMainSync(()->{covers.presentationBlocked(false);Bitmap b=Bitmap.createBitmap(120,120,Bitmap.Config.ARGB_8888);covers.draw(new android.graphics.Canvas(b),0,72,1,0);initial[0]=android.graphics.Color.alpha(b.getPixel(60,60));b.recycle();});
            check("newly_visible_neighbor_starts_transparent",initial[0]==0 && running(covers));
            Thread.sleep(170);int middle=alpha(covers);
            check("neighbor_fades_in_gradually",middle>0 && middle<255 && running(covers));
            host.runOnMainSync(()->covers.refresh(fresh));Thread.sleep(850);
            check("neighbor_finishes_at_original_frost_without_polling",alpha(covers)==255 && !running(covers));
            host.runOnMainSync(()->covers.refresh(fresh));
            check("duplicate_queue_does_not_restart_neighbor_reveal",!running(covers));
            host.runOnMainSync(()->covers.visible(false));check("hidden_surface_finishes_neighbor_animations",!running(covers));
        }finally{if(holder[0]!=null)host.runOnMainSync(()->holder[0].release());session.release();}
    }
    private void run() {
        Bundle result=new Bundle();int code=0;
        try {
            if(!Build.FINGERPRINT.contains("generic") && !Build.MODEL.contains("sdk"))throw new AssertionError("Emulator only");
            Bitmap texture=Bitmap.createBitmap(256,256,Bitmap.Config.ARGB_8888);
            for(int y=0;y<256;y++)for(int x=0;x<256;x++)texture.setPixel(x,y,((Math.min(x,255-x)/16+y/16)%2)==0?0xffffffff:0xff000000);
            Bitmap left=SideCoverFrost.create(texture,true),right=SideCoverFrost.create(texture,false);
            check("right_cover_is_more_blurred_toward_screen_edge",contrast(right,8,48)>contrast(right,190,230)*4);
            check("left_cover_is_more_blurred_toward_screen_edge",contrast(left,208,248)>contrast(left,26,66)*4);
            boolean mirrored=true;
            for(int y=0;y<256;y++)for(int x=0;x<256;x++)if(left.getPixel(x,y)!=right.getPixel(255-x,y))mirrored=false;
            check("left_and_right_frost_are_symmetric",mirrored);
            check("blur_preserves_original_artwork",!texture.isRecycled() && texture.getPixel(0,0)==0xffffffff && texture.getPixel(20,0)==0xff000000);
            check("frost_corners_are_fully_transparent",android.graphics.Color.alpha(left.getPixel(0,0))==0);
            int edge=android.graphics.Color.alpha(left.getPixel(2,128)),feather=android.graphics.Color.alpha(left.getPixel(12,128)),inside=android.graphics.Color.alpha(left.getPixel(32,128));
            check("entire_cover_outline_has_a_soft_alpha_feather",edge>0 && edge<feather && feather<inside && inside==255);
            Bitmap deep=SideCoverFrost.deep(texture,false);
            check("new_cover_starts_with_much_stronger_blur",contrast(deep,32,72)<contrast(right,32,72)/4);
            deep.recycle();
            verifyReveal(texture);
            texture.recycle();left.recycle();right.recycle();
        } catch(Throwable failure){code=-1;result.putString("error",failure.toString());}
        try {
            JSONObject report=new JSONObject().put("checks",checks).put("success",code==0);
            try(FileOutputStream out=new FileOutputStream(new File(host.getTargetContext().getFilesDir(),"carousel-frost.json"))) {out.write(report.toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));}
            result.putString("report",report.toString());
        }catch(Exception failure){code=-1;result.putString("error",failure.toString());}
        host.finish(code,result);
    }
}
