package it.noxia.ambientplayer;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.media.session.MediaSession;
import android.os.Handler;
import android.os.Looper;
import android.view.animation.PathInterpolator;
import android.animation.ValueAnimator;
import android.widget.Toast;
import java.util.Objects;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Exactly two published queue neighbors, bounded loading and one confirmed transport request. */
final class AdjacentCovers {
    private final Context context;
    private final Runnable redraw,changed;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final QueueArtwork artwork;
    private final ThreadPoolExecutor worker=new ThreadPoolExecutor(1,1,10,TimeUnit.SECONDS,new LinkedBlockingQueue<>());
    private final Slot previous=new Slot(true),next=new Slot(false);
    private MusicQueue.Snapshot snapshot=MusicQueue.Snapshot.EMPTY;
    private QueueSelection.Item pending;
    private MediaSession.Token pendingToken;
    private boolean visible,presentationBlocked,selectionFailed,refreshing;
    private final Runnable timeout=this::timedOut;
    private void timedOut(){selectionFailed=true;clearPending();if(visible)Toast.makeText(context,context.getString(R.string.track_change_unconfirmed,MusicSources.name(context,MusicSessionService.current==null?null:MusicSessionService.current.getPackageName())),Toast.LENGTH_SHORT).show();changed.run();}
    AdjacentCovers(Context context,Runnable redraw,Runnable changed) {
        this.context=context;this.redraw=redraw;this.changed=changed;
        artwork=new QueueArtwork(context);worker.allowCoreThreadTimeOut(true);
    }
    void visible(boolean value) {
        if(visible==value)return;visible=value;
        if(value){artwork.resume();previous.load();next.load();}
        else {artwork.pause();previous.pause();next.pause();}
    }
    void presentationBlocked(boolean value) {
        if(presentationBlocked==value)return;presentationBlocked=value;
        if(!value){previous.installStaged(!previous.skipReveal);next.installStaged(!next.skipReveal);}
    }
    void preserveDepartingCover(boolean left,QueueSelection.Item origin) {
        Slot slot=left?previous:next;
        if(slot.entry!=null && slot.entry.key().equals(origin)){slot.skipReveal=true;slot.installStaged(false);slot.finish();}
    }
    void refresh(MusicQueue.Snapshot fresh) {
        // Publish a complete row before notifying a confirmed selection. Reentrant slot
        // callbacks cannot hand off a carousel while just one neighbor is rebound.
        boolean selectionSettled=false;refreshing=true;
        try {
            snapshot=fresh;
            previous.bind(MusicHistory.previous(fresh),fresh.token());
            next.bind(fresh.current()>=0 && fresh.current()+1<fresh.entries().size()?fresh.entries().get(fresh.current()+1):null,fresh.token());
            if(pending!=null && fresh.token()!=null) {
                if(!Objects.equals(fresh.token(),pendingToken)){selectionFailed=true;clearPending();selectionSettled=true;}
                else if(MusicQueue.confirmed(fresh,pendingToken,pending)) {
                    android.util.Log.i("AmbientCovers","selection_confirmed queueId="+pending.id());clearPending();selectionSettled=true;
                }
            }
        }finally{refreshing=false;}
        if(selectionSettled)changed.run();
    }
    boolean currentIs(MediaSession.Token token,QueueSelection.Item target){
        return !refreshing && Objects.equals(token,snapshot.token()) && snapshot.current()>=0 && snapshot.entries().get(snapshot.current()).key().equals(target);
    }
    boolean metadataMatchesCurrent(MediaSession.Token token,android.media.MediaMetadata metadata) {
        if(metadata==null || !Objects.equals(token,snapshot.token()))return false;
        if(snapshot.entries().isEmpty())return true;
        if(snapshot.current()<0)return false;
        MusicQueue.Entry entry=snapshot.entries().get(snapshot.current());
        android.media.session.PlaybackState state=MusicSessionService.playbackState();
        if(state!=null && state.getActiveQueueItemId()!=MediaSession.QueueItem.UNKNOWN_ID
                && state.getActiveQueueItemId()!=entry.key().id())return false;
        return CarouselIdentity.matches(entry.key(),entry.title(),entry.artist(),metadata.getString(android.media.MediaMetadata.METADATA_KEY_MEDIA_ID),metadata.getString(android.media.MediaMetadata.METADATA_KEY_TITLE),metadata.getString(android.media.MediaMetadata.METADATA_KEY_ARTIST));
    }
    record Anchor(MediaSession.Token token,QueueSelection.Item current,QueueSelection.Item previous,QueueSelection.Item next) { }
    MusicQueue.Entry currentEntry(){return snapshot.current()<0?null:snapshot.entries().get(snapshot.current());}
    Anchor anchor() {
        return new Anchor(snapshot.token(),snapshot.current()<0?null:snapshot.entries().get(snapshot.current()).key(),
            previous.entry==null?null:previous.entry.key(),next.entry==null?null:next.entry.key());
    }
    boolean pending(){return pending!=null;}
    boolean selectionFailed(){return selectionFailed;}
    int readyCount(){return (previous.ready?1:0)+(next.ready?1:0);}
    int loadingCount(){return artwork.pendingCount()+(previous.job!=null?1:0)+(next.job!=null?1:0);}
    boolean hasNeighbor(boolean left){return (left?previous:next).entry!=null;}
    boolean artworkReady(boolean left){return (left?previous:next).ready;}
    boolean available(boolean left){Slot slot=left?previous:next;return slot.entry!=null && (snapshot.selectable(slot.entry) && snapshot.entries().stream().anyMatch(e->e.key().equals(slot.entry.key())) || left && MusicQueue.previousSupported(snapshot,slot.entry));}
    String label(boolean left){Slot slot=left?previous:next;return slot.entry==null?"":slot.entry.title(context)+(slot.entry.artist().isEmpty()?"":", "+slot.entry.artist());}
    boolean select(boolean left,Anchor touched) {
        Slot slot=left?previous:next;
        if(!visible || pending!=null || !available(left) || touched==null)return false;
        Anchor now=anchor();
        if(!Objects.equals(touched.token(),now.token()) || !Objects.equals(touched.current(),now.current())
            || !Objects.equals(left?touched.previous():touched.next(),slot.entry.key()))return false;
        // A delayed tap/swipe must still address the adjacent item of this exact session.
        MusicQueue.Snapshot fresh=MusicQueue.read();int index=fresh.current()+(left?-1:1);
        if(!Objects.equals(snapshot.token(),fresh.token()) || fresh.current()<0)return false;
        boolean sent;
        if(left && index<0)sent=MusicQueue.selectObservedPrevious(snapshot.token(),touched.current(),slot.entry);
        else sent=index>=0 && index<fresh.entries().size() && fresh.entries().get(index).key().equals(slot.entry.key()) && MusicQueue.select(snapshot.token(),slot.entry);
        if(!sent)return false;
        selectionFailed=false;pending=slot.entry.key();pendingToken=snapshot.token();main.postDelayed(timeout,5000);
        android.util.Log.i("AmbientCovers","selection_sent side="+(left?"previous":"next"));changed.run();return true;
    }
    void draw(Canvas canvas,float top,float halfWidth,float alpha,float drag) {
        previous.draw(canvas,-halfWidth+12+drag,top,alpha);
        next.draw(canvas,halfWidth-12+drag,top,alpha);
    }
    CarouselFrame.Card card(boolean left) {
        Slot slot=left?previous:next;
        return slot.ready?new CarouselFrame.Card(slot.sharp.snapshot(),slot.frostLeft.snapshot(),slot.frostRight.snapshot()):null;
    }
    MusicQueue.Entry entry(boolean left){return (left?previous:next).entry;}
    boolean previousRetained(){return previous.entry!=null && snapshot.current()==0;}
    boolean running(){return previous.layer.running() || next.layer.running() || previous.sharp.running() || next.sharp.running();}
    void finish(){previous.finish();next.finish();}
    void release(){visible(false);clearPending();previous.release();next.release();worker.shutdownNow();main.removeCallbacksAndMessages(null);}
    private static boolean sameArtwork(Bitmap first,Bitmap second) {
        if(first==second)return true;
        if(first==null || second==null || first.isRecycled() || second.isRecycled())return false;
        try{return first.sameAs(second);}catch(RuntimeException unavailable){return false;}
    }
    private void clearPending(){main.removeCallbacks(timeout);pending=null;pendingToken=null;}
    private final class Slot {
        final boolean left;
        final ContentLayer sharp=new ContentLayer(120,120,true,()->redraw.run(),()->changed.run());
        final ContentLayer frostLeft=new ContentLayer(120,120,false,()->redraw.run(),()->changed.run());
        final ContentLayer frostRight=new ContentLayer(120,120,false,()->redraw.run(),()->changed.run());
        final ContentLayer layer;
        MusicQueue.Entry entry;MediaSession.Token token;
        QueueArtwork.Ticket ticket;Future<?> job;int generation;boolean ready,attempted,skipReveal;
        Bitmap stagedLeft,stagedRight,stagedDeepLeft,stagedDeepRight;
        Slot(boolean left){this.left=left;layer=left?frostLeft:frostRight;}
        void bind(MusicQueue.Entry value,MediaSession.Token session) {
            boolean same=Objects.equals(token,session) && entry!=null && value!=null && entry.key().equals(value.key())
                && sameArtwork(entry.artwork(),value.artwork()) && Objects.equals(entry.artworkUri(),value.artworkUri());
            if(same)return;
            if(entry==null && value==null && Objects.equals(token,session))return;
            pause();entry=value;token=session;ready=false;attempted=false;skipReveal=false;
            // Fade the former neighbor away while its replacement loads; it is never a stale tap target.
            sharp.replace(null,false,0,0);frostLeft.replace(null,visible && ValueAnimator.areAnimatorsEnabled(),0,0);frostRight.replace(null,visible && ValueAnimator.areAnimatorsEnabled(),0,0);
            load();redraw.run();
        }
        void load() {
            if(!visible || entry==null || ready || attempted || job!=null || ticket!=null)return;
            attempted=true;int stamp=generation;
            if(entry.artwork()!=null && !entry.artwork().isRecycled())prepare(entry.artwork(),stamp);
            else if(entry.artworkUri()!=null)ticket=artwork.load(entry.artworkUri(),(bitmap,cached)->{
                ticket=null;if(stamp!=generation)return;if(bitmap!=null)prepare(bitmap,stamp);
            });
        }
        void prepare(Bitmap bitmap,int stamp) {
            job=worker.submit(()->{
                Bitmap leftImage=null,rightImage=null,sharpImage=null,deepLeft=null,deepRight=null;
                try {
                    leftImage=SideCoverFrost.create(bitmap,true);rightImage=SideCoverFrost.create(bitmap,false);
                    deepLeft=SideCoverFrost.deep(bitmap,true);deepRight=SideCoverFrost.deep(bitmap,false);
                    Bitmap readable=bitmap.getConfig()==Bitmap.Config.HARDWARE?bitmap.copy(Bitmap.Config.ARGB_8888,false):bitmap;
                    if(readable!=null) {
                        sharpImage=Bitmap.createBitmap(360,360,Bitmap.Config.ARGB_8888);
                        new Canvas(sharpImage).drawBitmap(readable,null,new android.graphics.Rect(0,0,360,360),new Paint(Paint.FILTER_BITMAP_FLAG));
                        if(readable!=bitmap)readable.recycle();
                    }
                }catch(RuntimeException unavailable){android.util.Log.d("AmbientCovers","artwork_unavailable");}
                Bitmap l=leftImage,r=rightImage,s=sharpImage,dl=deepLeft,dr=deepRight;
                main.post(()->{
                    if(stamp!=generation || !visible){recycle(l);recycle(r);recycle(s);recycle(dl);recycle(dr);return;}
                    job=null;ready=s!=null && l!=null && r!=null;
                    sharp.replace(s,false,0,0);
                    stagedLeft=l;stagedRight=r;stagedDeepLeft=dl;stagedDeepRight=dr;
                    if(!presentationBlocked)installStaged(!skipReveal);
                    redraw.run();changed.run();
                });
            });
        }
        void installStaged(boolean animate) {
            if(stagedLeft==null && stagedRight==null)return;
            Bitmap l=stagedLeft,r=stagedRight,dl=stagedDeepLeft,dr=stagedDeepRight;
            stagedLeft=stagedRight=stagedDeepLeft=stagedDeepRight=null;
            boolean motion=animate && visible && ValueAnimator.areAnimatorsEnabled();
            frostLeft.revealFrom(dl,l,motion);frostRight.revealFrom(dr,r,motion);
        }
        void finish(){installStaged(false);sharp.finish();frostLeft.finish();frostRight.finish();}
        void release(){sharp.release();frostLeft.release();frostRight.release();}
        private void recycle(Bitmap value){if(value!=null && !value.isRecycled())value.recycle();}
        void pause() {
            generation++;
            if(ticket!=null){ticket.cancel();ticket=null;}
            if(job!=null){job.cancel(true);if(job instanceof Runnable)worker.remove((Runnable)job);job=null;}
            if(!ready)attempted=false;finish();
        }
        void draw(Canvas canvas,float center,float top,float alpha) {
            if(entry==null || alpha<=0)return;
            canvas.save();canvas.translate(center-60,top);
            // The artwork already includes its soft rounded alpha silhouette. Never clip it sharply.
            layer.draw(canvas,null,alpha);canvas.restore();
        }
    }
}
