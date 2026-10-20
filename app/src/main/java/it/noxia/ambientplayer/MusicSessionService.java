package it.noxia.ambientplayer;

import android.content.ComponentName;
import android.content.SharedPreferences;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.app.KeyguardManager;
import android.os.Build;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import java.util.ArrayList;
import java.util.List;
import java.io.FileDescriptor;
import java.io.PrintWriter;

/** Event-driven media sessions and ephemeral notification icons, processed on device. */
public class MusicSessionService extends NotificationListenerService {
    private static final List<Runnable> listeners = new ArrayList<>();
    private static final List<Runnable> iconListeners = new ArrayList<>();
    private static final NotificationIcons notificationIcons = new NotificationIcons();
    private static AccessoryBattery accessoryBattery;
    static AccessoryBattery accessoryBattery() { return accessoryBattery; }
    private static boolean clearNotificationState() {
        boolean changed=notificationIcons.clear() || accessoryBattery!=null;
        accessoryBattery=null;return changed;
    }
    static MediaController current;
    // Keep the actual callback timestamp. getPlaybackState() may synthesize a newer
    // position/timestamp even when YouTube Music has not published a new state.
    private static PlaybackState playbackSnapshot;
    static PlaybackState playbackState() { return playbackSnapshot; }
    static boolean connected;
    private static MusicSessionService instance;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private MediaSessionManager manager;
    private boolean observing;
    private boolean wasPlaying;
    private long lastManualScreenOff;
    private final AutoPlayerGate auto = new AutoPlayerGate();
    private final Runnable checkAuto = this::evaluateAutoStart;
    private static final long[] LOCK_CHECK_DELAYS={700,900,1600,2500};
    private int lockCheckAttempt;
    private final Runnable expireAutoRequest=auto::requestExpired;
    private final Runnable checkLock=new Runnable(){
        @Override public void run(){
            evaluateAutoStart();
            Log.i("AmbientAuto","lock_check attempt="+(lockCheckAttempt+1)+" locked="+getSystemService(KeyguardManager.class).isKeyguardLocked()+" surface="+MainActivity.hasSurface());
            if(!MainActivity.hasSurface() && observing && PlayerPreferences.isEnabled(MusicSessionService.this) && auto.canRetry()
                    && ++lockCheckAttempt<LOCK_CHECK_DELAYS.length)handler.postDelayed(this,LOCK_CHECK_DELAYS[lockCheckAttempt]);
        }
    };
    private final Runnable updateIcons = this::refreshNotificationIcons;
    private final BroadcastReceiver screenEvents = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (Intent.ACTION_USER_PRESENT.equals(action)) {
                auto.dismissed();
                handler.removeCallbacks(checkAuto);handler.removeCallbacks(checkLock);handler.removeCallbacks(expireAutoRequest);
            } else if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                boolean manualOff = SystemClock.uptimeMillis() - lastManualScreenOff < 1_000;
                auto.newLockCycle(MainActivity.hasSurface() || manualOff);
                scheduleLockCheck();
            } else if (Intent.ACTION_SCREEN_ON.equals(action)) {
                auto.userWokeScreen();
                scheduleLockCheck();
            }
        }
    };
    private final SharedPreferences.OnSharedPreferenceChangeListener preferencesChanged = (prefs, key) -> {
        if (PlayerPreferences.ENABLED.equals(key)) updateMonitoring();
    };
    private final java.util.LinkedHashMap<android.media.session.MediaSession.Token,SessionWatch> watched=new java.util.LinkedHashMap<>();
    private android.media.session.MediaSession.Token preferredSession;
    private final class SessionWatch extends MediaController.Callback {
        final MediaController controller;PlaybackState state;SessionShuffle shuffle;
        void start(){if(!MusicSources.hearts(controller.getPackageName()))shuffle=new SessionShuffle(MusicSessionService.this,controller,handler,()->{if(live() && selected())notifyListeners();});}
        void close(){controller.unregisterCallback(this);if(shuffle!=null)shuffle.close();}
        SessionWatch(MediaController controller){this.controller=controller;state=controller.getPlaybackState();}
        boolean live(){return observing && watched.get(controller.getSessionToken())==this;}
        boolean playing(){return state!=null && state.getState()==PlaybackState.STATE_PLAYING;}
        boolean selected(){return current!=null && current.getSessionToken().equals(controller.getSessionToken());}
        @Override public void onPlaybackStateChanged(PlaybackState next){
            if(!live())return;boolean started=!playing() && next!=null && next.getState()==PlaybackState.STATE_PLAYING;
            state=next;
            if(started || selected()){selectSession(started?this:null);publishState();}
        }
        @Override public void onMetadataChanged(MediaMetadata metadata){if(live() && selected())publishState();}
        @Override public void onQueueChanged(java.util.List<android.media.session.MediaSession.QueueItem> queue){if(live() && selected())publishState();}
        @Override public void onQueueTitleChanged(CharSequence title){if(live() && selected())publishState();}
        @Override public void onSessionDestroyed(){if(live())refreshSessions();}
    }
    static SessionShuffle sessionShuffle(MediaController media){
        SessionWatch watch=instance==null || media==null?null:instance.watched.get(media.getSessionToken());
        return watch==null?null:watch.shuffle;
    }
    private void selectSession(SessionWatch preferred){
        SessionWatch selected=watched.get(preferredSession);
        if(preferred!=null && preferred.playing())selected=preferred;
        else if(selected==null || !selected.playing())for(SessionWatch candidate:watched.values())if(candidate.playing()){selected=candidate;break;}
        // A vanished current session is a gap, not permission to show another app's old paused song.
        if(selected==null && preferredSession==null && !watched.isEmpty())selected=watched.values().iterator().next();
        if(selected!=null)preferredSession=selected.controller.getSessionToken();
        current=selected==null?null:selected.controller;playbackSnapshot=selected==null?null:selected.state;
    }
    private final MediaSessionManager.OnActiveSessionsChangedListener sessions = this::refresh;

    static void addListener(Runnable listener) { listeners.add(listener); }
    static void removeListener(Runnable listener) { listeners.remove(listener); }
    private static void notifyListeners() {
        for (Runnable listener : new ArrayList<>(listeners)) listener.run();
    }
    static void addIconListener(Runnable listener) { iconListeners.add(listener); }
    static void removeIconListener(Runnable listener) { iconListeners.remove(listener); }
    static NotificationIcons.Snapshot notificationIcons(boolean dim) { return notificationIcons.snapshot(dim); }
    private static void notifyIconListeners() {
        for (Runnable listener : new ArrayList<>(iconListeners)) listener.run();
    }
    private void scheduleIcons() {
        if (!connected || !PlayerPreferences.isEnabled(this)) return;
        handler.removeCallbacks(updateIcons);
        handler.postDelayed(updateIcons,150);
    }
    private void refreshNotificationIcons() {
        if (!connected || !PlayerPreferences.isEnabled(this)) return;
        try {
            StatusBarNotification[] notifications=getActiveNotifications();
            RankingMap rankings=getCurrentRanking();
            AccessoryBattery next=AccessorySupport.readBattery(notifications,rankings);
            boolean changed=!java.util.Objects.equals(next,accessoryBattery);
            accessoryBattery=next;
            if (notificationIcons.update(this,notifications,rankings) || changed) notifyIconListeners();
        } catch (SecurityException unavailable) {
            if (clearNotificationState()) notifyIconListeners();
        }
    }
    @Override public void onNotificationPosted(StatusBarNotification notification, RankingMap rankings) { scheduleIcons(); }
    @Override public void onNotificationRemoved(StatusBarNotification notification, RankingMap rankings) { scheduleIcons(); }
    @Override public void onNotificationRankingUpdate(RankingMap rankings) { scheduleIcons(); }
    private ComponentName component() { return new ComponentName(this, MusicSessionService.class); }
    static boolean isPlaying() {
        PlaybackState state = current == null ? null : playbackSnapshot;
        return state != null && state.getState() == PlaybackState.STATE_PLAYING;
    }
    static boolean accessGranted(Context context) {
        return context.getSystemService(android.app.NotificationManager.class).isNotificationListenerAccessGranted(new ComponentName(context,MusicSessionService.class));
    }
    static PlaybackPresence.Kind presenceKind() {
        PlaybackState state=current==null?null:playbackSnapshot;
        if(state==null)return PlaybackPresence.Kind.MISSING;
        return switch(state.getState()) {
            case PlaybackState.STATE_PLAYING -> PlaybackPresence.Kind.PLAYING;
            case PlaybackState.STATE_BUFFERING,PlaybackState.STATE_CONNECTING,PlaybackState.STATE_SKIPPING_TO_NEXT,
                PlaybackState.STATE_SKIPPING_TO_PREVIOUS,PlaybackState.STATE_SKIPPING_TO_QUEUE_ITEM,
                PlaybackState.STATE_FAST_FORWARDING,PlaybackState.STATE_REWINDING -> PlaybackPresence.Kind.TRANSIENT;
            case PlaybackState.STATE_PAUSED -> PlaybackPresence.Kind.PAUSED;
            case PlaybackState.STATE_NONE -> PlaybackPresence.Kind.MISSING;
            default -> PlaybackPresence.Kind.STOPPED;
        };
    }
    static void recheckAutomatic() {
        if (instance != null) instance.handler.post(instance.checkAuto);
    }
    static void surfaceOpened() { if (instance != null) {
        instance.auto.opened();instance.handler.removeCallbacks(instance.checkLock);instance.handler.removeCallbacks(instance.expireAutoRequest);
    } }
    static void surfaceClosed(String reason) {
        if (instance == null) return;
        instance.auto.dismissed();
        if ("screen_off".equals(reason)) instance.lastManualScreenOff = SystemClock.uptimeMillis();
    }
    @Override public void onCreate() {
        super.onCreate();
        instance = this;
        PlayerPreferences.store(this).registerOnSharedPreferenceChangeListener(preferencesChanged);
        IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_USER_PRESENT);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(screenEvents, filter, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(screenEvents, filter);
    }
    @Override public void onListenerConnected() {
        connected = true;
        manager = getSystemService(MediaSessionManager.class);
        updateMonitoring();
    }
    private void updateMonitoring() {
        boolean enabled = connected && manager != null && PlayerPreferences.isEnabled(this);
        if (enabled) scheduleIcons();
        else {
            handler.removeCallbacks(updateIcons);
            if (clearNotificationState()) notifyIconListeners();
        }
        if (enabled && !observing) {
            try {
                manager.addOnActiveSessionsChangedListener(sessions, component(), handler);
                observing = true;
                refreshSessions();
            } catch(SecurityException revoked) {
                // Permission can change after onListenerConnected was queued by Android.
                disconnect();
            }
        } else if (enabled) {
            publishState();
        } else if (!enabled) {
            stopMonitoring();
            publishState();
        }
    }
    private void publishState() {
        MusicHistory.observe(MusicQueue.read());
        boolean playing = isPlaying();
        if (playing && !wasPlaying) auto.newPlayback();
        wasPlaying = playing;
        evaluateAutoStart();
        notifyListeners();
    }
    private void scheduleLockCheck() {
        handler.removeCallbacks(checkLock);handler.removeCallbacks(expireAutoRequest);
        auto.requestExpired();lockCheckAttempt=0;
        // A finite lock-cycle window handles a late keyguard or a silently rejected first launch.
        if(!MainActivity.hasSurface())handler.postDelayed(checkLock,LOCK_CHECK_DELAYS[0]);
    }
    private void evaluateAutoStart() {
        boolean enabled = observing && PlayerPreferences.isEnabled(this);
        boolean permission = Settings.canDrawOverlays(this);
        boolean locked = getSystemService(KeyguardManager.class).isKeyguardLocked();
        if (!auto.request(enabled, permission, isPlaying(), locked, MainActivity.hasSurface())) return;
        try {
            startActivity(new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra("automatic", true));
            Log.i("AmbientAuto", "auto_start_requested");
        } catch (RuntimeException unavailable) {
            Log.w("AmbientAuto", "auto_start_unavailable", unavailable);
        }
        // BAL can be rejected silently by Android. No retry loop or wake-up polling.
        handler.removeCallbacks(expireAutoRequest);handler.postDelayed(expireAutoRequest, 3_000);
    }
    private void refreshSessions() {
        if (!observing || manager == null) return;
        try { refresh(manager.getActiveSessions(component())); }
        catch (SecurityException revoked) { disconnect(); }
    }
    private void stopMonitoring() {
        handler.removeCallbacks(checkLock);handler.removeCallbacks(expireAutoRequest);
        if (observing && manager != null) manager.removeOnActiveSessionsChangedListener(sessions);
        observing = false;
        for(SessionWatch item:watched.values())item.close();
        watched.clear();preferredSession=null;current = null;
        playbackSnapshot=null;
    }
    private void refresh(List<MediaController> controllers) {
        if (!observing || !PlayerPreferences.isEnabled(this)) return;
        var next=new java.util.LinkedHashMap<android.media.session.MediaSession.Token,SessionWatch>();
        if(controllers!=null)for(MediaController controller:controllers){
            if(!MusicSources.supported(controller.getPackageName()))continue;
            var token=controller.getSessionToken();SessionWatch watch=watched.get(token);
            if(watch==null){watch=new SessionWatch(controller);controller.registerCallback(watch,handler);watch.start();}
            next.put(token,watch);
        }
        for(var entry:watched.entrySet())if(!next.containsKey(entry.getKey()))entry.getValue().close();
        watched.clear();watched.putAll(next);selectSession(null);publishState();
    }
    @Override public void onListenerDisconnected() {
        disconnect();
        if(PlayerPreferences.isEnabled(this) && accessGranted(this))requestRebind(component());
    }
    private void disconnect() {
        connected = false;
        handler.removeCallbacks(updateIcons);
        if (clearNotificationState()) notifyIconListeners();
        stopMonitoring();
        publishState();
    }
    @Override public void onDestroy() {
        PlayerPreferences.store(this).unregisterOnSharedPreferenceChangeListener(preferencesChanged);
        unregisterReceiver(screenEvents);
        handler.removeCallbacksAndMessages(null);
        disconnect();
        if (instance == this) instance = null;
        super.onDestroy();
    }
    @Override protected void dump(FileDescriptor fd, PrintWriter writer, String[] args) {
        writer.println("listenerConnected=" + connected);
        writer.println("monitoringEnabled=" + observing);
        writer.println("lockCheckPending=" + handler.hasCallbacks(checkLock));
        writer.println("lockCheckAttempt=" + lockCheckAttempt);
        writer.println("overlayPermission=" + Settings.canDrawOverlays(this));
        writer.println("sessionPackage=" + (current == null ? "none" : current.getPackageName()));
        writer.println("musicPlaying=" + isPlaying());
        writer.println("playbackPresence="+presenceKind());
        writer.println("notificationApps=" + notificationIcons.snapshot(false).appCount());
        writer.println("ambientNotificationApps=" + notificationIcons.snapshot(true).appCount());
        writer.println("accessoryBatteryAvailable="+(accessoryBattery!=null));
        if(accessoryBattery!=null)writer.println("accessoryBattery="+accessoryBattery.left()+","+accessoryBattery.right()+","+accessoryBattery.caseLevel());
        MusicQueue.Snapshot queue=MusicQueue.read();
        writer.println("queueSize="+queue.entries().size());
        writer.println("queueCurrentIndex="+queue.current());
        writer.println("queueSkipSupported="+queue.skip());
        writer.println("queueLegacySkipAvailable="+queue.legacySkip());
        writer.println("queuePlayFromIdSupported="+queue.fromId());
        writer.println("queueSelectable="+queue.entries().stream().filter(queue::selectable).count());
        writer.println("queueBitmapCount="+queue.entries().stream().filter(e->e.artwork()!=null).count());
        try {
            var published=current==null?null:current.getQueue();
            writer.println("queueIconUriCount="+(published==null?0:published.stream().filter(e->e!=null && e.getDescription().getIconUri()!=null).count()));
        } catch(SecurityException|IllegalStateException unavailable) { writer.println("queueIconUriCount=unavailable"); }
        PlaybackState playback = current == null ? null : current.getPlaybackState();
        boolean hearts=MusicFeedback.hearts(current);
        writer.println("feedbackStyle="+(hearts?"heart":"thumbs"));
        if(hearts){
            Boolean favorite=MusicFeedback.bitchord(current)?MusicFeedback.favoriteState(playback,current.getMetadata()):current.getMetadata()==null || current.getMetadata().getRating(MediaMetadata.METADATA_KEY_USER_RATING)==null?null:MusicFeedback.selected(current,playback,current.getMetadata(),true);
            writer.println("favoriteState="+(favorite==null?"unknown":favorite?"filled":"outline"));
        }
        if(current!=null){
            Boolean shuffled=MusicShuffle.selected(current,playback,current.getMetadata());
            writer.println("shuffleAvailable="+MusicShuffle.available(current,playback));
            writer.println("shuffleState="+(shuffled==null?"unknown":shuffled?"on":"off"));
        }
        writer.println("seekAvailable="+(playback!=null && (playback.getActions()&PlaybackState.ACTION_SEEK_TO)!=0));
        if (playback != null) for (PlaybackState.CustomAction action : playback.getCustomActions()) {
            writer.println("customAction=" + action.getAction() + " label=" + action.getName());
        }
    }
}
