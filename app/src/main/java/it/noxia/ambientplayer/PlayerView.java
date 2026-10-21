package it.noxia.ambientplayer;

import android.content.Context;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.SystemClock;
import android.os.BatteryManager;
import android.view.WindowInsets;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.view.animation.PathInterpolator;
import com.google.android.material.motion.MotionUtils;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import androidx.appcompat.content.res.AppCompatResources;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.PlaybackState;
import android.os.Bundle;
import android.util.Log;
import androidx.core.graphics.ColorUtils;
import com.google.android.material.color.MaterialColors;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;

/** Static ambient surface with finite transitions into the interactive player. */
final class PlayerView extends View {
    interface Actions {
        void onActivate();
        void onToggle();
        void onInteract();
        void onUnlock();
        void onQueue();
        void onAccessory();
        default void onOpenMusic() { }
        default void onOpenNotification(android.app.PendingIntent action) { }
        void onVisualFrame();
        void onVisualSettled();
        default void onStatusChanged() { }
    }
    private static final Actions NO_ACTIONS = new Actions() {
        public void onActivate() { }
        public void onToggle() { }
        public void onInteract() { }
        public void onUnlock() { }
        public void onQueue() { }
        public void onAccessory() { }
        public void onVisualFrame() { }
        public void onVisualSettled() { }
    };
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path coverClip = new Path();
    private final RectF coverRect = new RectF(-60, -238, 60, -118);
    private final RectF ambientCoverRect = new RectF(-60, -163, 60, -43);
    private final RectF animatedCoverRect = new RectF();
    private final RectF backdropRect = new RectF();
    private int playerLeft,playerWidth;
    private boolean twoColumns;
    private float columnProgress,queueReveal;
    void setPlayerViewport(int left,int width,boolean split) {
        if(playerLeft==left && playerWidth==width && twoColumns==split)return;
        if(transition!=null)transition.end();
        resetCoverMotion();
        playerLeft=left;playerWidth=width;twoColumns=split;
        columnProgress=split && active?1:0;queueReveal=split && active?1:0;invalidate();
    }
    private float contentWidth(){return playerWidth>0?playerWidth:getWidth();}
    float contentCenterX(){return mix(getWidth()/2f,playerLeft+contentWidth()/2f,columnProgress);}
    float columnProgress(){return columnProgress;}
    float queueReveal(){return queueReveal;}
    boolean columnsMoving(){return twoColumns && isTransitionRunning();}
    private static final ExecutorService ARTWORK_WORKER = Executors.newSingleThreadExecutor();
    private MediaMetadata displayedMetadata;
    private ContentLayer coverLayer,titleLayer,artistLayer;
    private AccessorySupport audioOutput;
    private AdjacentCovers adjacent;
    private float coverDownX,coverDownY,coverDrag;
    private boolean coverDown,coverSwiping,swipeStartedActive;
    private MusicFeedback.Target coverTransportTouch,coverTransportPending;
    private int coverTransportDirection;
    private boolean coverTransportMoving;
    private final Runnable coverTransportTimeout=()->animateCoverTo(0,this::clearFrozenRow);
    private final android.util.LongSparseArray<android.app.PendingIntent> notificationTouches=new android.util.LongSparseArray<>();
    private final android.util.LongSparseArray<AdjacentCovers.Anchor> coverTouches=new android.util.LongSparseArray<>();
    private ValueAnimator coverReturn;
    private CarouselFrame frozenRow;
    private final CoverDrag coverGesture=new CoverDrag();
    private int coverCommitDirection,coverDetents;
    private boolean coverCommitConfirmed,coverExternalCommit;
    private android.media.session.MediaSession.Token metadataToken;
    private int transportDirection;
    private long transportDirectionUntil;
    private MusicQueue.Entry coverCommitEntry,coverCommitOriginEntry,lastHandoffTarget,lastHandoffOrigin;
    private android.media.session.MediaSession.Token lastHandoffToken;
    private Bitmap coverOriginArtwork,coverOriginQueueArtwork;
    private Bitmap carouselFallback,carouselFallbackOrigin,carouselFallbackQueueOrigin;
    private MusicQueue.Entry carouselFallbackEntry;
    private android.media.session.MediaSession.Token carouselFallbackToken;
    private QueueSelection.Item coverCommitOrigin;
    private android.media.session.MediaSession.Token coverCommitToken;
    private Bitmap centerFrostLeft,centerFrostRight;
    private volatile int centerFrostGeneration;
    private final Runnable hapticEcho=()->{if(coverSwiping && coverGesture.armed()!=0 && this.active && isShown())performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);};
    private final Runnable coverRecovery=()->{
        adjacentChanged();
        if(coverCommitDirection==0)return;
        if(coverCommitConfirmed){
            if(adjacent.currentIs(coverCommitToken,coverCommitEntry.key()))finishCoverCommit(true);
            else resetCoverMotion();
            return;
        }
        coverCommitDirection=0;animateCoverTo(0,this::clearFrozenRow);
    };
    private Bitmap contentArtwork;
    private String contentTitle,contentArtist;
    private PlaybackState displayedState;
    private final Drawable playIcon, pauseIcon, previousIcon, nextIcon, arrowIcon, musicIcon;
    private String clockText="";
    private int batteryPercent=-1;
    private boolean charging,statusRegistered;
    private final BroadcastReceiver statusEvents=new BroadcastReceiver() {
        @Override public void onReceive(Context context,Intent intent) {
            refreshStatus(intent);
        }
    };
    private Bitmap backdropSource;
    private Bitmap backdrop;
    private Bitmap previousBackdrop;
    private float backdropProgress = 1f;
    private ValueAnimator backdropTransition;
    private NotificationIcons.Snapshot activeNotifications = NotificationIcons.Snapshot.EMPTY;
    private NotificationIcons.Snapshot ambientNotifications = NotificationIcons.Snapshot.EMPTY;
    private int backdropGeneration;
    private android.graphics.Insets contentCutout=android.graphics.Insets.NONE;
    private float activeProgress,queueProgress;
    private float activeNotificationPresence,ambientNotificationPresence;
    private ValueAnimator notificationLayoutTransition;
    private ValueAnimator transition;
    private final Typeface regular = Typeface.create("sans-serif", Typeface.NORMAL);
    private final Typeface boldFace = Typeface.create("sans-serif", Typeface.BOLD);
    private final boolean probe;
    private final Actions actions;
    private final GestureDetector gestures;
    private boolean active;
    private int position;
    private final int accent;
    private final boolean preview;
    // Physical pixels, independent of screen size and canvas scale. Every step is 2 px.
    private static final int[][] OFFSETS = {
        {0,0},{2,0},{4,0},{4,2},{4,4},{2,4},{0,4},{-2,4},{-4,4},{-4,2},
        {-4,0},{-4,-2},{-4,-4},{-2,-4},{0,-4},{2,-4},{4,-4},{4,-2},{2,-2},{0,-2}
    };

    public PlayerView(Context context) { this(context, false, NO_ACTIONS); }
    PlayerView(Context context, boolean probe, Actions actions) {
        super(context);
        this.probe = probe;
        this.preview = context instanceof PreviewActivity;
        int primary = MaterialColors.getColor(context, androidx.appcompat.R.attr.colorPrimary, Color.LTGRAY);
        accent = MaterialColors.getColorRoles(primary, false).getAccent();
        this.actions = actions;
        playIcon = AppCompatResources.getDrawable(context,R.drawable.ic_play);
        pauseIcon = AppCompatResources.getDrawable(context,R.drawable.ic_pause);
        previousIcon = AppCompatResources.getDrawable(context,R.drawable.ic_skip_back);
        nextIcon = AppCompatResources.getDrawable(context,R.drawable.ic_skip_forward);
        arrowIcon = AppCompatResources.getDrawable(context,R.drawable.ic_arrow_up);
        musicIcon = AppCompatResources.getDrawable(context,R.drawable.ic_music);
        refreshStatus(null);
        coverLayer=new ContentLayer(120,120,true,this::invalidate,this::contentSettled);
        titleLayer=new ContentLayer(320,35,false,this::invalidate,this::contentSettled);
        artistLayer=new ContentLayer(320,28,false,this::invalidate,this::contentSettled);
        audioOutput=new AccessorySupport(context,this::refreshListening);
        adjacent=new AdjacentCovers(context,this::invalidate,this::adjacentChanged);
        refreshMedia();
        refreshNotifications();
        // Keep the default display-list renderer. A full-window software layer can
        // exceed Android's drawing-cache limit when the Fold opens to its inner display.
        gestures = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDown(MotionEvent event) { return true; }
            @Override public boolean onDoubleTap(MotionEvent event) {
                PlayerView.this.actions.onToggle();
                return true;
            }
            @Override public boolean onSingleTapConfirmed(MotionEvent event) {
                handleSingleTap(event);
                return true;
            }
            @Override public boolean onFling(MotionEvent first, MotionEvent last, float vx, float vy) {
                if(active && first!=null && !coverBandHit(first.getX(),first.getY()) && last.getX()-first.getX()>80*getResources().getDisplayMetrics().density
                        && Math.abs(last.getX()-first.getX())>Math.abs(last.getY()-first.getY())*1.4f) {
                    PlayerView.this.actions.onQueue();return true;
                }
                return false;
            }
        });
        setFocusable(true);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        updateDescription();
    }
    void setActive(boolean value) {
        if (active == value) return;
        active = value;
        if(!value)resetCoverMotion();
        updateDescription();
        if (transition != null) transition.cancel();
        float target = value ? 1f : 0f;
        if (!isAttachedToWindow() || !ValueAnimator.areAnimatorsEnabled()) {
            activeProgress = target;
            columnProgress=twoColumns?target:0;queueReveal=twoColumns?target:0;
            actions.onVisualFrame();
            invalidate();
            return;
        }
        float fromPlayer=activeProgress,fromColumns=columnProgress,fromQueue=queueReveal;
        boolean staged=twoColumns;
        android.animation.TimeInterpolator easing=MotionUtils.resolveThemeInterpolator(getContext(),
            com.google.android.material.R.attr.motionEasingEmphasizedInterpolator,
            new PathInterpolator(0.2f, 0f, 0f, 1f));
        transition = ValueAnimator.ofFloat(staged?0:activeProgress, staged?1:target);
        float distance=staged?Math.max(Math.abs(target-fromPlayer),Math.max(Math.abs(target-fromColumns),Math.abs(target-fromQueue))):Math.abs(target-fromPlayer);
        transition.setDuration(Math.max(1,Math.round((staged?value?ColumnTransition.OPEN_MS:ColumnTransition.CLOSE_MS:320)*distance)));
        transition.setInterpolator(staged?new android.view.animation.LinearInterpolator():easing);
        transition.addUpdateListener(animation -> {
            float time=(float)animation.getAnimatedValue();
            if(staged) {
                ColumnTransition.Stages phase=ColumnTransition.at(time,value);
                activeProgress=mix(fromPlayer,target,easing.getInterpolation(phase.player()));
                columnProgress=mix(fromColumns,target,easing.getInterpolation(phase.columns()));
                queueReveal=mix(fromQueue,target,easing.getInterpolation(phase.queue()));
            } else activeProgress=time;
            if (activeProgress == 0) finishBackdropTransition();
            actions.onVisualFrame();
            invalidate();
        });
        transition.addListener(new AnimatorListenerAdapter() {
            private boolean cancelled;
            @Override public void onAnimationCancel(Animator animation) { cancelled = true; }
            @Override public void onAnimationEnd(Animator animation) {
                if (transition == animation) transition = null;
                if (!cancelled){actions.onVisualFrame();actions.onVisualSettled();}
            }
        });
        transition.start();
    }
    boolean isTransitionRunning() { return transition != null && transition.isRunning(); }
    boolean isContentTransitionRunning() {
        return notificationLayoutTransition!=null || coverLayer!=null && (coverLayer.running() || titleLayer.running() || artistLayer.running() || adjacent!=null && adjacent.running() || coverReturn!=null);
    }
    private void contentSettled() { actions.onVisualSettled(); }
    private void finishContentTransitions() {
        finishNotificationLayout();
        if(coverLayer!=null) { coverLayer.finish();titleLayer.finish();artistLayer.finish(); }
        if(adjacent!=null)adjacent.finish();
        resetCoverMotion();
    }
    boolean isBackdropReady() { return backdrop != null; }
    boolean isBackdropTransitionRunning() { return backdropTransition != null && backdropTransition.isRunning(); }
    float backdropProgress() { return backdropProgress; }
    float progress() { return activeProgress; }
    boolean isActiveTarget(){return active;}
    void drawStatusOverlay(Canvas canvas){drawStatus(canvas,activeProgress);}
    void setQueueProgress(float value) { queueProgress=value;updateAdjacentVisibility();invalidate(); }
    boolean feedbackReady(MediaController media,MediaMetadata metadata){
        if(columnsMoving() || media==null || metadata==null || displayedMetadata==null || coverCommitDirection!=0 || coverSwiping || coverReturn!=null)return false;
        return MusicFeedback.identity(metadata)!=null && java.util.Objects.equals(MusicFeedback.identity(metadata),MusicFeedback.identity(displayedMetadata))
            && adjacent!=null && adjacent.metadataMatchesCurrent(media.getSessionToken(),metadata);
    }
    boolean adjacentPending(){return coverTransportPending!=null || adjacent!=null && adjacent.pending();}
    int adjacentReady(){return adjacent==null?0:adjacent.readyCount();}
    int adjacentLoading(){return adjacent==null?0:adjacent.loadingCount();}
    private void updateAdjacentVisibility() {
        if(adjacent!=null)adjacent.visible(isAttachedToWindow() && isShown() && getWindowVisibility()==VISIBLE && queueProgress==0);
        if(audioOutput!=null && !probe) {
            if(isAttachedToWindow() && isShown() && getWindowVisibility()==VISIBLE)audioOutput.start();
            else audioOutput.stop();
        }
    }
    @Override protected void onVisibilityChanged(View changed,int visibility) {
        super.onVisibilityChanged(changed,visibility);updateAdjacentVisibility();
        if(visibility!=VISIBLE)finishContentTransitions();
    }
    boolean coverBandHit(float physicalX,float physicalY) {
        float s=scale();if(s<=0)return false;
        if(activeProgress==1 && (physicalX<playerLeft || physicalX>=playerLeft+contentWidth()))return false;
        float y=(physicalY-getHeight()/2f-contentOffsetY())/s;
        float top=mix(ambientCoverRect.top,coverRect.top,activeProgress);
        // Reserve the entire horizontal strip, including the gaps and absent end cards.
        return y>=top-24 && y<=top+144;
    }
    private boolean sideCoverHit(float x,float y) {
        float top=mix(ambientCoverRect.top,coverRect.top,activeProgress),spacing=coverSpacing();
        return y>=top && y<=top+120 && Math.abs(Math.abs(x)-spacing)<=60;
    }
    private float coverSpacing(){float s=scale();return s<=0?188:contentWidth()/(2*s)-12;}
    float coverTravel(){return coverDrag;}
    int coverArmed(){return coverGesture.armed();}
    int coverDetents(){return coverDetents;}
    boolean previousCoverRetained(){return adjacent!=null && adjacent.previousRetained();}
    private void captureFrozenRow() {
        if(frozenRow!=null)return;
        frozenRow=new CarouselFrame(adjacent.card(true),new CarouselFrame.Card(coverLayer.snapshot(),CarouselFrame.copy(centerFrostLeft),CarouselFrame.copy(centerFrostRight)),adjacent.card(false));
        adjacent.presentationBlocked(true);
    }
    private void clearFrozenRow() {
        if(frozenRow!=null)frozenRow.release();frozenRow=null;coverDrag=0;coverCommitDirection=0;
        coverTransportPending=null;coverTransportMoving=false;coverTransportDirection=0;removeCallbacks(coverTransportTimeout);
        coverOriginArtwork=coverOriginQueueArtwork=null;coverCommitOriginEntry=null;coverCommitEntry=null;coverCommitToken=null;coverCommitConfirmed=false;coverExternalCommit=false;coverCommitOrigin=null;removeCallbacks(coverRecovery);
        if(adjacent!=null)adjacent.presentationBlocked(false);invalidate();
    }
    private void resetCoverMotion() {
        if(coverReturn!=null)coverReturn.cancel();coverReturn=null;
        coverDown=false;coverSwiping=false;if(coverGesture!=null)coverGesture.reset();removeCallbacks(hapticEcho);clearFrozenRow();
    }
    private boolean canNavigateCover(boolean previous) {
        return adjacent!=null && adjacent.available(previous) || transportAvailable(previous);
    }
    private boolean transportAvailable(boolean previous) {
        MediaController media=media();PlaybackState state=media==null?null:media.getPlaybackState();
        return state!=null && (state.getActions()&(previous?PlaybackState.ACTION_SKIP_TO_PREVIOUS:PlaybackState.ACTION_SKIP_TO_NEXT))!=0;
    }
    private boolean navigateCover(boolean previous) {
        if(adjacent==null || !adjacent.available(previous))return navigateTransportCover(previous,MusicFeedback.target(media(),displayedMetadata),true);
        return navigateCover(previous,adjacent.anchor(),true);
    }
    /** A published queue is optional: standard transport still works without neighboring metadata. */
    private boolean navigateTransportCover(boolean previous,MusicFeedback.Target touched,boolean click) {
        MediaController media=media();
        if(!active || activeProgress!=1 || columnsMoving() || queueProgress!=0 || coverCommitDirection!=0 || coverTransportPending!=null
            || touched==null || !touched.equals(MusicFeedback.target(media,media==null?null:media.getMetadata())) || !transportAvailable(previous))return false;
        captureFrozenRow();coverTransportPending=touched;coverTransportDirection=previous?1:-1;
        transportDirection=coverTransportDirection;transportDirectionUntil=SystemClock.uptimeMillis()+5000;
        postDelayed(coverTransportTimeout,5000);actions.onInteract();
        try {
            if(previous)media.getTransportControls().skipToPrevious();else media.getTransportControls().skipToNext();
        }catch(SecurityException|IllegalStateException unavailable){animateCoverTo(0,this::clearFrozenRow);return false;}
        if(click)performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM);
        return true;
    }
    private void observeTransportCover(MediaController media,MediaMetadata next) {
        if(coverTransportPending==null)return;
        if(media==null || !coverTransportPending.token().equals(media.getSessionToken())){resetCoverMotion();return;}
        String identity=MusicFeedback.identity(next);
        if(coverTransportMoving || identity==null || identity.equals(coverTransportPending.identity()))return;
        coverTransportMoving=true;removeCallbacks(coverTransportTimeout);
        Bitmap art=metadataArtwork(next);
        frozenRow.replaceDestination(coverTransportDirection,new CarouselFrame.Card(coverBitmap(art),null,null));
        animateCoverTo(coverTransportDirection*coverSpacing(),()->{coverLayer.finish();clearFrozenRow();actions.onVisualSettled();});
    }
    boolean requestSwipeUnlock(float dx,float dy) {
        if(!active || coverSwiping || queueProgress!=0 || columnsMoving() || -dy<=80*getResources().getDisplayMetrics().density || Math.abs(dx)>=-dy)return false;
        actions.onUnlock();return true;
    }
    private boolean navigateCover(boolean previous,AdjacentCovers.Anchor touched,boolean click) {
        if(columnsMoving() || !active || activeProgress!=1 || queueProgress!=0 || adjacent==null || coverCommitDirection!=0 || coverTransportPending!=null)return false;
        actions.onInteract();MusicQueue.Entry target=adjacent.entry(previous);
        if(target==null || touched==null)return false;
        captureFrozenRow();
        coverCommitDirection=previous?1:-1;coverCommitEntry=target;coverCommitToken=touched.token();coverCommitOrigin=touched.current();coverCommitConfirmed=false;
        coverOriginArtwork=metadataArtwork(displayedMetadata);
        MusicQueue.Snapshot origin=MusicQueue.read();coverCommitOriginEntry=origin.current()<0?null:origin.entries().get(origin.current());coverOriginQueueArtwork=coverCommitOriginEntry==null?null:coverCommitOriginEntry.artwork();
        // Establish the transaction before the command's synchronous status callbacks.
        if(!adjacent.select(previous,touched)){coverCommitDirection=0;animateCoverTo(0,this::clearFrozenRow);return false;}
        if(click)performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM);
        postDelayed(coverRecovery,5500);
        animateCoverTo(coverCommitDirection*coverSpacing(),this::finishCoverCommit);
        return true;
    }
    private void observeCoverChange(MediaController media,MediaMetadata next){
        if(adjacent==null || media==null || displayedMetadata==null || frozenRow!=null || coverSwiping || coverReturn!=null
                || columnsMoving() || queueProgress!=0 || !isAttachedToWindow() || !isShown() || getWindowVisibility()!=VISIBLE || !ValueAnimator.areAnimatorsEnabled())return;
        AdjacentCovers.Anchor anchor=adjacent.anchor();MusicQueue.Entry origin=adjacent.currentEntry();
        if(origin==null || !java.util.Objects.equals(anchor.token(),media.getSessionToken()) || !metadataMatches(displayedMetadata,origin))return;
        MusicQueue.Snapshot fresh=MusicQueue.read();MusicQueue.Entry target=null;boolean previous=false;
        for(boolean left:new boolean[]{false,true}){
            MusicQueue.Entry candidate=adjacent.entry(left);if(candidate==null)continue;
            boolean queueMoved=fresh.current()>=0 && fresh.entries().get(fresh.current()).key().equals(candidate.key());
            boolean metadataMoved=metadataMatches(next,candidate) && !metadataMatches(next,origin);
            if(queueMoved || metadataMoved){target=candidate;previous=left;break;}
        }
        if(target==null)return;
        captureFrozenRow();coverExternalCommit=true;coverCommitDirection=previous?1:-1;
        coverCommitEntry=target;coverCommitToken=anchor.token();coverCommitOrigin=anchor.current();coverCommitOriginEntry=origin;
        coverOriginArtwork=metadataArtwork(displayedMetadata);coverOriginQueueArtwork=origin.artwork();
        Bitmap targetArt=target.artwork();Bitmap received=metadataMatches(next,target)?metadataArtwork(next):null;
        if(received!=null && !sameArtwork(received,coverOriginArtwork) && !sameArtwork(received,coverOriginQueueArtwork))targetArt=received;
        if(!frozenRow.hasDestination(coverCommitDirection))frozenRow.ensureDestination(coverCommitDirection,new CarouselFrame.Card(coverBitmap(targetArt),null,null));
        coverCommitConfirmed=MusicQueue.confirmed(fresh,anchor.token(),target.key());
        postDelayed(coverRecovery,5500);animateCoverTo(coverCommitDirection*coverSpacing(),this::finishCoverCommit);
        Log.i("AmbientCovers","observed_slide direction="+coverCommitDirection);
    }
    private void adjacentChanged() {
        if(coverCommitDirection!=0 && adjacent!=null) {
            MusicQueue.Snapshot fresh=MusicQueue.read();
            if(coverCommitEntry!=null && (MusicQueue.confirmed(fresh,coverCommitToken,coverCommitEntry.key()) || coverExternalCommit && fresh.current()>=0 && fresh.entries().get(fresh.current()).key().equals(coverCommitEntry.key()) && metadataMatches(displayedMetadata,coverCommitEntry)))coverCommitConfirmed=true;
            boolean differentSession=fresh.token()!=null && !java.util.Objects.equals(fresh.token(),coverCommitToken);
            if(differentSession || !coverExternalCommit && adjacent.selectionFailed() && !coverCommitConfirmed) {
                coverCommitDirection=0;animateCoverTo(0,this::clearFrozenRow);
            } else {
                // Buffering, temporary queue gaps and callbacks carrying the former ID do
                // not revoke an already confirmed request or move the visible row backwards.
                if(fresh.current()>=0){MusicQueue.Entry now=fresh.entries().get(fresh.current());
                    if(!now.key().equals(coverCommitOrigin) && !now.key().equals(coverCommitEntry.key())
                        && metadataMatches(displayedMetadata,now) && fresh.playing()){resetCoverMotion();}
                }
                finishCoverCommit();
            }
        }
        if(isAttachedToWindow())actions.onVisualSettled();
    }
    private void finishCoverCommit(){finishCoverCommit(false);}
    private void finishCoverCommit(boolean allowMetadataDelay) {
        if(coverReturn!=null || !coverCommitConfirmed || !allowMetadataDelay && !metadataMatches(displayedMetadata,coverCommitEntry)
            || !adjacent.currentIs(coverCommitToken,coverCommitEntry.key()))return;
        if(contentArtwork==null || sameArtwork(contentArtwork,coverOriginArtwork) || sameArtwork(contentArtwork,coverOriginQueueArtwork)) {
            Bitmap image=frozenRow==null?null:frozenRow.destinationCopy(coverCommitDirection);
            if(image==null)image=coverBitmap(coverCommitEntry.artwork());
            if(image!=null){
                retireCarouselFallback();carouselFallback=image;carouselFallbackEntry=coverCommitEntry;carouselFallbackToken=coverCommitToken;
                carouselFallbackOrigin=coverOriginArtwork;carouselFallbackQueueOrigin=coverOriginQueueArtwork;
                contentArtwork=image;coverLayer.replace(coverBitmap(image),false,0,0);prepareCenterFrost(image);prepareBackdrop(image);
                Log.i("AmbientCovers","handoff_preserves_destination");
            } else coverLayer.finish();
        } else coverLayer.finish();
        lastHandoffTarget=coverCommitEntry;lastHandoffOrigin=coverCommitOriginEntry;lastHandoffToken=coverCommitToken;
        adjacent.preserveDepartingCover(coverCommitDirection<0,coverCommitOrigin);clearFrozenRow();actions.onVisualSettled();
    }
    private static Bitmap metadataArtwork(MediaMetadata metadata){
        if(metadata==null)return null;Bitmap art=metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
        return art!=null?art:metadata.getBitmap(MediaMetadata.METADATA_KEY_ART);
    }
    private static boolean metadataMatches(MediaMetadata metadata,MusicQueue.Entry entry){
        return metadata!=null && entry!=null && CarouselIdentity.matches(entry.key(),entry.title(),entry.artist(),
            metadata.getString(MediaMetadata.METADATA_KEY_MEDIA_ID),metadata.getString(MediaMetadata.METADATA_KEY_TITLE),metadata.getString(MediaMetadata.METADATA_KEY_ARTIST));
    }
    private static boolean sameArtwork(Bitmap first,Bitmap second){
        return first!=null && second!=null && !first.isRecycled() && !second.isRecycled() && (first==second || first.sameAs(second));
    }
    private Bitmap carouselArtwork(MediaMetadata metadata,Bitmap actual){
        if(carouselFallback==null)return actual;
        MediaController media=media();
        if(media!=null && java.util.Objects.equals(media.getSessionToken(),carouselFallbackToken)
            && (metadataMatches(metadata,carouselFallbackEntry) || metadataMatches(metadata,lastHandoffOrigin) && adjacent.currentIs(carouselFallbackToken,carouselFallbackEntry.key()))
            && (actual==null || sameArtwork(actual,carouselFallbackOrigin) || sameArtwork(actual,carouselFallbackQueueOrigin)))return carouselFallback;
        retireCarouselFallback();return actual;
    }
    private void retireCarouselFallback(){
        Bitmap retired=carouselFallback;carouselFallback=null;carouselFallbackOrigin=carouselFallbackQueueOrigin=null;carouselFallbackEntry=null;carouselFallbackToken=null;
        // Source readers share this executor: recycle only after their queued blur jobs.
        if(retired!=null)ARTWORK_WORKER.execute(()->{if(!retired.isRecycled())retired.recycle();});
    }
    private void animateCoverTo(float target,Runnable settled) {
        if(coverReturn!=null)coverReturn.cancel();coverReturn=null;
        if(coverDrag==target || !isShown() || !ValueAnimator.areAnimatorsEnabled()) {coverDrag=target;settled.run();invalidate();return;}
        ValueAnimator motion=ValueAnimator.ofFloat(coverDrag,target);coverReturn=motion;
        motion.setDuration(Math.max(220,Math.min(640,Math.round(560*Math.abs(target-coverDrag)/coverSpacing()))));
        motion.setInterpolator(new PathInterpolator(.35f,0,.15f,1));
        motion.addUpdateListener(a->{coverDrag=(float)a.getAnimatedValue();invalidate();});
        motion.addListener(new AnimatorListenerAdapter(){
            private boolean cancelled;
            @Override public void onAnimationCancel(Animator a){cancelled=true;}
            @Override public void onAnimationEnd(Animator a){if(coverReturn==a)coverReturn=null;if(!cancelled)settled.run();actions.onVisualSettled();}
        });motion.start();actions.onVisualSettled();
    }
    private void prepareCenterFrost(Bitmap art) {
        int stamp=++centerFrostGeneration;
        if(centerFrostLeft!=null)centerFrostLeft.recycle();if(centerFrostRight!=null)centerFrostRight.recycle();centerFrostLeft=null;centerFrostRight=null;
        if(art==null || art.isRecycled())return;
        ARTWORK_WORKER.execute(()->{
            if(stamp!=centerFrostGeneration)return;
            Bitmap l=null,r=null;
            try{l=SideCoverFrost.create(art,true);r=SideCoverFrost.create(art,false);}catch(RuntimeException unavailable){Log.d("AmbientCovers","center_frost_unavailable");}
            Bitmap left=l,right=r;
            post(()->{
                if(stamp!=centerFrostGeneration){if(left!=null)left.recycle();if(right!=null)right.recycle();return;}
                centerFrostLeft=left;centerFrostRight=right;
            });
        });
    }
    float offsetX() { return OFFSETS[position][0] * (1 - activeProgress); }
    float offsetY() { return OFFSETS[position][1] * (1 - activeProgress); }
    float contentOffsetY() {
        return offsetY()+PlayerContentLayout.offsetPx(activeProgress,ambientNotificationPresence,activeNotificationPresence,scale(),contentCutout.top,contentCutout.bottom);
    }
    int contentSafeTop(){return contentCutout.top;}
    int contentSafeBottom(){return contentCutout.bottom;}
    @Override public WindowInsets onApplyWindowInsets(WindowInsets insets) {
        android.graphics.Insets next=insets.getInsetsIgnoringVisibility(WindowInsets.Type.displayCutout());
        if(!next.equals(contentCutout)) {
            contentCutout=next;actions.onVisualFrame();invalidate();
        }
        return super.onApplyWindowInsets(insets);
    }
    private static float mix(float start, float end, float fraction) { return start + (end - start) * fraction; }
    private static int opacity(int color, float fraction) {
        return ColorUtils.setAlphaComponent(color, Math.round(Color.alpha(color) * Math.max(0, Math.min(1, fraction))));
    }
    private void prepareBackdrop(Bitmap artwork) {
        if (artwork == backdropSource) return;
        if (artwork != null && backdropSource != null && !backdropSource.isRecycled() && artwork.sameAs(backdropSource)) {
            backdropSource = artwork;
            return;
        }
        backdropSource = artwork;
        int generation = ++backdropGeneration;
        // Keep the displayed frost until the next one is ready: no black flash during blur.
        if (artwork == null || artwork.isRecycled()) { changeBackdrop(null); return; }
        ARTWORK_WORKER.execute(() -> {
            Bitmap ready;
            try { ready = ArtworkFrost.create(artwork); }
            catch (IllegalArgumentException | IllegalStateException unavailable) { ready = null; }
            final Bitmap result = ready;
            post(() -> {
                if (generation != backdropGeneration) {
                    if (result != null) result.recycle();
                    return;
                }
                changeBackdrop(result);
            });
        });
    }
    private void changeBackdrop(Bitmap next) {
        Bitmap displayed = backdrop;
        if (isBackdropTransitionRunning()) {
            Log.i("AmbientFrost","transition_interrupted progress="+backdropProgress);
            // A rapid second change starts from the exact current blend, not the last cover.
            displayed = Bitmap.createBitmap(64,64,Bitmap.Config.ARGB_8888);
            drawBackdropBlend(new Canvas(displayed),64,64);
        }
        if (backdropTransition != null) backdropTransition.cancel();
        recycleBackdrop(previousBackdrop,displayed,next);
        recycleBackdrop(backdrop,displayed,next);
        previousBackdrop = displayed;
        backdrop = next;
        backdropProgress = 0;
        if (activeProgress == 0 || !isAttachedToWindow() || !isShown() || getWindowVisibility() != VISIBLE
                || !ValueAnimator.areAnimatorsEnabled()) {
            finishBackdropTransition();
            if (activeProgress > 0) invalidate();
            return;
        }
        backdropTransition = ValueAnimator.ofFloat(0,1);
        backdropTransition.setDuration(1600);
        backdropTransition.setInterpolator(new PathInterpolator(0.4f,0f,0.2f,1f));
        backdropTransition.addUpdateListener(animation -> {
            backdropProgress = (float) animation.getAnimatedValue();
            invalidate();
        });
        backdropTransition.addListener(new AnimatorListenerAdapter() {
            private boolean cancelled;
            @Override public void onAnimationCancel(Animator animation) { cancelled=true; }
            @Override public void onAnimationEnd(Animator animation) {
                if (backdropTransition == animation) backdropTransition = null;
                if (!cancelled) {
                    recycleBackdrop(previousBackdrop,backdrop,null);
                    previousBackdrop = null;
                    backdropProgress = 1;
                    Log.i("AmbientFrost","transition_finished");
                    actions.onVisualSettled();
                }
            }
        });
        Log.i("AmbientFrost","transition_started duration=1600");
        backdropTransition.start();
    }
    private static void recycleBackdrop(Bitmap bitmap,Bitmap keep,Bitmap alsoKeep) {
        if (bitmap != null && bitmap != keep && bitmap != alsoKeep && !bitmap.isRecycled()) bitmap.recycle();
    }
    private void finishBackdropTransition() {
        if (backdropTransition != null) backdropTransition.cancel();
        backdropTransition = null;
        recycleBackdrop(previousBackdrop,backdrop,null);
        previousBackdrop = null;
        backdropProgress = 1;
    }
    void settleHiddenTransitions() {
        if (transition!=null) transition.end();
        finishBackdropTransition();
        finishContentTransitions();
    }
    private void drawBackdropBitmap(Canvas canvas,Bitmap bitmap,int width,int height,float opacity) {
        if (bitmap == null || bitmap.isRecycled() || opacity <= 0) return;
        float fill = Math.max(width/(float)bitmap.getWidth(),height/(float)bitmap.getHeight());
        float w = bitmap.getWidth()*fill, h = bitmap.getHeight()*fill;
        backdropRect.set((width-w)/2,(height-h)/2,(width+w)/2,(height+h)/2);
        paint.setStyle(Paint.Style.FILL); paint.setColor(Color.WHITE);
        paint.setAlpha(Math.round(255*opacity)); paint.setFilterBitmap(true);
        canvas.drawBitmap(bitmap,null,backdropRect,paint);
        paint.setAlpha(255);
    }
    private void drawBackdropBlend(Canvas canvas,int width,int height) {
        drawBackdropBitmap(canvas,previousBackdrop,width,height,backdrop == null ? 1-backdropProgress : 1);
        drawBackdropBitmap(canvas,backdrop,width,height,backdropProgress);
    }
    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        requestApplyInsets();
        IntentFilter filter=new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        filter.addAction(Intent.ACTION_TIME_TICK);filter.addAction(Intent.ACTION_TIME_CHANGED);
        filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);filter.addAction(Intent.ACTION_LOCALE_CHANGED);
        Intent battery=android.os.Build.VERSION.SDK_INT>=33
            ?getContext().registerReceiver(statusEvents,filter,Context.RECEIVER_NOT_EXPORTED)
            :getContext().registerReceiver(statusEvents,filter);
        statusRegistered=true;refreshStatus(battery);updateAdjacentVisibility();refreshListening();
    }
    private void refreshStatus(Intent intent) {
        clockText=android.text.format.DateFormat.getTimeFormat(getContext()).format(new java.util.Date());
        if(intent!=null && Intent.ACTION_BATTERY_CHANGED.equals(intent.getAction())) {
            int level=intent.getIntExtra(BatteryManager.EXTRA_LEVEL,-1);
            int total=intent.getIntExtra(BatteryManager.EXTRA_SCALE,100);
            batteryPercent=level<0 || total<=0?-1:Math.max(0,Math.min(100,Math.round(level*100f/total)));
            int status=intent.getIntExtra(BatteryManager.EXTRA_STATUS,BatteryManager.BATTERY_STATUS_UNKNOWN);
            charging=status==BatteryManager.BATTERY_STATUS_CHARGING || status==BatteryManager.BATTERY_STATUS_FULL
                && intent.getIntExtra(BatteryManager.EXTRA_PLUGGED,0)!=0;
        }
        updateDescription();
        actions.onStatusChanged();
        if(isShown() && getWindowVisibility()==VISIBLE)invalidate();
    }
    String clockText() { return clockText; }
    int batteryPercent() { return batteryPercent; }
    boolean isCharging() { return charging; }
    private void drawStatus(Canvas canvas,float fraction) {
        float s=Math.min(getWidth()/400f,getHeight()/650f);if(s<=0)return;
        android.graphics.Insets safe=android.graphics.Insets.NONE;
        WindowInsets insets=getRootWindowInsets();
        if(insets!=null)safe=insets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
        float left=Math.max(24,safe.left/s+16),right=getWidth()/s-Math.max(24,safe.right/s+16);
        // Short edge-aligned values can occupy the system status row beside the camera.
        float baseline=statusBaseline(safe.top,s);
        int color=ColorUtils.blendARGB(Color.rgb(145,155,149),Color.rgb(238,242,239),fraction);
        canvas.save();canvas.translate(offsetX(),offsetY());canvas.scale(s,s);
        paint.setTextAlign(Paint.Align.LEFT);text(canvas,clockText,left,baseline,14,color,true);
        String batteryText=batteryPercent<0?"—":batteryPercent+"%";
        paint.setTextAlign(Paint.Align.RIGHT);text(canvas,batteryText,right,baseline,14,color,false);
        canvas.restore();
    }
    static float statusBaseline(int safeTop,float scale) {
        return Math.max(28,safeTop/scale-6);
    }
    @Override protected void onDetachedFromWindow() {
        notificationTouches.clear();
        if(statusRegistered){getContext().unregisterReceiver(statusEvents);statusRegistered=false;}
        if(audioOutput!=null)audioOutput.stop();
        retireCarouselFallback();
        finishNotificationLayout();
        if (transition != null) transition.cancel();
        finishBackdropTransition();
        recycleBackdrop(backdrop,null,null);
        backdrop = null;
        backdropSource = null;
        backdropGeneration++;
        coverLayer.release();titleLayer.release();artistLayer.release();
        if(adjacent!=null)adjacent.release();resetCoverMotion();centerFrostGeneration++;
        if(centerFrostLeft!=null)centerFrostLeft.recycle();if(centerFrostRight!=null)centerFrostRight.recycle();centerFrostLeft=null;centerFrostRight=null;
        contentArtwork=null;contentTitle=null;contentArtist=null;
        super.onDetachedFromWindow();
    }
    @Override protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (visibility != VISIBLE && transition != null) transition.end();
        if (visibility != VISIBLE) { finishBackdropTransition();finishContentTransitions(); }
        updateAdjacentVisibility();
    }
    private void updateDescription() {
        String description=getContext().getString(preview?R.string.preview_hint:active?R.string.player_active_description:R.string.player_ambient_description);
        if(!clockText.isEmpty())description+=" "+clockText;
        if(batteryPercent>=0)description+=". "+getContext().getString(R.string.phone_battery_description,batteryPercent)
            +(charging?", "+getContext().getString(R.string.charging_description):"");
        setContentDescription(description);
    }
    void shiftPosition() { position = (position + 1) % OFFSETS.length; actions.onStatusChanged(); invalidate(); }
    private int tint(float amount) { return ColorUtils.blendARGB(Color.BLACK, accent, amount); }
    private float scale() { return Math.min(contentWidth()/400f, getHeight()/650f); }
    private void text(Canvas c, String value, float x, float y, float size, int color, boolean bold) {
        paint.setColor(color); paint.setStyle(Paint.Style.FILL); paint.setTextSize(size);
        paint.setTypeface(bold ? boldFace : regular);
        c.drawText(value, x, y, paint);
    }
    private String fit(String value, float size, float width) {
        paint.setTextSize(size);
        if (paint.measureText(value) <= width) return value;
        while (value.length() > 1 && paint.measureText(value + "…") > width)
            value = value.substring(0, value.offsetByCodePoints(value.length(), -1));
        return value + "…";
    }
    private MediaController media() { return probe ? null : MusicSessionService.current; }
    void refreshNotifications() {
        activeNotifications = probe ? NotificationIcons.Snapshot.EMPTY : MusicSessionService.notificationIcons(false);
        ambientNotifications = probe ? NotificationIcons.Snapshot.EMPTY : MusicSessionService.notificationIcons(true);
        updateNotificationLayout();
        refreshListening();
        invalidate();
    }
    private void updateNotificationLayout() {
        float nextActive=activeNotifications.icons().isEmpty()?0:1;
        float nextAmbient=ambientNotifications.icons().isEmpty()?0:1;
        if(notificationLayoutTransition!=null)notificationLayoutTransition.cancel();
        notificationLayoutTransition=null;
        if(nextActive==activeNotificationPresence && nextAmbient==ambientNotificationPresence)return;
        if(!isAttachedToWindow() || !isShown() || getWindowVisibility()!=VISIBLE || !ValueAnimator.areAnimatorsEnabled()) {
            activeNotificationPresence=nextActive;ambientNotificationPresence=nextAmbient;
            actions.onVisualFrame();invalidate();return;
        }
        float fromActive=activeNotificationPresence,fromAmbient=ambientNotificationPresence;
        notificationLayoutTransition=ValueAnimator.ofFloat(0,1);
        notificationLayoutTransition.setDuration(360);
        notificationLayoutTransition.setInterpolator(new PathInterpolator(.2f,0,0,1));
        notificationLayoutTransition.addUpdateListener(animation->{
            float t=(float)animation.getAnimatedValue();
            activeNotificationPresence=mix(fromActive,nextActive,t);
            ambientNotificationPresence=mix(fromAmbient,nextAmbient,t);
            actions.onVisualFrame();invalidate();
        });
        notificationLayoutTransition.addListener(new AnimatorListenerAdapter(){
            @Override public void onAnimationEnd(Animator animation){
                if(notificationLayoutTransition==animation){notificationLayoutTransition=null;actions.onVisualSettled();}
            }
        });
        notificationLayoutTransition.start();actions.onVisualSettled();
    }
    private void finishNotificationLayout(){
        if(notificationLayoutTransition!=null)notificationLayoutTransition.end();
        notificationLayoutTransition=null;
    }
    private void refreshListening() {
        updateDescription();invalidate();actions.onStatusChanged();
    }
    String audioOutputName(){return audioOutput==null?"":audioOutput.name();}
    boolean accessoryConnected(){return audioOutput!=null && audioOutput.connected();}
    AccessoryBattery accessoryReading(){return accessoryConnected()?MusicSessionService.accessoryBattery():null;}
    boolean audioRouteListening(){return audioOutput!=null && audioOutput.listening();}
    void refreshMedia() {
        MediaController media = media();
        MediaMetadata next = media == null ? null : media.getMetadata();
        observeTransportCover(media,next);
        // A temporary metadata gap must not flash the placeholder between songs.
        boolean staleOrigin=false;
        if(coverCommitDirection==0 && next!=null && media!=null && lastHandoffTarget!=null
            && java.util.Objects.equals(media.getSessionToken(),lastHandoffToken) && metadataMatches(next,lastHandoffOrigin)) {
            MusicQueue.Snapshot fresh=MusicQueue.read();
            staleOrigin=fresh.current()>=0 && fresh.entries().get(fresh.current()).key().equals(lastHandoffTarget.key());
        }
        MediaMetadata before=displayedMetadata;
        if(!staleOrigin)observeCoverChange(media,next);
        boolean identityChanged=!staleOrigin && before!=null && next!=null && !java.util.Objects.equals(MusicFeedback.identity(before),MusicFeedback.identity(next));
        int fallbackDirection=identityChanged && media!=null && java.util.Objects.equals(metadataToken,media.getSessionToken())?(transportDirectionUntil>=SystemClock.uptimeMillis()?transportDirection:-1):0;
        metadataToken=media==null?null:media.getSessionToken();
        if(identityChanged){transportDirection=0;transportDirectionUntil=0;}
        if(!staleOrigin && (next!=null || displayedMetadata==null))displayedMetadata=next;
        displayedState = media == null ? null : media.getPlaybackState();
        Bitmap cover=carouselArtwork(displayedMetadata,metadataArtwork(displayedMetadata));
        String title=displayedMetadata==null?getContext().getString(R.string.your_music):displayedMetadata.getString(MediaMetadata.METADATA_KEY_TITLE);
        String artist=displayedMetadata==null?getContext().getString(R.string.music_space):displayedMetadata.getString(MediaMetadata.METADATA_KEY_ARTIST);
        if(title==null)title=getContext().getString(R.string.your_music);
        if(artist==null)artist="";
        boolean animate=queueProgress<1 && isAttachedToWindow() && isShown() && getWindowVisibility()==VISIBLE && ValueAnimator.areAnimatorsEnabled();
        boolean sameArt=cover==contentArtwork || cover!=null && contentArtwork!=null
            && !cover.isRecycled() && !contentArtwork.isRecycled() && cover.sameAs(contentArtwork);
        if(!sameArt || contentTitle==null || frozenRow==null && fallbackDirection!=0) {
            contentArtwork=cover;
            if(frozenRow==null && fallbackDirection!=0)coverLayer.slide(coverBitmap(cover),animate,-fallbackDirection*120);
            else coverLayer.replace(coverBitmap(cover),animate,0,0);
            prepareCenterFrost(cover);
        }
        if(!title.equals(contentTitle)) {
            contentTitle=title;
            titleLayer.replace(textBitmap(title,22,320,35,30,true),animate,activeProgress>0?28:0,0);
        }
        if(!artist.equals(contentArtist)) {
            contentArtist=artist;
            artistLayer.replace(textBitmap(artist,14,320,28,20,false),animate,activeProgress>0?20:0,activeProgress>0?45:0);
        }
        prepareBackdrop(cover);
        if(adjacent!=null)adjacent.refresh(probe?MusicQueue.Snapshot.EMPTY:MusicQueue.read());
        adjacentChanged();
        refreshListening();
        invalidate();
    }
    private Bitmap coverBitmap(Bitmap art) {
        Bitmap bitmap=Bitmap.createBitmap(360,360,Bitmap.Config.ARGB_8888);
        Canvas canvas=new Canvas(bitmap);canvas.scale(3,3);canvas.drawColor(Color.BLACK);
        paint.setStyle(Paint.Style.FILL);paint.setColor(Color.WHITE);paint.setAlpha(255);paint.setFilterBitmap(true);
        if(art!=null && !art.isRecycled())canvas.drawBitmap(art,null,new RectF(0,0,120,120),paint);
        else {
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1);paint.setColor(tint(.22f));
            canvas.drawRoundRect(.5f,.5f,119.5f,119.5f,28,28,paint);
            icon(canvas,musicIcon,60,60,36,accent,1);
        }
        return bitmap;
    }
    private Bitmap textBitmap(String value,float size,int width,int height,float baseline,boolean bold) {
        Bitmap bitmap=Bitmap.createBitmap(width*3,height*3,Bitmap.Config.ARGB_8888);
        Canvas canvas=new Canvas(bitmap);canvas.scale(3,3);
        paint.setTypeface(bold?boldFace:regular);paint.setTextAlign(Paint.Align.CENTER);
        text(canvas,fit(value,size,310),width/2f,baseline,size,Color.WHITE,bold);
        return bitmap;
    }
    @Override protected void onDraw(Canvas canvas) {
        canvas.drawColor(Color.BLACK);
        float fraction = activeProgress;
        MediaController media = media();
        PlaybackState state = displayedState;
        boolean playing = state != null && state.getState() == PlaybackState.STATE_PLAYING;
        if (fraction > 0) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(opacity(Color.rgb(18, 20, 24), fraction));
            canvas.drawRect(0, 0, getWidth(), getHeight(), paint);
            if (fraction == 1) drawBackdropBlend(canvas,getWidth(),getHeight());
            else {
                int layer = canvas.saveLayerAlpha(0,0,getWidth(),getHeight(),Math.round(255*fraction));
                drawBackdropBlend(canvas,getWidth(),getHeight());
                canvas.restoreToCount(layer);
            }
        }
        float scale = scale();
        if(queueProgress<1) {
        int foreground=queueProgress>0?canvas.saveLayerAlpha(0,0,getWidth(),getHeight(),Math.round(255*(1-queueProgress))):canvas.save();
        canvas.clipRect(mix(0,playerLeft,columnProgress),0,mix(getWidth(),playerLeft+contentWidth(),columnProgress),getHeight());
        canvas.translate(32*scale*queueProgress,0);
        canvas.save();
        canvas.translate(contentCenterX() + offsetX(), getHeight() / 2f + contentOffsetY());
        canvas.scale(scale, scale);
        paint.setTextAlign(Paint.Align.CENTER);
        animatedCoverRect.set(mix(ambientCoverRect.left, coverRect.left, fraction),
            mix(ambientCoverRect.top, coverRect.top, fraction),
            mix(ambientCoverRect.right, coverRect.right, fraction),
            mix(ambientCoverRect.bottom, coverRect.bottom, fraction));
        if(frozenRow!=null)frozenRow.draw(canvas,animatedCoverRect.top,coverSpacing(),coverDrag,fraction);
        else {
        if(adjacent!=null)adjacent.draw(canvas,animatedCoverRect.top,contentWidth()/(2*scale),mix(.25f,.88f,fraction),0);
        canvas.save();canvas.translate(coverDrag,0);
        coverClip.reset();coverClip.addRoundRect(animatedCoverRect,28,28,Path.Direction.CW);
        canvas.save();canvas.clipPath(coverClip);
        canvas.translate(animatedCoverRect.left,animatedCoverRect.top);
        coverLayer.draw(canvas,null,mix(140/255f,1,fraction));canvas.restore();canvas.restore();
        }
        int titleColor = ColorUtils.blendARGB(Color.rgb(145,155,149), Color.rgb(238,242,239), fraction);
        int artistColor = ColorUtils.blendARGB(Color.rgb(89,103,95), Color.rgb(184,190,187), fraction);
        canvas.save();canvas.translate(-160,mix(0,-83,fraction)-30);canvas.clipRect(0,0,320,35);
        titleLayer.draw(canvas,titleColor,1);canvas.restore();
        canvas.save();canvas.translate(-160,mix(29,-58,fraction)-20);canvas.clipRect(0,0,320,28);
        artistLayer.draw(canvas,artistColor,1);canvas.restore();
        float controls = Math.max(0, (fraction - 0.35f) / 0.65f);
        if (controls > 0) {
            float y = mix(102, 58, fraction);
            paint.setStyle(Paint.Style.FILL); paint.setColor(opacity(Color.WHITE, 0.08f * controls));
            canvas.drawRoundRect(-134, y - 40, 134, y + 40, 40, 40, paint);
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(0.6f);
            paint.setColor(opacity(Color.WHITE, 0.14f * controls));
            canvas.drawRoundRect(-134, y - 40, 134, y + 40, 40, 40, paint);
            paint.setStrokeWidth(1); paint.setColor(opacity(tint(0.85f), controls));
            canvas.drawCircle(0,y,29,paint);
            paint.setStyle(Paint.Style.FILL); paint.setColor(opacity(accent, controls));
            icon(canvas,playing ? pauseIcon : playIcon,0,y,26,accent,controls);
            icon(canvas,previousIcon,-88,y,24,accent,controls);
            icon(canvas,nextIcon,88,y,24,accent,controls);
        }
        if (controls > 0) icon(canvas,arrowIcon,0,mix(130,218,fraction),24,accent,0.85f*controls);
        drawNotifications(canvas, fraction);
        canvas.restore();
        canvas.restoreToCount(foreground);
        }
        if(queueProgress>0) {
            paint.setStyle(Paint.Style.FILL);paint.setColor(Color.argb(Math.round(75*queueProgress),0,0,0));
            canvas.drawRect(0,0,getWidth(),getHeight(),paint);
        }
    }
    private void drawNotifications(Canvas canvas, float fraction) {
        // The row shares the scene's physical pixel shifts and has no timer or animation loop.
        if (activeNotifications.equals(ambientNotifications)) {
            drawNotificationRow(canvas,activeNotifications,mix(170,258,fraction),mix(0.32f,0.78f,fraction));
        } else {
            drawNotificationRow(canvas,ambientNotifications,mix(170,258,fraction),0.32f*(1-fraction));
            drawNotificationRow(canvas,activeNotifications,mix(170,258,fraction),0.78f*fraction);
        }
    }
    private void drawNotificationRow(Canvas canvas, NotificationIcons.Snapshot snapshot, float y, float alpha) {
        int count = snapshot.icons().size();
        if (count == 0 || alpha <= 0) return;
        int extra = snapshot.appCount()-count;
        float width = count*20+(count-1)*14+(extra>0?38:0);
        float start = -width/2+10;
        for (int i=0;i<count;i++) icon(canvas,snapshot.icons().get(i),start+i*34,y,20,accent,alpha);
        if (extra>0) text(canvas,"+"+extra,start+(count-1)*34+38,y+4,12,opacity(accent,alpha),false);
    }
    private void icon(Canvas canvas, Drawable icon, float x, float y, int size, int color, float alpha) {
        icon.setBounds(Math.round(x-size/2f),Math.round(y-size/2f),Math.round(x+size/2f),Math.round(y+size/2f));
        icon.setTint(color); icon.setAlpha(Math.round(255*alpha)); icon.draw(canvas);
    }
    private android.app.PendingIntent notificationHit(float physicalX,float physicalY) {
        if(!active || activeProgress!=1 || queueProgress!=0)return null;
        float s=scale();if(s<=0)return null;
        float x=(physicalX-contentCenterX()-offsetX())/s,y=(physicalY-getHeight()/2f-contentOffsetY())/s;
        if(Math.abs(y-258)>20)return null;
        int count=activeNotifications.icons().size(),extra=activeNotifications.appCount()-count;
        float width=count*20+(count-1)*14+(extra>0?38:0),start=-width/2+10;
        for(int i=0;i<count;i++)if(Math.abs(x-(start+i*34))<=16)return activeNotifications.actions().get(i);
        return null;
    }
    private void handleSingleTap(MotionEvent event) {
        if (!active || columnsMoving()) return;
        actions.onInteract();
        android.app.PendingIntent notification=notificationTouches.get(event.getDownTime());
        if(activeProgress==1 && queueProgress==0 && notification!=null && activeNotifications.actions().contains(notification)) {
            actions.onOpenNotification(notification);return;
        }
        float scale=scale();
        float x=(event.getX()-contentCenterX()-offsetX())/scale;
        float y=(event.getY()-getHeight()/2f-contentOffsetY())/scale;
        if(activeProgress==1 && queueProgress==0 && coverCommitDirection==0 && !adjacentPending() && coverReturn==null
                && coverRect.contains(x,y)) { actions.onOpenMusic();return; }
        if(activeProgress==1 && sideCoverHit(x,y)) {
            if(adjacent!=null && adjacent.artworkReady(x<0))navigateCover(x<0,coverTouches.get(event.getDownTime()),true);return;
        }
        if (Math.abs(x)<70 && y>190 && y<248) { actions.onUnlock(); return; }
        MediaController media=media();
        if (media == null || Math.abs(y-mix(102,58,activeProgress))>=35) return;
        PlaybackState state=media.getPlaybackState();
        long offered=state==null?0:state.getActions();
        if (Math.abs(x)<35) {
            if (state!=null && state.getState()==PlaybackState.STATE_PLAYING) {
                if ((offered & PlaybackState.ACTION_PAUSE)!=0) media.getTransportControls().pause();
            } else if ((offered & PlaybackState.ACTION_PLAY)!=0) media.getTransportControls().play();
        } else if (coverCommitDirection==0 && coverTransportPending==null && coverReturn==null && Math.abs(x-88)<35 && (offered & PlaybackState.ACTION_SKIP_TO_NEXT)!=0) {
            transportDirection=-1;transportDirectionUntil=SystemClock.uptimeMillis()+5000;media.getTransportControls().skipToNext();
        } else if (coverCommitDirection==0 && coverTransportPending==null && coverReturn==null && Math.abs(x+88)<35 && (offered & PlaybackState.ACTION_SKIP_TO_PREVIOUS)!=0) {
            transportDirection=1;transportDirectionUntil=SystemClock.uptimeMillis()+5000;media.getTransportControls().skipToPrevious();
        }
    }
    @Override public boolean onTouchEvent(MotionEvent event) {
        int action=event.getActionMasked();
        if(action==MotionEvent.ACTION_DOWN) {
            if(notificationTouches.size()>=3)notificationTouches.removeAt(0);
            android.app.PendingIntent notification=notificationHit(event.getX(),event.getY());
            if(notification!=null)notificationTouches.put(event.getDownTime(),notification);
            if(coverTouches.size()>=3)coverTouches.removeAt(0);
            if(active && adjacent!=null && coverBandHit(event.getX(),event.getY()))coverTouches.put(event.getDownTime(),adjacent.anchor());
            coverTransportTouch=MusicFeedback.target(media(),displayedMetadata);swipeStartedActive=active;
            coverDown=active && !columnsMoving() && activeProgress==1 && queueProgress==0 && coverCommitDirection==0 && coverTransportPending==null && !adjacentPending() && coverBandHit(event.getX(),event.getY());
            coverDownX=event.getX();coverDownY=event.getY();coverSwiping=false;coverGesture.reset();removeCallbacks(hapticEcho);
            if(coverDown && coverReturn!=null){coverReturn.cancel();coverReturn=null;clearFrozenRow();}
        }
        if(coverDown && action==MotionEvent.ACTION_MOVE) {
            float dx=event.getX()-coverDownX,dy=event.getY()-coverDownY;
            int slop=android.view.ViewConfiguration.get(getContext()).getScaledTouchSlop();
            if(!coverSwiping && Math.abs(dx)>slop && Math.abs(dx)>Math.abs(dy)*1.4f) {
                coverSwiping=true;captureFrozenRow();
                MotionEvent cancel=MotionEvent.obtain(event);cancel.setAction(MotionEvent.ACTION_CANCEL);gestures.onTouchEvent(cancel);cancel.recycle();
            }
            if(coverSwiping) {
                actions.onInteract();int before=coverGesture.armed();
                CoverDrag.State state=coverGesture.update(dx/scale(),coverSpacing(),canNavigateCover(true),canNavigateCover(false));coverDrag=state.displacement();
                if(state.detent()) {coverDetents++;performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM);removeCallbacks(hapticEcho);postDelayed(hapticEcho,38);}
                else if(before!=state.armed() && state.armed()==0){removeCallbacks(hapticEcho);performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);}
                if(before!=state.armed())actions.onVisualSettled();invalidate();return true;
            }
        }
        if(coverSwiping && (action==MotionEvent.ACTION_UP || action==MotionEvent.ACTION_CANCEL)) {
            int direction=coverGesture.armed();boolean sent=false;
            if(action==MotionEvent.ACTION_UP && direction!=0) {
                boolean previous=direction>0;
                sent=adjacent.available(previous)?navigateCover(previous,coverTouches.get(event.getDownTime()),false):navigateTransportCover(previous,coverTransportTouch,false);
            }
            coverDown=false;coverSwiping=false;coverGesture.reset();removeCallbacks(hapticEcho);
            if(!sent)animateCoverTo(0,this::clearFrozenRow);return true;
        }
        if(action==MotionEvent.ACTION_POINTER_DOWN)swipeStartedActive=false;
        if(action==MotionEvent.ACTION_UP && swipeStartedActive && requestSwipeUnlock(event.getX()-coverDownX,event.getY()-coverDownY)) {
            coverDown=false;MotionEvent cancel=MotionEvent.obtain(event);cancel.setAction(MotionEvent.ACTION_CANCEL);gestures.onTouchEvent(cancel);cancel.recycle();return true;
        }
        if(action==MotionEvent.ACTION_UP || action==MotionEvent.ACTION_CANCEL)coverDown=false;
        boolean handled=gestures.onTouchEvent(event);
        if(active && action==MotionEvent.ACTION_UP)performClick();return handled;
    }
    @Override public boolean performClick() { super.performClick(); actions.onActivate(); return true; }
    @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.addAction(new AccessibilityNodeInfo.AccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK,getContext().getString(R.string.show_controls)));
        if(active) {
            info.addAction(new AccessibilityNodeInfo.AccessibilityAction(R.id.action_open_music,getContext().getString(R.string.open_music_app,MusicSources.name(getContext(),media()==null?null:media().getPackageName()))));
            info.addAction(new AccessibilityNodeInfo.AccessibilityAction(R.id.action_queue,getContext().getString(R.string.show_queue)));
            if(accessoryConnected())info.addAction(new AccessibilityNodeInfo.AccessibilityAction(R.id.action_accessory,getContext().getString(R.string.show_accessory_batteries)));
            if(canNavigateCover(true))info.addAction(new AccessibilityNodeInfo.AccessibilityAction(R.id.action_previous_cover,getContext().getString(R.string.previous_track_description,adjacent.label(true))));
            if(canNavigateCover(false))info.addAction(new AccessibilityNodeInfo.AccessibilityAction(R.id.action_next_cover,getContext().getString(R.string.next_track_description,adjacent.label(false))));
        }
        info.addAction(new AccessibilityNodeInfo.AccessibilityAction(R.id.action_unlock,preview ? getContext().getString(R.string.preview_close) : getContext().getString(R.string.unlock)));
    }
    @Override public boolean performAccessibilityAction(int action, Bundle arguments) {
        if(action==R.id.action_open_music && active && queueProgress==0 && coverCommitDirection==0 && !adjacentPending()){actions.onOpenMusic();return true;}
        if(action==R.id.action_previous_cover)return navigateCover(true);
        if(action==R.id.action_next_cover)return navigateCover(false);
        if(action==R.id.action_accessory && active && accessoryConnected()){actions.onAccessory();return true;}
        if (action == R.id.action_queue && active) { actions.onQueue();return true; }
        if (action == R.id.action_unlock) { actions.onUnlock(); return true; }
        if (action == AccessibilityNodeInfo.ACTION_CLICK) return performClick();
        return super.performAccessibilityAction(action,arguments);
    }
}
