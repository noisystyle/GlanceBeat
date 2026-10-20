package it.noxia.ambientplayer;

import android.content.Context;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.view.animation.PathInterpolator;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.HapticFeedbackConstants;
import android.view.ViewConfiguration;
import android.view.accessibility.AccessibilityNodeInfo;
import android.os.Bundle;
import android.os.Build;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import java.util.Locale;

/** Animated native widgets above the cached ambient canvas; the wave redraws only its small strip. */
@android.annotation.SuppressLint({"ViewConstructor","ClickableViewAccessibility"}) // Programmatic scene; seek accessibility lives on its SeekBar node.
final class PlayerSurface extends FrameLayout {
    int queueArtworkCached(){return queue==null?0:queue.artworkCached();}
    int queueArtworkPending(){return queue==null?0:queue.artworkPending();}
    boolean queueEdgeFrost(){return queue!=null && queue.edgeFrost();}
    boolean queueHeaderFrost(){return queue!=null && queue.headerFrost();}
    int queueThumbnailLoading(){return queue==null?0:queue.thumbnailLoading();}
    private final PlayerView scene;
    private View statusOverlay;
    private QueuePanel queue;
    private AccessoryPanel accessory;
    private boolean accessoryOpen,pageAccessory;
    private final Runnable report;
    private boolean queueOpen,queueSwipe,pageTouchStarted;
    private float queueFraction,queueDownX,queueDownY;
    private ValueAnimator queueTransition;
    private final LinearProgressIndicator progress;
    private final MaterialButton like, dislike, shuffle;
    private final TextView elapsed, duration, seekLabel;
    private final FrameLayout seekTouch;
    private final SeekGesture seek = new SeekGesture();
    private MediaController seekMedia;
    private String seekTrackKey, pendingTrackKey;
    private long pendingSeek=-1,pendingSeekAt;
    private float downX,downY;
    private int lastSeekSegment;
    private long lastSeekTick;
    private final Runnable hold = this::activateSeeking;
    private final View[] animatedWidgets;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable interact;
    private final int accent;
    private final boolean preview;
    private boolean pocketed;
    private float fraction;
    private int viewportWidth, viewportHeight;
    private int playerLeft;
    private PlayerColumns columns;
    private boolean splitQueueObserving;
    boolean splitColumns(){return columns!=null && columns.split();}
    private long durationMs;
    private ValueAnimator trackTransition;
    private final TrackChangeTracker trackChanges = new TrackChangeTracker();
    private boolean waitingForPosition, changePending;
    private long waitingStamp;
    private final Runnable startTrackChange = () -> {
        changePending=false;
        if (fraction>0 && !pocketed && getWindowVisibility()==VISIBLE) animateTrackChange();
    };
    private boolean renderedTime, resumeTrackTransition;
    private long renderedElapsed, renderedDuration, targetElapsed, targetDuration;
    private int targetProgress;
    private boolean likeAvailable, dislikeAvailable, shuffleAvailable;
    private boolean shuffleReady;
    private MusicFeedback.Target shuffleTarget,shuffleTouch;
    private Boolean shuffleTouchState;
    private long shuffleTouchTime;
    private boolean heartStyle,feedbackLayoutDirty;
    private MusicFeedback.Target feedbackTarget,likeTouch,dislikeTouch;
    private long likeTouchTime,dislikeTouchTime;
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (fraction <= 0 || auxiliaryOpen() || pocketed || getWindowVisibility() != VISIBLE) return;
            updateProgress();
            handler.postDelayed(this, 1_000);
        }
    };

    PlayerSurface(Context context, PlayerView scene, Runnable interact, boolean preview,Runnable report) {
        super(context);
        this.scene = scene;
        this.interact = interact;
        this.preview = preview;
        this.report=report;
        int primary = MaterialColors.getColor(context, androidx.appcompat.R.attr.colorPrimary, Color.LTGRAY);
        accent = MaterialColors.getColorRoles(primary, false).getAccent();
        addView(scene, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        LayoutInflater.from(context).inflate(R.layout.player_widgets, this, true);
        progress = findViewById(R.id.track_progress);
        seekTouch = findViewById(R.id.seek_touch);
        seekLabel = new TextView(context);
        seekLabel.setGravity(android.view.Gravity.CENTER);
        seekLabel.setTextColor(accent);
        seekLabel.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        GradientDrawable bubble = new GradientDrawable();
        bubble.setColor(Color.rgb(30,33,40));
        bubble.setCornerRadius(40);
        bubble.setStroke(1,Color.argb(45,255,255,255));
        seekLabel.setBackground(bubble);
        seekLabel.setVisibility(INVISIBLE);
        addView(seekLabel);
        seekTouch.setOnTouchListener(this::handleSeekTouch);
        elapsed = findViewById(R.id.track_elapsed);
        duration = findViewById(R.id.track_duration);
        like = findViewById(R.id.like_track);
        dislike = findViewById(R.id.dislike_track);
        shuffle = findViewById(R.id.shuffle_queue);
        animatedWidgets = new View[]{seekTouch, elapsed, duration, like, dislike, shuffle};
        elapsed.setTextColor(Color.rgb(190,195,199));
        duration.setTextColor(Color.rgb(190,195,199));
        duration.setGravity(android.view.Gravity.END);
        progress.setIndicatorColor(accent);
        progress.setTrackColor(Color.argb(40,255,255,255));
        progress.setAccessibilityDelegate(new AccessibilityDelegate() {
            @Override public void onInitializeAccessibilityNodeInfo(View view,AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(view,info);
                if (canSeek() && !scene.columnsMoving()) {
                    info.setClassName("android.widget.SeekBar");
                    info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS);
                    info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD);
                    info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD);
                }
            }
            @Override public boolean performAccessibilityAction(View view,int action,Bundle arguments) {
                if (canSeek() && !scene.columnsMoving()) {
                    float value=progress.getProgress();
                    if (action==AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.getId() && arguments!=null)
                        value=arguments.getFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE,value);
                    else if(action==AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) value+=500;
                    else if(action==AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) value-=500;
                    else return super.performAccessibilityAction(view,action,arguments);
                    interact.run();return commitSeek(media(),Math.round(Math.max(0,Math.min(10000,value))*durationMs/10000f),trackKey(media().getMetadata()));
                }
                return super.performAccessibilityAction(view,action,arguments);
            }
        });
        like.setCheckable(false); dislike.setCheckable(false);shuffle.setCheckable(false);
        shuffle.setOnTouchListener(this::captureShuffleTouch);
        shuffle.setOnClickListener(view -> sendShuffle());
        like.setOnTouchListener((view,event)->captureFeedbackTouch(view,event,true));
        dislike.setOnTouchListener((view,event)->captureFeedbackTouch(view,event,false));
        like.setOnClickListener(view -> sendFeedback(true));
        dislike.setOnClickListener(view -> sendFeedback(false));
        queue=new QueuePanel(context,accent,interact,()->closeQueue(true),report);
        addView(queue,new LayoutParams(-1,-1));
        accessory=AccessorySupport.createPanel(context,accent,interact,()->closeAccessory(true));
        if(accessory!=null)addView(accessory,new LayoutParams(-1,-1));
        statusOverlay=new View(context) {
            @Override protected void onDraw(android.graphics.Canvas canvas){scene.drawStatusOverlay(canvas);}
        };
        statusOverlay.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        addView(statusOverlay,new LayoutParams(-1,-1));
        refreshSession();
    }
    boolean queueOpen() { return queueOpen; }
    boolean accessoryOpen(){return accessoryOpen;}
    boolean auxiliaryOpen(){return queueOpen || accessoryOpen;}
    boolean queuePending() { return queue!=null && queue.pending() || scene.adjacentPending(); }
    int queueItems() { return queue==null?0:queue.itemCount(); }
    boolean queueAnimating() { return queueTransition!=null; }
    void openQueue() {
        if(splitColumns())return;
        if(!scene.isActiveTarget() || fraction!=1 || pocketed || queue==null || auxiliaryOpen() || queueFraction>0 || preview)return;
        interact.run();pageAccessory=false;queueOpen=true;cancelSeeking();cancelTrackTransition();handler.removeCallbacks(tick);progress.setWaveSpeed(0);
        scene.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        queue.setVisibility(VISIBLE);queue.opened();animateQueue(1,true);
        android.util.Log.i("AmbientQueue","page_opened items="+queueItems());report.run();
    }
    void closeQueue(boolean animate) {
        if(splitColumns() && !queueOpen && queueFraction==0)return;
        if(queue==null || !queueOpen && queueFraction==0)return;
        queueOpen=false;queue.closed();animateQueue(0,animate);
        android.util.Log.i("AmbientQueue","page_closed");report.run();
    }
    void openAccessory(){
        if(accessory==null || scene.columnsMoving() || !scene.isActiveTarget() || fraction!=1 || pocketed || auxiliaryOpen() || queueFraction>0 || !scene.accessoryConnected())return;
        interact.run();pageAccessory=true;accessoryOpen=true;cancelSeeking();cancelTrackTransition();handler.removeCallbacks(tick);progress.setWaveSpeed(0);
        scene.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        accessory.refresh(scene.accessoryReading());accessory.setVisibility(VISIBLE);animateQueue(1,true);report.run();
    }
    void closeAccessory(boolean animate){
        if(!accessoryOpen && (!pageAccessory || queueFraction==0))return;
        accessoryOpen=false;animateQueue(0,animate);report.run();
    }
    void closeAuxiliary(boolean animate){if(pageAccessory)closeAccessory(animate);else closeQueue(animate);}
    private void animateQueue(float target,boolean animate) {
        if(queueTransition!=null)queueTransition.cancel();queueTransition=null;
        if(!animate || !ValueAnimator.areAnimatorsEnabled() || !isShown()) { setQueueFraction(target);settleQueue(target);return; }
        queueTransition=ValueAnimator.ofFloat(queueFraction,target);queueTransition.setDuration(Math.max(1,Math.round(420*Math.abs(target-queueFraction))));
        queueTransition.setInterpolator(new PathInterpolator(.2f,0,0,1));
        queueTransition.addUpdateListener(a->setQueueFraction((float)a.getAnimatedValue()));
        queueTransition.addListener(new AnimatorListenerAdapter(){
            boolean cancelled;
            @Override public void onAnimationCancel(Animator a){cancelled=true;}
            @Override public void onAnimationEnd(Animator a){if(queueTransition==a)queueTransition=null;if(!cancelled)settleQueue(target);}
        });queueTransition.start();
    }
    private void setQueueFraction(float value) {
        queueFraction=value;scene.setQueueProgress(value);
        View page=pageAccessory?accessory:queue;page.setAlpha(value);page.setTranslationX((value-1)*(pageAccessory?-32:32)*scale());updateVisuals();
    }
    private void settleQueue(float target) {
        if(target==1 && pageAccessory)accessory.shown();
        if(target==0) {
            if(!splitColumns())queue.setVisibility(INVISIBLE);if(accessory!=null)accessory.setVisibility(INVISIBLE);scene.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
            refreshSession();if(fraction>0 && !pocketed && isShown() && getWindowVisibility()==VISIBLE){handler.removeCallbacks(tick);handler.post(tick);}
        }
        report.run();
    }
    @Override public boolean onInterceptTouchEvent(MotionEvent event) {
        if(event.getActionMasked()==MotionEvent.ACTION_DOWN){queueDownX=event.getX();queueDownY=event.getY();queueSwipe=false;pageTouchStarted=auxiliaryOpen() || queueFraction>0;}
        if(event.getActionMasked()==MotionEvent.ACTION_MOVE && !pageTouchStarted && !scene.columnsMoving() && !auxiliaryOpen() && queueFraction==0 && scene.isActiveTarget() && fraction==1 && !pocketed && !seek.isDown() && (!splitColumns() || queueDownX>=playerLeft && queueDownX<playerLeft+viewportWidth) && !scene.coverBandHit(queueDownX,queueDownY)) {
            float dx=event.getX()-queueDownX,dy=event.getY()-queueDownY;
            if(!splitColumns() && dx>64*scale() && dx>Math.abs(dy)*1.4f){queueSwipe=true;openQueue();return true;}
            if(dx < -64*scale() && -dx>Math.abs(dy)*1.4f && scene.accessoryConnected()){queueSwipe=true;openAccessory();return true;}
        }
        return queueSwipe;
    }
    @Override public boolean onTouchEvent(MotionEvent event) { return queueSwipe || super.onTouchEvent(event); }

    private MediaController media() { return preview ? null : MusicSessionService.current; }
    private boolean captureFeedbackTouch(View view,MotionEvent event,boolean positive){
        if(event.getActionMasked()==MotionEvent.ACTION_DOWN){
            if(positive){likeTouch=feedbackTarget;likeTouchTime=event.getDownTime();}
            else{dislikeTouch=feedbackTarget;dislikeTouchTime=event.getDownTime();}
        }else if(event.getActionMasked()==MotionEvent.ACTION_CANCEL){
            if(positive){likeTouch=null;likeTouchTime=0;}else{dislikeTouch=null;dislikeTouchTime=0;}
        }else if(event.getActionMasked()==MotionEvent.ACTION_UP){
            long stamp=event.getDownTime();
            // View posts its click after this listener returns. Clear only after that click,
            // including when a metadata refresh canceled the press without producing a click.
            view.post(()->view.post(()->{
                if(positive && likeTouchTime==stamp){likeTouch=null;likeTouchTime=0;}
                if(!positive && dislikeTouchTime==stamp){dislikeTouch=null;dislikeTouchTime=0;}
            }));
        }
        return false;
    }
    private boolean captureShuffleTouch(View view,MotionEvent event){
        if(event.getActionMasked()==MotionEvent.ACTION_DOWN){
            shuffleTouch=shuffleTarget;shuffleTouchTime=event.getDownTime();
            MediaController current=media();
            shuffleTouchState=MusicShuffle.selected(current,current==null?null:current.getPlaybackState(),current==null?null:current.getMetadata());
        }else if(event.getActionMasked()==MotionEvent.ACTION_CANCEL){
            shuffleTouch=null;shuffleTouchState=null;shuffleTouchTime=0;
        }else if(event.getActionMasked()==MotionEvent.ACTION_UP){
            long stamp=event.getDownTime();view.post(()->view.post(()->{
                if(shuffleTouchTime==stamp){shuffleTouch=null;shuffleTouchState=null;shuffleTouchTime=0;}
            }));
        }
        return false;
    }
    private void sendShuffle(){
        MediaController current=media();MediaMetadata metadata=current==null?null:current.getMetadata();
        PlaybackState state=current==null?null:current.getPlaybackState();
        MusicFeedback.Target touched=shuffleTouchTime==0?shuffleTarget:shuffleTouch;
        Boolean selected=MusicShuffle.selected(current,state,metadata);
        boolean changed=shuffleTouchTime!=0 && !java.util.Objects.equals(shuffleTouchState,selected);
        shuffleTouch=null;shuffleTouchTime=0;shuffleTouchState=null;
        if(changed || touched==null || !touched.equals(MusicFeedback.target(current,metadata)) || !scene.feedbackReady(current,metadata) || !MusicShuffle.available(current,state) || selected==null){refreshSession();return;}
        interact.run();
        if(MusicShuffle.send(current,metadata))shuffle.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK);
        else Toast.makeText(getContext(),R.string.music_action_unavailable,Toast.LENGTH_SHORT).show();
        refreshSession();
    }
    private void sendFeedback(boolean positive) {
        MediaController current=media();MediaMetadata metadata=current==null?null:current.getMetadata();
        long touchedAt=positive?likeTouchTime:dislikeTouchTime;
        MusicFeedback.Target touched=touchedAt==0?feedbackTarget:positive?likeTouch:dislikeTouch;
        likeTouch=dislikeTouch=null;likeTouchTime=dislikeTouchTime=0;
        if(touched==null || !touched.equals(MusicFeedback.target(current,metadata)) || !scene.feedbackReady(current,metadata)){refreshFeedback();return;}
        interact.run();
        if(MusicFeedback.send(current,positive,metadata))(positive?like:dislike).performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK);
        else Toast.makeText(getContext(),R.string.music_action_unavailable,Toast.LENGTH_SHORT).show();
        refreshSession();
    }
    void refreshFeedback(){
        MediaController current=media();MediaMetadata metadata=current==null?null:current.getMetadata();
        PlaybackState state=current==null?null:current.getPlaybackState();
        boolean nextShuffle=MusicShuffle.available(current,state);
        if(nextShuffle!=shuffleAvailable){shuffleAvailable=nextShuffle;feedbackLayoutDirty=true;requestLayout();}
        Boolean shuffled=MusicShuffle.selected(current,state,metadata);
        shuffleReady=shuffleAvailable && shuffled!=null && scene.feedbackReady(current,metadata);
        shuffleTarget=shuffleReady?MusicFeedback.target(current,metadata):null;
        boolean shuffleSelected=shuffleReady && Boolean.TRUE.equals(shuffled);
        shuffle.setSelected(shuffleSelected);
        styleFeedback(shuffle,shuffleSelected);
        int shuffleLabel=shuffleSelected?R.string.disable_shuffle:R.string.enable_shuffle;
        shuffle.setContentDescription(getContext().getString(shuffleLabel));
        shuffle.setTooltipText(getContext().getString(shuffleLabel));
        shuffle.setEnabled(shuffleReady);
        boolean nextStyle=MusicFeedback.hearts(current);
        if(nextStyle!=heartStyle){heartStyle=nextStyle;feedbackLayoutDirty=true;requestLayout();}
        boolean ready=scene.feedbackReady(current,metadata) && MusicFeedback.ready(current,state,metadata);
        feedbackTarget=ready?MusicFeedback.target(current,metadata):null;
        boolean nextLike=MusicFeedback.available(current,true),nextDislike=MusicFeedback.available(current,false);
        if(nextLike!=likeAvailable || nextDislike!=dislikeAvailable){feedbackLayoutDirty=true;requestLayout();}
        likeAvailable=nextLike;dislikeAvailable=nextDislike;
        like.setEnabled(ready);dislike.setEnabled(ready);
        updateFeedback(like,true,ready && MusicFeedback.selected(current,state,metadata,true));
        updateFeedback(dislike,false,ready && MusicFeedback.selected(current,state,metadata,false));
    }
    void refreshSession() {
        MediaController media = media();
        MediaMetadata metadata = media == null ? null : media.getMetadata();
        durationMs = preview ? 240_000 : metadata == null ? 0 : Math.max(0, metadata.getLong(MediaMetadata.METADATA_KEY_DURATION));
        if (seek.isDown() && !sameSeekTarget(media,seekTrackKey)) cancelSeeking();
        if (pendingSeek>=0 && !trackKey(metadata).equals(pendingTrackKey)) pendingSeek=-1;
        progress.setFocusable(canSeek());
        refreshFeedback();
        updateProgress();
        updateVisuals();
        if(queueOpen || splitQueueObserving)queue.refresh();
    }
    private void updateFeedback(MaterialButton button, boolean positive, boolean selected) {
        button.setSelected(selected);
        if(positive)button.setIconResource(heartStyle?selected?R.drawable.ic_heart_fill:R.drawable.ic_heart:R.drawable.ic_thumb_up);
        styleFeedback(button,selected);
        int label = positive ? heartStyle?selected?R.string.remove_favorite:R.string.add_favorite:selected ? R.string.remove_like : R.string.like
            : selected ? R.string.remove_dislike : R.string.dislike;
        button.setContentDescription(getContext().getString(label));
        button.setTooltipText(getContext().getString(label));
    }
    private void styleFeedback(MaterialButton button,boolean selected){
        button.setBackgroundTintList(ColorStateList.valueOf(selected ? accent : Color.argb(24,255,255,255)));
        button.setIconTint(ColorStateList.valueOf(selected ? Color.rgb(22,26,30) : accent));
        button.setStrokeColor(ColorStateList.valueOf(Color.argb(35,255,255,255)));
    }
    private void updateProgress() {
        if (durationMs <= 0) {
            // Metadata can be absent between tracks. Keep the mounted bar and its last value.
            resumeTrackTransition |= trackTransition != null || changePending;
            cancelTrackTransition();
            progress.setWaveSpeed(0);
            if (!renderedTime) { elapsed.setText("—:—");duration.setText("—:—"); }
            return;
        }
        MediaController media = media();
        PlaybackState state = media == null ? null : MusicSessionService.playbackState();
        boolean playing = state != null && state.getState() == PlaybackState.STATE_PLAYING;
        long position = preview ? 64_000 : state == null ? 0 : Math.max(0, state.getPosition());
        if (playing && state.getLastPositionUpdateTime() > 0)
            position += (long) (Math.max(0, SystemClock.elapsedRealtime() - state.getLastPositionUpdateTime()) * state.getPlaybackSpeed());
        if (pendingSeek>=0) {
            long age=SystemClock.elapsedRealtime()-pendingSeekAt;
            if (age>1500 || state!=null && state.getLastPositionUpdateTime()>=pendingSeekAt
                    && Math.abs(position-pendingSeek)<2000) pendingSeek=-1;
            else position=pendingSeek+(playing?age:0);
        }
        if (seek.isActive()) position=seek.position();
        if (durationMs > 0) position = Math.min(durationMs, Math.max(0, position));
        int value = durationMs > 0 ? (int) Math.round(position * 10_000.0 / durationMs) : 0;
        progress.setWaveSpeed(playing && queueFraction==0 && !seek.isActive() ? Math.round(8 * scale()) : 0);
        MediaMetadata metadata=media==null?null:media.getMetadata();
        long stamp=state==null?0:state.getLastPositionUpdateTime();
        boolean manual=seek.isActive() || pendingSeek>=0;
        TrackChangeTracker.Change change=trackChanges.observe(preview?"preview":visualIdentity(metadata),
            durationMs,position,stamp,manual);
        boolean freshPosition=waitingForPosition && stamp!=waitingStamp;
        if (change.stalePosition()) { waitingForPosition=true;waitingStamp=stamp; }
        if (manual || stamp!=waitingStamp) waitingForPosition=false;
        if (waitingForPosition) { position=0;value=0; }
        targetElapsed=position;targetDuration=durationMs;targetProgress=value;
        if (manual) {
            cancelTrackTransition();resumeTrackTransition=false;
            renderProgress(position,durationMs,value,false);
        } else if ((change.any() || freshPosition || resumeTrackTransition) && renderedTime && fraction>0 && queueFraction==0 && !pocketed
                && getWindowVisibility()==VISIBLE && ValueAnimator.areAnimatorsEnabled()) {
            resumeTrackTransition=false;
            // Hold the visible frame while metadata and playback callbacks settle together.
            cancelTrackTransition();changePending=true;
            handler.postDelayed(startTrackChange,180);
        } else if (trackTransition==null && !changePending) {
            resumeTrackTransition=false;
            renderProgress(position,durationMs,value,fraction>0 && progress.getProgress()!=value);
        }
    }
    private static String visualIdentity(MediaMetadata metadata) {
        if (metadata==null) return null;
        String id=metadata.getString(MediaMetadata.METADATA_KEY_MEDIA_ID);
        return id!=null && !id.isEmpty()?id:metadata.getString(MediaMetadata.METADATA_KEY_TITLE)+"|"
            +metadata.getString(MediaMetadata.METADATA_KEY_ARTIST);
    }

    private void renderProgress(long position,long length,int value,boolean animateBar) {
        renderedElapsed=position;renderedDuration=length;renderedTime=true;
        progress.setProgressCompat(value,animateBar);
        String elapsedText=time(position),durationText=time(length);
        if (!elapsedText.contentEquals(elapsed.getText())) elapsed.setText(elapsedText);
        if (!durationText.contentEquals(duration.getText())) duration.setText(durationText);
    }
    private void animateTrackChange() {
        cancelTrackTransition();
        long fromElapsed=renderedElapsed,fromDuration=renderedDuration;
        int fromProgress=progress.getProgress();
        // Each run owns immutable endpoints. Later updates retarget from the rendered frame.
        final long toElapsed=targetElapsed,toDuration=targetDuration;
        final int toProgress=targetProgress;
        trackTransition=ValueAnimator.ofFloat(0,1);
        trackTransition.setDuration(1200);
        trackTransition.setInterpolator(new PathInterpolator(0.4f,0f,0.2f,1f));
        android.util.Log.i("AmbientTrack","started elapsed="+fromElapsed+" duration="+fromDuration
            +" targetElapsed="+toElapsed+" targetDuration="+toDuration);
        trackTransition.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            private boolean midpointLogged;
            @Override public void onAnimationUpdate(ValueAnimator animation) {
                float t=(float)animation.getAnimatedValue();
                renderProgress(Math.round(fromElapsed+(toElapsed-fromElapsed)*t),
                    Math.round(fromDuration+(toDuration-fromDuration)*t),
                    Math.round(fromProgress+(toProgress-fromProgress)*t),false);
                if (!midpointLogged && t>=.45f) {
                    midpointLogged=true;
                    android.util.Log.i("AmbientTrack","midpoint elapsed="+renderedElapsed+" duration="+renderedDuration+" progress="+progress.getProgress());
                }
            }
        });
        trackTransition.addListener(new AnimatorListenerAdapter() {
            private boolean cancelled;
            @Override public void onAnimationCancel(Animator animation) { cancelled=true; }
            @Override public void onAnimationEnd(Animator animation) {
                if (trackTransition==animation) trackTransition=null;
                if (!cancelled) {
                    renderProgress(toElapsed,toDuration,toProgress,false);
                    android.util.Log.i("AmbientTrack","finished elapsed="+renderedElapsed+" duration="+renderedDuration);
                }
            }
        });
        trackTransition.start();
    }
    private void cancelTrackTransition() {
        handler.removeCallbacks(startTrackChange);changePending=false;
        if (trackTransition!=null) trackTransition.cancel();
        trackTransition=null;
    }
    boolean trackTransitionRunning() { return changePending || trackTransition!=null && trackTransition.isRunning(); }
    long displayedElapsedMs() { return renderedElapsed; }
    long displayedDurationMs() { return renderedDuration; }
    private boolean canSeek() {
        MediaController media=media();
        PlaybackState state=media==null?null:media.getPlaybackState();
        return durationMs>0 && state!=null && (state.getActions()&PlaybackState.ACTION_SEEK_TO)!=0;
    }
    private static String trackKey(MediaMetadata metadata) {
        if (metadata==null) return "";
        return String.valueOf(metadata.getString(MediaMetadata.METADATA_KEY_MEDIA_ID))+"|"
            +metadata.getString(MediaMetadata.METADATA_KEY_TITLE)+"|"
            +metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)+"|"+metadata.getLong(MediaMetadata.METADATA_KEY_DURATION);
    }
    private boolean sameSeekTarget(MediaController media,String key) {
        return media!=null && seekMedia!=null && media.getSessionToken().equals(seekMedia.getSessionToken())
            && trackKey(media.getMetadata()).equals(key) && canSeek();
    }
    @android.annotation.SuppressLint("ClickableViewAccessibility") // Seek actions also exposed on the progress accessibility node.
    private boolean handleSeekTouch(View view,MotionEvent event) {
        if (event.getActionMasked()==MotionEvent.ACTION_DOWN) {
            if (fraction!=1 || scene.columnsMoving() || !canSeek()) return false;
            interact.run();seekMedia=media();seekTrackKey=trackKey(seekMedia.getMetadata());
            downX=event.getX();downY=event.getY();
            if (!seek.begin(downX,view.getWidth(),durationMs)) return false;
            view.getParent().requestDisallowInterceptTouchEvent(true);
            handler.postDelayed(hold,250);return true;
        }
        if (event.getActionMasked()==MotionEvent.ACTION_MOVE) {
            if (!seek.isActive()) {
                int slop=ViewConfiguration.get(getContext()).getScaledTouchSlop();
                if (Math.hypot(event.getX()-downX,event.getY()-downY)>slop) cancelSeeking();
            } else {
                seek.move(event.getX(),view.getWidth());showSeekPreview();
                int segment=(int)(seek.position()*20/Math.max(1,durationMs));
                long now=SystemClock.uptimeMillis();
                if (segment!=lastSeekSegment && now-lastSeekTick>=60) {
                    view.performHapticFeedback(Build.VERSION.SDK_INT>=34?HapticFeedbackConstants.SEGMENT_FREQUENT_TICK:HapticFeedbackConstants.CLOCK_TICK);
                    lastSeekSegment=segment;lastSeekTick=now;
                }
                interact.run();
            }
            return true;
        }
        if (event.getActionMasked()==MotionEvent.ACTION_UP) {
            handler.removeCallbacks(hold);long position=seek.release();
            if (position>=0 && commitSeek(seekMedia,position,seekTrackKey))
                view.performHapticFeedback(HapticFeedbackConstants.GESTURE_END);
            seekLabel.setVisibility(INVISIBLE);seekMedia=null;updateProgress();return true;
        }
        if (event.getActionMasked()==MotionEvent.ACTION_POINTER_DOWN || event.getActionMasked()==MotionEvent.ACTION_CANCEL) { cancelSeeking();return true; }
        return seek.isDown();
    }
    private void activateSeeking() {
        if (fraction!=1 || !sameSeekTarget(media(),seekTrackKey)) { cancelSeeking();return; }
        if (!seek.activate()) return;
        pendingSeek=-1;lastSeekSegment=(int)(seek.position()*20/Math.max(1,durationMs));
        boolean haptic=seekTouch.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        android.util.Log.i("AmbientSeek","hold_activated haptic="+haptic);
        showSeekPreview();interact.run();
    }
    private void showSeekPreview() {
        updateProgress();seekLabel.setText(time(seek.position()));
        float center=(float)seek.position()/Math.max(1,durationMs)*seekTouch.getWidth();
        float half=seekLabel.getWidth()/2f;
        float x=seekTouch.getLeft()+Math.max(half,Math.min(seekTouch.getWidth()-half,center));
        seekLabel.setTranslationX(x-playerLeft-viewportWidth/2f);seekLabel.setVisibility(VISIBLE);
    }
    private boolean commitSeek(MediaController target,long position,String key) {
        MediaController current=media();
        if (target==null || current==null || !canSeek() || !current.getSessionToken().equals(target.getSessionToken())
                || !trackKey(current.getMetadata()).equals(key)) return false;
        pendingSeek=Math.max(0,Math.min(durationMs,position));pendingSeekAt=SystemClock.elapsedRealtime();pendingTrackKey=key;
        target.getTransportControls().seekTo(pendingSeek);
        android.util.Log.i("AmbientSeek","seek_sent");interact.run();return true;
    }
    private void cancelSeeking() {
        if (seek.isActive()) android.util.Log.i("AmbientSeek","hold_cancelled");
        handler.removeCallbacks(hold);seek.cancel();seekMedia=null;
        if (seekLabel!=null) seekLabel.setVisibility(INVISIBLE);
        if (progress!=null) updateProgress();
    }
    boolean isSeeking() { return seek.isActive(); }
    boolean seekAvailable() { return canSeek(); }
    private static String time(long ms) {
        long seconds = Math.max(0, ms / 1_000);
        return seconds >= 3_600 ? String.format(Locale.ROOT, "%d:%02d:%02d", seconds / 3_600, seconds / 60 % 60, seconds % 60)
            : String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
    }
    private float scale() { return Math.min(viewportWidth / 400f, viewportHeight / 650f); }
    void setVisualProgress(float value) {
        fraction = value;
        if (fraction<1 && seek.isDown()) cancelSeeking();
        updateVisuals();
        if (fraction > 0 && !auxiliaryOpen() && !pocketed && !handler.hasCallbacks(tick)) handler.post(tick);
        if (fraction <= 0) handler.removeCallbacks(tick);
        if (fraction <= 0) cancelTrackTransition();
    }
    private void updateVisuals() {
        invalidateStatus();
        updateSplitQueue();
        float alpha = Math.max(0, (fraction - 0.4f) / 0.6f)*(1-queueFraction);
        float y = (1 - fraction) * 18 * scale();
        for (View view : animatedWidgets) {
            view.setAlpha(alpha);
            view.setTranslationX(scene.offsetX()+scene.contentCenterX()-(playerLeft+viewportWidth/2f));
            view.setTranslationY(y + scene.contentOffsetY());
        }
        boolean interactive=!scene.columnsMoving();
        like.setEnabled(interactive && feedbackTarget!=null);dislike.setEnabled(interactive && feedbackTarget!=null);
        shuffle.setEnabled(interactive && shuffleReady);
        seekTouch.setEnabled(interactive);
        seekTouch.setVisibility(alpha > 0 ? VISIBLE : INVISIBLE);
        elapsed.setVisibility(alpha > 0 ? VISIBLE : INVISIBLE);
        duration.setVisibility(alpha > 0 ? VISIBLE : INVISIBLE);
        like.setVisibility(alpha > 0 && likeAvailable ? VISIBLE : INVISIBLE);
        dislike.setVisibility(alpha > 0 && dislikeAvailable ? VISIBLE : INVISIBLE);
        shuffle.setVisibility(alpha > 0 && shuffleAvailable ? VISIBLE : INVISIBLE);
    }
    private void updateSplitQueue() {
        if(queue==null || !splitColumns())return;
        boolean visible=fraction>0 && !pocketed && getWindowVisibility()==VISIBLE;
        boolean observe=visible && scene.isActiveTarget() && !auxiliaryOpen() && queueFraction==0 && isAttachedToWindow();
        queue.setVisibility(visible?VISIBLE:INVISIBLE);
        float reveal=scene.queueReveal();
        queue.setAlpha(reveal*(1-queueFraction));
        queue.setTranslationX((1-reveal)*(columns.queueLeft()==0?-12:12)*scale());
        if(observe!=splitQueueObserving){splitQueueObserving=observe;if(observe)queue.opened();else queue.closed();}
        boolean interactive=observe && reveal==1 && !scene.columnsMoving();
        queue.setInteractive(interactive);
        queue.setImportantForAccessibility(interactive?IMPORTANT_FOR_ACCESSIBILITY_YES:IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
    }
    void invalidateStatus(){
        if(statusOverlay!=null)statusOverlay.invalidate();
        if(accessoryOpen){if(!scene.accessoryConnected())closeAccessory(true);else accessory.refresh(scene.accessoryReading());}
    }
    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int width = MeasureSpec.getSize(widthSpec), height = MeasureSpec.getSize(heightSpec);
        PlayerColumns next=PlayerColumns.resolve(width,getResources().getDisplayMetrics().density,
            getResources().getConfiguration().smallestScreenWidthDp,PlayerPreferences.internalReversed(getContext()));
        if (!next.equals(columns) || viewportHeight != height || feedbackLayoutDirty) {
        feedbackLayoutDirty=false;
        if(columns!=null && columns.split()!=next.split()) {
            if(splitQueueObserving){splitQueueObserving=false;queue.closed();}
            closeAuxiliary(false);
        }
        columns=next;playerLeft=next.playerLeft();viewportWidth=next.playerWidth();viewportHeight=height;
        if(!next.split() && !queueOpen){queue.setVisibility(INVISIBLE);queue.setAlpha(queueFraction);}
        scene.setPlayerViewport(playerLeft,viewportWidth,next.split());
        queue.setPersistent(next.split());
        LayoutParams queueParams=new LayoutParams(next.queueWidth(),height);queueParams.leftMargin=next.queueLeft();queue.setLayoutParams(queueParams);
        float s = scale();
        place(seekTouch, -144, -36, 288, 48, s);
        LayoutParams bar = new LayoutParams(LayoutParams.MATCH_PARENT,Math.round(20*s));
        bar.topMargin=Math.round(13*s);progress.setLayoutParams(bar);
        place(seekLabel, -32, -54, 64, 26, s);
        seekLabel.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,12*s);
        place(elapsed, -144, -4, 100, 18, s);
        place(duration, 44, -4, 100, 18, s);
        place(like, FeedbackLayout.left(likeAvailable,dislikeAvailable,shuffleAvailable,0), 126, 48, 48, s);
        place(dislike, FeedbackLayout.left(likeAvailable,dislikeAvailable,shuffleAvailable,1), 126, 48, 48, s);
        place(shuffle, FeedbackLayout.left(likeAvailable,dislikeAvailable,shuffleAvailable,2), 126, 48, 48, s);
        progress.setTrackThickness(Math.max(1, Math.round(3 * s)));
        progress.setTrackCornerRadius(Math.round(2 * s));
        progress.setWaveAmplitude(Math.round(2.5f * s));
        progress.setWavelength(Math.round(26 * s));
        progress.setTrackStopIndicatorSize(Math.round(4 * s));
        for (MaterialButton button : new MaterialButton[]{like, dislike, shuffle}) {
            button.setIconSize(Math.round(22 * s));
            button.setGravity(android.view.Gravity.CENTER);
            button.setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_TOP);
            button.setCornerRadius(Math.round(24 * s));
            button.setInsetTop(0); button.setInsetBottom(0);
            button.setPadding(0,0,0,0);
            button.setMinimumWidth(0); button.setMinimumHeight(0);
            button.setStrokeWidth(Math.max(1,Math.round(0.5f*s)));
        }
        elapsed.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,11*s);
        duration.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,11*s);
        updateProgress();
        }
        if(queue!=null)queue.viewport(columns.queueWidth(),height,scale());
        if(accessory!=null)accessory.viewport(width,height,Math.min(width/400f,height/650f));
        super.onMeasure(widthSpec,heightSpec);
        updateVisuals();
    }
    private void place(View view, float x, float y, float width, float height, float s) {
        LayoutParams params = new LayoutParams(Math.round(width*s), Math.round(height*s));
        params.leftMargin = Math.round(playerLeft+viewportWidth/2f + x*s);
        params.topMargin = Math.round(viewportHeight/2f + y*s);
        view.setLayoutParams(params);
    }
    @Override protected void onLayout(boolean changed,int left,int top,int right,int bottom) {
        super.onLayout(changed,left,top,right,bottom);
        updateVisuals();
    }
    @Override protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        handler.removeCallbacks(tick);
        if (visibility!=VISIBLE && seekTouch!=null) cancelSeeking();
        if (visibility!=VISIBLE) {cancelTrackTransition();closeAuxiliary(false);}
        if(queue!=null)updateSplitQueue();
        if (visibility == VISIBLE && fraction > 0) handler.post(tick);
    }
    @Override protected void onDetachedFromWindow() {
        if(splitQueueObserving){splitQueueObserving=false;queue.closed();}
        closeAuxiliary(false);
        if(queueTransition!=null)queueTransition.cancel();
        cancelTrackTransition();
        cancelSeeking();
        handler.removeCallbacksAndMessages(null);
        super.onDetachedFromWindow();
    }
    int progressValue() { return progress.getProgress(); }
    void setPocketed(boolean value) {
        if (pocketed==value) return;
        pocketed=value;
        updateSplitQueue();
        setVisibility(value?INVISIBLE:VISIBLE);
        if (value) {
            closeAuxiliary(false);
            cancelTrackTransition();cancelSeeking();handler.removeCallbacks(tick);
            progress.setWaveSpeed(0);scene.settleHiddenTransitions();
        } else {
            refreshSession();setVisualProgress(scene.progress());
        }
    }
    boolean progressVisible() { return seekTouch.getVisibility()==VISIBLE; }
    boolean progressTickScheduled() { return handler.hasCallbacks(tick); }
    boolean hasLike() { return likeAvailable; }
    boolean hasDislike() { return dislikeAvailable; }
    boolean isLikeSelected() { return like.isSelected(); }
    boolean isDislikeSelected() { return dislike.isSelected(); }
}
