package it.noxia.ambientplayer;

import android.accessibilityservice.AccessibilityService;
import android.app.KeyguardManager;
import android.content.Intent;
import android.graphics.*;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.view.*;
import android.view.accessibility.AccessibilityEvent;
import android.widget.ImageView;
import java.lang.ref.WeakReference;

/** Optional trusted continuity window. Never retrieves screen contents or controls other apps. */
public final class PlayerContinuityService extends AccessibilityService {
    private static WeakReference<PlayerContinuityService> binding=new WeakReference<>(null);
    private static PlayerContinuityService instance(){return binding.get();}
    private final Handler handler=new Handler(Looper.getMainLooper());
    private WeakReference<MainActivity> owner=new WeakReference<>(null);
    private WindowManager manager;
    private ImageView image;
    private Bitmap bitmap;
    private WindowManager.LayoutParams params;
    private boolean shown,returning,blocked;
    static boolean connected(){return instance()!=null;}
    static boolean covering(MainActivity activity){PlayerContinuityService service=instance();return service!=null && service.owner.get()==activity && service.shown;}
    @Override protected void onServiceConnected(){
        binding=new WeakReference<>(this);manager=getSystemService(WindowManager.class);
        MainActivity activity=MainActivity.currentSurface();if(activity!=null)activity.prepareContinuity();
    }
    @Override public void onAccessibilityEvent(AccessibilityEvent event){
        if(!shown)return;
        String pkg=event.getPackageName()==null?"":event.getPackageName().toString();
        String type=event.getClassName()==null?"":event.getClassName().toString();
        // A call, system dialog or credential prompt always takes precedence.
        if(pkg.equals("android") || pkg.contains("permissioncontroller") || pkg.contains("incallui")
                || pkg.endsWith(".dialer") || pkg.equals("com.android.systemui") && (type.contains("Dialog") || type.contains("GlobalActions")) || type.contains("Biometric") || type.contains("Bouncer")){
            blocked=true;hide();
        }
    }
    @Override public void onInterrupt(){clear();}
    @Override public void onDestroy(){clear();if(instance()==this)binding.clear();super.onDestroy();}
    static void prepare(MainActivity activity,View root){PlayerContinuityService service=instance();if(service!=null)service.prepareWindow(activity,root);}
    static void cover(MainActivity activity,View root){
        PlayerContinuityService service=instance();
        if(service==null || !activity.continuityEligible() || service.callActive())return;
        service.prepareWindow(activity,root);
        if(service.image==null || service.owner.get()!=activity || service.blocked)return;
        service.capture(root);service.shown=true;service.returning=false;
        service.params.alpha=1;service.params.flags&=~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        service.params.screenBrightness=activity.getWindow().getAttributes().screenBrightness;
        service.manager.updateViewLayout(service.image,service.params);
        android.util.Log.i("AmbientContinuity","covered_interruption");
    }
    static void refresh(MainActivity activity,View root){PlayerContinuityService service=instance();if(service!=null && service.owner.get()==activity && service.shown)service.capture(root);}
    static void revealWhenDrawn(MainActivity activity,View root){
        PlayerContinuityService service=instance();
        if(service==null || root==null || service.owner.get()!=activity || !service.shown || service.returning || !activity.hasWindowFocus())return;
        service.returning=true;
        root.getViewTreeObserver().registerFrameCommitCallback(()->service.handler.post(()->{
            if(service.owner.get()==activity && activity.hasWindowFocus())service.hide();
            service.returning=false;
        }));
        root.invalidate();
    }
    static void dismiss(MainActivity activity){PlayerContinuityService service=instance();if(service!=null && service.owner.get()==activity)service.clear();}
    private boolean callActive(){int mode=getSystemService(AudioManager.class).getMode();return mode!=AudioManager.MODE_NORMAL;}
    private void prepareWindow(MainActivity activity,View root){
        if(!activity.continuityEligible() || root==null || root.getWidth()==0 || root.getHeight()==0)return;
        if(owner.get()!=activity){clear();owner=new WeakReference<>(activity);}
        if(activity.hasWindowFocus())blocked=false;
        if(image!=null && bitmap.getWidth()==root.getWidth() && bitmap.getHeight()==root.getHeight())return;
        clearWindow();owner=new WeakReference<>(activity);blocked=false;
        bitmap=Bitmap.createBitmap(root.getWidth(),root.getHeight(),Bitmap.Config.ARGB_8888);
        image=new androidx.appcompat.widget.AppCompatImageView(new android.view.ContextThemeWrapper(this,R.style.PlayerTheme)){
            @Override public boolean onTouchEvent(MotionEvent event){if(event.getActionMasked()==MotionEvent.ACTION_UP)performClick();return true;}
            @Override public boolean performClick(){super.performClick();return true;}
        };image.setScaleType(ImageView.ScaleType.FIT_XY);capture(root);
        image.setImageBitmap(bitmap);image.setContentDescription(getString(R.string.continuity_player_description));
        image.setOnClickListener(view->{MainActivity host=owner.get();if(host!=null && host.continuityEligible()){
            Intent intent=new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            host.activateFromContinuity();startActivity(intent);
        }});
        int flags=WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            |WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN|WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;
        params=new WindowManager.LayoutParams(-1,-1,WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,flags,PixelFormat.TRANSLUCENT);
        params.layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        params.alpha=0;params.preferredRefreshRate=1;params.setTitle("GlanceBeat continuity");
        try{manager.addView(image,params);}catch(RuntimeException unavailable){clearWindow();}
    }
    private void capture(View root){
        if(bitmap==null || root.getWidth()!=bitmap.getWidth() || root.getHeight()!=bitmap.getHeight())return;
        Canvas canvas=new Canvas(bitmap);canvas.drawColor(Color.BLACK);root.draw(canvas);if(image!=null)image.invalidate();
    }
    private void hide(){
        if(image==null)return;
        shown=false;returning=false;params.alpha=0;params.flags|=WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        params.screenBrightness=WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
        try{manager.updateViewLayout(image,params);}catch(RuntimeException ignored){clearWindow();}
        android.util.Log.i("AmbientContinuity","surface_visible_again");
    }
    private void clearWindow(){
        if(image!=null){try{manager.removeViewImmediate(image);}catch(RuntimeException ignored){}image.setImageDrawable(null);}
        image=null;bitmap=null;params=null;shown=false;returning=false;
    }
    private void clear(){handler.removeCallbacksAndMessages(null);clearWindow();owner.clear();blocked=false;}
}
