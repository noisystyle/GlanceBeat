package it.noxia.ambientplayer;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;
import android.provider.Settings;
import java.lang.ref.WeakReference;
import android.view.Display;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.graphics.Color;
import android.widget.FrameLayout;
import android.view.GestureDetector;
import android.view.MotionEvent;
import org.json.JSONObject;

public class MainActivity extends Activity {
    private static WeakReference<MainActivity> surface = new WeakReference<>(null);
    static boolean hasSurface() {
        MainActivity activity = surface.get();
        return activity != null && !activity.isFinishing() && !activity.isDestroyed();
    }
    static MainActivity currentSurface(){MainActivity activity=surface.get();return activity!=null && !activity.isFinishing()?activity:null;}
    boolean continuityEligible(){
        PlaybackPresence.Kind kind=MusicSessionService.presenceKind();
        return !preview && !isFinishing() && !isDestroyed() && PlayerPreferences.isEnabled(this)
            && getSystemService(KeyguardManager.class).isKeyguardLocked()
            && modes.mode()!=PlayerModeController.Mode.AUTHENTICATING && modes.mode()!=PlayerModeController.Mode.CLOSED
            && (entrance==null || !entrance.pending())
            && (kind==PlaybackPresence.Kind.PLAYING || kind==PlaybackPresence.Kind.TRANSIENT);
    }
    void activateFromContinuity(){activatePlayer();}
    void prepareContinuity(){if(playerRoot!=null && (entrance==null || !entrance.pending()))PlayerContinuityService.prepare(this,playerRoot);}
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final PlayerModeController modes = new PlayerModeController();
    private FrameLayout playerRoot;
    private PlayerView player;
    private WindowEntrance entrance;
    private MusicLaunch.Request pendingMusicLaunch;
    private android.app.PendingIntent pendingNotificationLaunch;
    private PlayerSurface widgets;
    private AmbientBrightness ambientLight;
    private PocketProximity pocket;
    private boolean pocketed;
    private CarBluetooth carBluetooth;
    private boolean resumed;
    private boolean probe;
    private boolean preview;
    private boolean automatic;
    private boolean closingNotified;
    private boolean keepScreen;
    private boolean displayListenerRegistered;
    private boolean receiverRegistered;
    private int lastDisplayState = Display.STATE_UNKNOWN;
    private long interactionBoostUntil;
    private final Runnable endInteractionBoost = () -> {
        if (SystemClock.uptimeMillis() < interactionBoostUntil) {
            handler.postDelayed(this.endInteractionBoost, interactionBoostUntil-SystemClock.uptimeMillis());
        } else updateRefreshRate();
    };
    private final PlaybackPresence playbackPresence=new PlaybackPresence();
    private final Runnable closePaused = this::updatePlaybackVisibility;
    private final Runnable dim = () -> {
        modes.expire(SystemClock.uptimeMillis());
        applyMode();
    };
    private final Runnable shift = new Runnable() {
        @Override public void run() {
            if (!pocketed && player != null && modes.mode() == PlayerModeController.Mode.AMBIENT) player.shiftPosition();
            handler.postDelayed(this, 120_000);
        }
    };
    private final BroadcastReceiver lockReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_USER_PRESENT.equals(intent.getAction())) completeUnlock("system_unlocked");
            else if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) closePlayer("screen_off");
        }
    };
    private final DisplayManager.DisplayListener displayListener = new DisplayManager.DisplayListener() {
        @Override public void onDisplayAdded(int id) { }
        @Override public void onDisplayRemoved(int id) { }
        @Override public void onDisplayChanged(int id) {
            logState("display_changed");
            Display display = getDisplay();
            int state = display == null ? Display.STATE_UNKNOWN : display.getState();
            if (state != lastDisplayState && player != null) player.invalidate();
            lastDisplayState = state;
        }
    };
    private final Runnable notificationListener = () -> {
        if (player != null) player.refreshNotifications();
        logState("notification_icons_changed");
    };
    private final Runnable musicListener = () -> {
        if (player != null) player.refreshMedia();
        if (widgets != null) widgets.refreshSession();
        updatePlaybackVisibility();
        updateDrivingMode();
        PlayerContinuityService.refresh(this,playerRoot);
        logState("session_changed");
    };
    private final SharedPreferences.OnSharedPreferenceChangeListener preferenceListener = (prefs, key) -> {
        if (PlayerPreferences.ENABLED.equals(key) && !PlayerPreferences.isEnabled(this) && !preview)
            closePlayer("player_disabled");
        else if (PlayerPreferences.drivingKey(key)) updateDrivingMonitoring();
        else if (PlayerPreferences.INTERNAL_REVERSED.equals(key) && widgets!=null) widgets.requestLayout();
    };

    @android.annotation.SuppressLint("ClickableViewAccessibility") // Black-surface double tap delegates to its accessible click action.
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        preview = this instanceof PreviewActivity;
        probe = BuildConfig.DEBUG && getIntent().getBooleanExtra("probe", false);
        automatic = !preview && !probe && getIntent().getBooleanExtra("automatic", false);
        if (!preview && !probe) ambientLight = new AmbientBrightness(this,this::applyAmbientBrightness);
        if (!preview && !probe) pocket = new PocketProximity(this,this::applyPocketState);
        if (!preview && !probe && !PlayerPreferences.isEnabled(this)) {
            closePlayer("player_disabled");
            return;
        }
        if (automatic && (!(MusicSessionService.isPlaying() || MusicSessionService.presenceKind()==PlaybackPresence.Kind.TRANSIENT) || !Settings.canDrawOverlays(this))) {
            closePlayer("automatic_requirements_missing");
            return;
        }
        if (!preview) {
            surface = new WeakReference<>(this);
            MusicSessionService.surfaceOpened();
        }
        boolean wake = !BuildConfig.DEBUG || getIntent().getBooleanExtra("wake", !probe);
        keepScreen = !preview && (!BuildConfig.DEBUG || getIntent().getBooleanExtra("keep", !probe));
        setShowWhenLocked(!preview);
        setTurnScreenOn(!preview && wake);
        if(!preview)entrance=new WindowEntrance(this,()->{applyMode();logState("entrance_settled");prepareContinuity();});
        player = new PlayerView(this, probe || preview, new PlayerView.Actions() {
            @Override public void onActivate() { activatePlayer(); }
            @Override public void onToggle() {
                modes.toggle(SystemClock.uptimeMillis());
                applyMode();
            }
            @Override public void onInteract() { extendActiveMode(); }
            @Override public void onAccessory(){if(widgets!=null)widgets.openAccessory();}
            @Override public void onQueue() { if(widgets!=null)widgets.openQueue(); }
            @Override public void onUnlock() { requestSystemUnlock(); }
            @Override public void onOpenMusic() { openMusic(); }
            @Override public void onOpenNotification(android.app.PendingIntent action) { openNotification(action); }
            @Override public void onVisualFrame() {
                if (widgets != null) widgets.setVisualProgress(player.progress());
            }
            @Override public void onVisualSettled() {
                if (!isFinishing() && !isDestroyed()) {
                    applyMode();
                    if(widgets!=null)widgets.refreshFeedback();
                    logState("visual_settled");
                }
            }
            @Override public void onStatusChanged(){if(widgets!=null)widgets.invalidateStatus();if(playerRoot!=null)PlayerContinuityService.refresh(MainActivity.this,playerRoot);}
        });
        widgets = new PlayerSurface(this, player, this::extendActiveMode, probe || preview,()->{updatePlaybackVisibility();logState("queue_changed");});
        FrameLayout blackSurface=new FrameLayout(this);playerRoot=blackSurface;
        blackSurface.setBackgroundColor(Color.BLACK);
        blackSurface.setClickable(true);
        blackSurface.setOnClickListener(view -> {
            if (pocketed && pocket!=null) pocket.revealManually();
        });
        GestureDetector revealGesture=new GestureDetector(this,new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDown(MotionEvent event) { return true; }
            @Override public boolean onDoubleTap(MotionEvent event) {
                blackSurface.performClick();return true;
            }
        });
        blackSurface.setOnTouchListener((view,event) -> pocketed && pocket!=null
            && pocket.canRevealManually() && revealGesture.onTouchEvent(event));
        blackSurface.addView(widgets,new FrameLayout.LayoutParams(-1,-1));
        setContentView(blackSurface);
        if(entrance!=null)entrance.attach(blackSurface);
        if(Build.VERSION.SDK_INT>=33)getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
            android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,this::handleBack);
        if (!preview) carBluetooth = new CarBluetooth(this, this::updateDrivingMode);
        applyMode();
        hideSystemBars();
        getSystemService(DisplayManager.class).registerDisplayListener(displayListener, handler);
        displayListenerRegistered = true;
        MusicSessionService.addListener(musicListener);
        MusicSessionService.addIconListener(notificationListener);
        PlayerPreferences.store(this).registerOnSharedPreferenceChangeListener(preferenceListener);
        IntentFilter filter = new IntentFilter(Intent.ACTION_USER_PRESENT);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(lockReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(lockReceiver, filter);
        receiverRegistered = true;
        handler.postDelayed(shift, 120_000);
        int finishAfter = preview || !BuildConfig.DEBUG ? 0 : getIntent().getIntExtra("finish_after_ms", probe ? 15_000 : 0);
        if (finishAfter > 0) handler.postDelayed(() -> closePlayer("lab_timeout"), Math.min(finishAfter, 60_000));
        logState("created");
        closeIfUnlocked();
    }

    private void updatePlaybackVisibility() {
        if(!automatic || isFinishing())return;
        handler.removeCallbacks(closePaused);
        if(!Settings.canDrawOverlays(this) || !MusicSessionService.accessGranted(this)) {
            closePlayer("music_access_removed");return;
        }
        if(modes.mode()==PlayerModeController.Mode.AUTHENTICATING || widgets!=null && widgets.queuePending())return;
        android.media.session.MediaController media=MusicSessionService.current;
        long wait=playbackPresence.remaining(MusicSessionService.presenceKind(),media==null?null:media.getSessionToken(),SystemClock.uptimeMillis());
        if(wait==0)closePlayer("music_stopped");
        else if(wait>0)handler.postDelayed(closePaused,wait);
    }

    private void updateDrivingMonitoring() {
        if (carBluetooth == null) return;
        boolean observe = resumed && !isFinishing() && PlayerPreferences.driveEnabled(this)
            && !PlayerPreferences.driveAddress(this).isEmpty() && CarBluetooth.permitted(this);
        if (observe) carBluetooth.start(); else { carBluetooth.stop(); updateDrivingMode(); }
    }
    private void updateDrivingMode() {
        if (carBluetooth == null || isFinishing() || isDestroyed()) return;
        boolean active = DrivingModePolicy.active(PlayerPreferences.driveEnabled(this), PlayerPreferences.driveAddress(this),
            CarBluetooth.permitted(this), carBluetooth.bluetoothOn(), carBluetooth.connected(PlayerPreferences.driveAddress(this)),
            PlayerPreferences.driveCharging(this), carBluetooth.charging(), MusicSessionService.isPlaying(), pocketed);
        boolean before = modes.driving();
        modes.setDriving(active, SystemClock.uptimeMillis());
        if (before != modes.driving()) { applyMode(); logState("driving_changed"); }
    }

    private void activatePlayer() {
        modes.activate(SystemClock.uptimeMillis());
        applyMode();
    }
    private void extendActiveMode() {
        modes.interact(SystemClock.uptimeMillis());
        boostInteraction();
        scheduleDim();
    }
    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        if(entrance!=null && entrance.pending())return true;
        if (event.getActionMasked()==MotionEvent.ACTION_DOWN || event.getActionMasked()==MotionEvent.ACTION_MOVE
                || event.getActionMasked()==MotionEvent.ACTION_UP) boostInteraction();
        return super.dispatchTouchEvent(event);
    }
    private void boostInteraction() {
        if (pocketed || modes.mode()!=PlayerModeController.Mode.ACTIVE || isFinishing()) return;
        interactionBoostUntil=SystemClock.uptimeMillis()+1500;
        if(!handler.hasCallbacks(endInteractionBoost))handler.postDelayed(endInteractionBoost,1500);
        updateRefreshRate();
    }
    private float interactionRefreshRate() {
        Display display=getDisplay();
        if(display==null)return 60f;
        Display.Mode current=display.getMode();float highest=60f;
        for(Display.Mode mode:display.getSupportedModes())
            if(mode.getPhysicalWidth()==current.getPhysicalWidth() && mode.getPhysicalHeight()==current.getPhysicalHeight())
                highest=Math.max(highest,mode.getRefreshRate());
        return highest;
    }
    private float requestedRefreshRate() {
        if(preview || modes.mode()==PlayerModeController.Mode.AUTHENTICATING)return 0f;
        if(pocketed)return 1f;
        if(entrance!=null && entrance.pending())return 60f;
        boolean active=modes.mode()==PlayerModeController.Mode.ACTIVE;
        boolean moving=player!=null && player.isTransitionRunning();
        if(moving || active && SystemClock.uptimeMillis()<interactionBoostUntil)return interactionRefreshRate();
        return active || player!=null && player.isContentTransitionRunning()?60f:1f;
    }
    private void updateRefreshRate() {
        if(isFinishing() || isDestroyed())return;
        WindowManager.LayoutParams params=getWindow().getAttributes();float rate=requestedRefreshRate();
        if(Math.abs(params.preferredRefreshRate-rate)<.1f)return;
        params.preferredRefreshRate=rate;getWindow().setAttributes(params);logState("interaction_refresh_changed");
    }
    private void scheduleDim() {
        handler.removeCallbacks(dim);
        if (modes.hasActiveTimeout())
            handler.postDelayed(dim, Math.max(0, modes.activeUntil() - SystemClock.uptimeMillis()));
    }
    private void applyMode() {
        if (modes.mode() == PlayerModeController.Mode.CLOSED || isFinishing() || isDestroyed()) return;
        boolean active = modes.mode() == PlayerModeController.Mode.ACTIVE;
        boolean authenticating = modes.mode() == PlayerModeController.Mode.AUTHENTICATING;
        if(!active) {
            interactionBoostUntil=0;handler.removeCallbacks(endInteractionBoost);
            if(widgets!=null)widgets.closeAuxiliary(false);
        }
        if (player != null) player.setActive(active);
        boolean moving = player != null && player.isTransitionRunning();
        WindowManager.LayoutParams p = getWindow().getAttributes();
        if (widgets!=null) widgets.setPocketed(pocketed);
        p.preferredRefreshRate = requestedRefreshRate();
        p.screenBrightness = pocketed ? 0f : preview || active || moving || authenticating ? WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            : ambientLight==null?AmbientBrightnessPolicy.FALLBACK:ambientLight.brightness();
        getWindow().setAttributes(p);
        if (keepScreen && !authenticating) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        scheduleDim();
        updateAmbientLight();
        updatePocketMonitoring();
        logState("mode_changed");
    }

    private void handleBack() {
        if(widgets!=null && widgets.auxiliaryOpen()){extendActiveMode();widgets.closeAuxiliary(true);}
        else closePlayer("back");
    }
    @android.annotation.SuppressLint("GestureBackNavigation") // API 31–32 fallback; API 33+ registers OnBackInvokedCallback above.
    @Override public void onBackPressed() { handleBack(); }
    private void updateAmbientLight() {
        if (ambientLight!=null) ambientLight.setEnabled(!pocketed && resumed && hasWindowFocus() && !isFinishing()
            && modes.mode()==PlayerModeController.Mode.AMBIENT && player!=null && !player.isTransitionRunning());
    }
    private void applyAmbientBrightness() {
        if (pocketed || isFinishing() || isDestroyed() || modes.mode()!=PlayerModeController.Mode.AMBIENT
                || player==null || player.isTransitionRunning()) return;
        WindowManager.LayoutParams attributes=getWindow().getAttributes();
        attributes.screenBrightness=ambientLight.brightness();
        getWindow().setAttributes(attributes);
        logState("ambient_brightness_changed");
    }
    private void updatePocketMonitoring() {
        if (pocket==null) return;
        boolean enabled=resumed && hasWindowFocus() && !isFinishing()
            && modes.mode()!=PlayerModeController.Mode.AUTHENTICATING && modes.mode()!=PlayerModeController.Mode.CLOSED;
        pocket.setEnabled(enabled);
        if (!enabled && pocketed) applyPocketState();
    }
    private void applyPocketState() {
        boolean covered=pocket!=null && pocket.isCovered();
        if (covered==pocketed || isFinishing() || isDestroyed()) return;
        pocketed=covered;
        if (covered && modes.mode()==PlayerModeController.Mode.ACTIVE) modes.toggle(SystemClock.uptimeMillis());
        updateDrivingMode();
        applyMode();logState(covered?"pocket_covered":"pocket_clear");
    }
    private void openMusic() {
        if(preview || pocketed || isFinishing() || isDestroyed() || modes.mode()!=PlayerModeController.Mode.ACTIVE)return;
        MusicLaunch.Request launch=MusicLaunch.resolve(this,MusicSessionService.current);
        if(launch==null){android.widget.Toast.makeText(this,getString(R.string.open_track_unavailable),android.widget.Toast.LENGTH_SHORT).show();return;}
        pendingNotificationLaunch=null;
        pendingMusicLaunch=launch;
        if(!getSystemService(KeyguardManager.class).isKeyguardLocked())completeUnlock("open_music_unlocked");
        else requestSystemUnlock();
    }
    private void openNotification(android.app.PendingIntent action) {
        if(action==null || preview || pocketed || isFinishing() || isDestroyed() || modes.mode()!=PlayerModeController.Mode.ACTIVE)return;
        pendingMusicLaunch=null;pendingNotificationLaunch=action;
        if(!getSystemService(KeyguardManager.class).isKeyguardLocked())completeUnlock("open_notification_unlocked");
        else requestSystemUnlock();
    }
    private void completeUnlock(String reason) {
        if(isFinishing() || isDestroyed() || getSystemService(KeyguardManager.class).isKeyguardLocked())return;
        MusicLaunch.Request launch=pendingMusicLaunch;
        android.app.PendingIntent notification=pendingNotificationLaunch;
        pendingNotificationLaunch=null;
        pendingMusicLaunch=null; // Consume before launching: callback and USER_PRESENT may both arrive.
        if(launch!=null && !MusicLaunch.open(this,launch,MusicSessionService.current))
            android.widget.Toast.makeText(this,getString(R.string.open_track_failed),android.widget.Toast.LENGTH_SHORT).show();
        if(notification!=null && MusicSessionService.notificationIcons(false).actions().contains(notification))try{
            // Explicit user tap: allow the notification's Activity to launch on recent Android versions.
            android.app.ActivityOptions options=android.app.ActivityOptions.makeBasic();
            if(Build.VERSION.SDK_INT>=36)options.setPendingIntentBackgroundActivityStartMode(android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_IF_VISIBLE);
            else if(Build.VERSION.SDK_INT>=34)options.setPendingIntentBackgroundActivityStartMode(android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED);
            notification.send(this,0,null,null,null,null,options.toBundle());
        }catch(android.app.PendingIntent.CanceledException | SecurityException unavailable){
            android.widget.Toast.makeText(this,getString(R.string.notification_unavailable),android.widget.Toast.LENGTH_SHORT).show();
        }
        closePlayer(reason);
    }
    private void requestSystemUnlock() {
        PlayerContinuityService.dismiss(this);
        if(entrance!=null)entrance.finish();
        if (preview) { finish(); return; }
        if (!modes.beginAuthentication()) return;
        applyMode();
        logState("unlock_requested");
        // The OS chooses fingerprint, pattern, PIN or password according to its policy.
        // No custom biometric prompt, credential input or keyguard disabling.
        getSystemService(KeyguardManager.class).requestDismissKeyguard(this,
            new KeyguardManager.KeyguardDismissCallback() {
                @Override public void onDismissSucceeded() { completeUnlock("unlock_succeeded"); }
                @Override public void onDismissCancelled() {
                    pendingMusicLaunch=null;pendingNotificationLaunch=null;
                    modes.cancelAuthentication();
                    if (!isFinishing() && !isDestroyed()) applyMode();
                    updatePlaybackVisibility();
                    logState("unlock_cancelled");
                }
                @Override public void onDismissError() {
                    // Expose the native lock screen rather than leave the player in the way.
                    closePlayer("unlock_error_native_fallback");
                }
            });
    }
    private void closeIfUnlocked() {
        if (!preview && player != null && !getSystemService(KeyguardManager.class).isKeyguardLocked())
            completeUnlock("keyguard_gone");
    }
    private void closePlayer(String reason) {
        PlayerContinuityService.dismiss(this);
        if(entrance!=null)entrance.release();
        pendingMusicLaunch=null;pendingNotificationLaunch=null;
        if (isFinishing() || isDestroyed()) return;
        if (ambientLight!=null) ambientLight.stop();
        if (pocket!=null) pocket.stop();
        if (carBluetooth!=null) carBluetooth.stop();
        modes.unlocked();
        handler.removeCallbacksAndMessages(null);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        logState(reason);
        if (!preview && !closingNotified) {
            MusicSessionService.surfaceClosed(reason);
            closingNotified = true;
        }
        if (preview) finish();
        else finishAndRemoveTask();
    }
    private void logState(String event) {
        if (!BuildConfig.DEBUG) return;
        try {
            Display display = getDisplay();
            JSONObject data = new JSONObject();
            data.put("event", event);
            data.put("mode", modes.mode().name());
            data.put("interactive", getSystemService(PowerManager.class).isInteractive());
            data.put("locked", getSystemService(KeyguardManager.class).isKeyguardLocked());
            data.put("displayState", display == null ? 0 : display.getState());
            data.put("reportedRefreshRate", display == null ? 0 : display.getRefreshRate());
            data.put("requestedFrameRate", getWindow().getAttributes().preferredRefreshRate);
            data.put("continuityConnected",PlayerContinuityService.connected());
            data.put("continuityCovering",PlayerContinuityService.covering(this));
            data.put("entrancePending",entrance!=null && entrance.pending());
            data.put("entranceRunning",entrance!=null && entrance.running());
            data.put("entranceOpaque",entrance==null || entrance.opaque());
            data.put("windowAlpha",getWindow().getAttributes().alpha);
            data.put("brightnessOverride", getWindow().getAttributes().screenBrightness);
            data.put("drivingConfigured", PlayerPreferences.driveEnabled(this));
            data.put("drivingActive", modes.driving());
            data.put("drivingBluetoothPermission", CarBluetooth.permitted(this));
            data.put("drivingBluetoothListening", carBluetooth != null && carBluetooth.listening());
            data.put("drivingCarConnected", carBluetooth != null && carBluetooth.connected(PlayerPreferences.driveAddress(this)));
            data.put("drivingChargingOnly", PlayerPreferences.driveCharging(this));
            data.put("dimScheduled", handler.hasCallbacks(dim));
            data.put("keepScreenOn", (getWindow().getAttributes().flags & WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) != 0);
            data.put("queueOpen",widgets!=null && widgets.queueOpen());
            data.put("accessoryOpen",widgets!=null && widgets.accessoryOpen());
            data.put("accessoryConnected",player!=null && player.accessoryConnected());
            data.put("queuePending",widgets!=null && widgets.queuePending());
            data.put("coverTravel",player==null?0:player.coverTravel());
            data.put("coverArmed",player==null?0:player.coverArmed());
            data.put("coverDetents",player==null?0:player.coverDetents());
            data.put("previousCoverRetained",player!=null && player.previousCoverRetained());
            data.put("adjacentCoversReady",player==null?0:player.adjacentReady());
            data.put("adjacentCoversLoading",player==null?0:player.adjacentLoading());
            data.put("queueItems",widgets==null?0:widgets.queueItems());
            data.put("queueArtworkCached",widgets==null?0:widgets.queueArtworkCached());
            data.put("queueArtworkPending",widgets==null?0:widgets.queueArtworkPending());
            data.put("queueEdgeFrost",widgets!=null && widgets.queueEdgeFrost());
            data.put("queueHeaderFrost",widgets!=null && widgets.queueHeaderFrost());
            data.put("queueThumbnailLoading",widgets==null?0:widgets.queueThumbnailLoading());
            data.put("queueAnimating",widgets!=null && widgets.queueAnimating());
            data.put("seekAvailable", widgets!=null && widgets.seekAvailable());
            data.put("seeking", widgets!=null && widgets.isSeeking());
            data.put("progressValue", widgets == null ? 0 : widgets.progressValue());
            data.put("progressVisible", widgets != null && widgets.progressVisible());
            data.put("trackCounterAnimating", widgets != null && widgets.trackTransitionRunning());
            data.put("displayedElapsedMs", widgets == null ? 0 : widgets.displayedElapsedMs());
            data.put("displayedDurationMs", widgets == null ? 0 : widgets.displayedDurationMs());
            data.put("progressTickScheduled", widgets != null && widgets.progressTickScheduled());
            data.put("likeSelected", widgets != null && widgets.isLikeSelected());
            data.put("dislikeSelected", widgets != null && widgets.isDislikeSelected());
            data.put("likeAvailable", widgets != null && widgets.hasLike());
            data.put("dislikeAvailable", widgets != null && widgets.hasDislike());
            data.put("ambientLightAvailable", ambientLight!=null && ambientLight.isAvailable());
            data.put("pocketSensorAvailable", pocket!=null && pocket.isAvailable());
            data.put("pocketSensorListening", pocket!=null && pocket.isListening());
            data.put("pocketCovered", pocketed);
            data.put("pocketLightCovered", pocket!=null && pocket.isLightCovered());
            data.put("pocketLightFallbackAvailable", pocket!=null && pocket.isLightFallbackAvailable());
            data.put("pocketLux", pocket==null || !Float.isFinite(pocket.lux())?JSONObject.NULL:pocket.lux());
            data.put("ambientLightListening", ambientLight!=null && ambientLight.isListening());
            data.put("ambientLux", ambientLight==null || !Float.isFinite(ambientLight.lux())?JSONObject.NULL:ambientLight.lux());
            data.put("notificationApps", MusicSessionService.notificationIcons(false).appCount());
            data.put("ambientNotificationApps", MusicSessionService.notificationIcons(true).appCount());
            data.put("visualProgress", player == null ? 0 : player.progress());
            data.put("clockText", player==null?"":player.clockText());
            data.put("batteryPercent", player==null?-1:player.batteryPercent());
            data.put("charging", player!=null && player.isCharging());
            data.put("audioOutput",player==null?"":player.audioOutputName());
            data.put("accessoryBatteryDisplayed",widgets!=null && widgets.accessoryOpen() && player!=null && player.accessoryReading()!=null);
            data.put("audioRouteListening",player!=null && player.audioRouteListening());
            data.put("contentTransitionRunning", player != null && player.isContentTransitionRunning());
            data.put("visualTransitionRunning", player != null && player.isTransitionRunning());
            data.put("frostReady", player != null && player.isBackdropReady());
            data.put("frostTransitionRunning", player != null && player.isBackdropTransitionRunning());
            data.put("frostProgress", player == null ? 1 : player.backdropProgress());
            data.put("offsetXPx", player == null ? 0 : player.offsetX());
            data.put("contentSafeTopPx",player==null?0:player.contentSafeTop());
            data.put("contentSafeBottomPx",player==null?0:player.contentSafeBottom());
            data.put("contentOffsetYPx", player == null ? 0 : player.contentOffsetY());
            data.put("visibilityKind",MusicSessionService.presenceKind().name());
            data.put("visibilityClosePending",handler.hasCallbacks(closePaused));
            data.put("musicLaunchPending",pendingMusicLaunch!=null);
            data.put("offsetYPx", player == null ? 0 : player.offsetY());
            VisibilityJournal.record(this,data);
            Log.i("AmbientProbe", data.toString());
        } catch (Exception e) { Log.w("AmbientProbe", "State unavailable", e); }
    }
    @Override public void onResume() {
        super.onResume();
        hideSystemBars();
        resumed=true;
        closeIfUnlocked();
        updatePlaybackVisibility();
        updateDrivingMonitoring();
        if (widgets != null) widgets.refreshSession();
        updateAmbientLight();
        updatePocketMonitoring();
        logState("resumed");
        if (player != null && !isFinishing()) player.refreshMedia();
        PlayerContinuityService.revealWhenDrawn(this,playerRoot);
    }
    @Override public void onConfigurationChanged(android.content.res.Configuration configuration) {
        super.onConfigurationChanged(configuration);
        hideSystemBars();
        if(widgets!=null)widgets.requestLayout();
        if(playerRoot!=null)playerRoot.post(this::prepareContinuity);
    }
    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus){hideSystemBars();closeIfUnlocked();prepareContinuity();PlayerContinuityService.revealWhenDrawn(this,playerRoot);}
        else if(entrance!=null && entrance.running())entrance.finish();
        updateAmbientLight();
        updatePocketMonitoring();
        logState("window_focus_changed");
    }
    private void hideSystemBars(){
        if(isFinishing() || isDestroyed() || modes.mode()==PlayerModeController.Mode.AUTHENTICATING)return;
        WindowInsetsController controller=getWindow().getInsetsController();
        if(controller!=null){
            controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            controller.hide(WindowInsets.Type.systemBars());
        }
    }
    @Override public void onPause() { PlayerContinuityService.cover(this,playerRoot); if(entrance!=null && entrance.running())entrance.finish(); resumed=false; updateDrivingMonitoring(); if (ambientLight!=null) ambientLight.stop(); updatePocketMonitoring(); logState("paused"); super.onPause(); }
    @Override public void onDestroy() {
        PlayerContinuityService.dismiss(this);
        if(entrance!=null)entrance.release();
        pendingMusicLaunch=null;pendingNotificationLaunch=null;
        if (carBluetooth!=null) carBluetooth.stop();
        if (ambientLight!=null) ambientLight.stop();
        if (pocket!=null) pocket.stop();
        handler.removeCallbacksAndMessages(null);
        if (displayListenerRegistered) getSystemService(DisplayManager.class).unregisterDisplayListener(displayListener);
        MusicSessionService.removeListener(musicListener);
        MusicSessionService.removeIconListener(notificationListener);
        PlayerPreferences.store(this).unregisterOnSharedPreferenceChangeListener(preferenceListener);
        if (receiverRegistered) unregisterReceiver(lockReceiver);
        if (surface.get() == this) {
            surface.clear();
            if (!closingNotified && !isChangingConfigurations()) MusicSessionService.surfaceClosed("surface_destroyed");
        }
        super.onDestroy();
    }
}
