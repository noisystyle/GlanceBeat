package it.noxia.ambientplayer;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.view.animation.PathInterpolator;

/** One finite compositor fade, including the black background; opaque again at rest. */
final class WindowEntrance {
    static final long DURATION_MS=550;
    private final Activity activity;
    private final Runnable settled;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private View root;
    private ValueAnimator animator;
    private boolean pending=true,opaque,released;
    private final Runnable fallback=this::finish;
    private final Runnable start=this::start;
    private final ViewTreeObserver.OnPreDrawListener ready=()->{
        removeReady();handler.post(start);return true;
    };
    WindowEntrance(Activity activity,Runnable settled){
        this.activity=activity;this.settled=settled;
        alpha(0);
    }
    void attach(View root){
        this.root=root;
        root.getViewTreeObserver().addOnPreDrawListener(ready);
        // A canceled launch or screen transition cannot leave an invisible window indefinitely.
        handler.postDelayed(fallback,1500);
    }
    boolean pending(){return pending;}
    boolean running(){return animator!=null && animator.isRunning();}
    boolean opaque(){return opaque;}
    private void removeReady(){
        if(root!=null && root.getViewTreeObserver().isAlive())root.getViewTreeObserver().removeOnPreDrawListener(ready);
    }
    private void alpha(float value){
        WindowManager.LayoutParams p=activity.getWindow().getAttributes();
        p.alpha=value;activity.getWindow().setAttributes(p);
    }
    private void start(){
        if(!pending || released || activity.isFinishing() || activity.isDestroyed())return;
        handler.removeCallbacks(fallback);
        if(!ValueAnimator.areAnimatorsEnabled()){finish();return;}
        animator=ValueAnimator.ofFloat(0,1);
        animator.setDuration(DURATION_MS);
        animator.setInterpolator(new PathInterpolator(.2f,0,.2f,1));
        animator.addUpdateListener(a->alpha((float)a.getAnimatedValue()));
        animator.addListener(new AnimatorListenerAdapter(){
            @Override public void onAnimationEnd(Animator a){if(animator==a){animator=null;finish();}}
        });
        Log.i("AmbientEntrance","started duration="+DURATION_MS);
        animator.start();
    }
    void finish(){
        if(!pending || released)return;
        pending=false;removeReady();handler.removeCallbacksAndMessages(null);
        ValueAnimator old=animator;animator=null;if(old!=null)old.cancel();
        alpha(1);
        if(!activity.isFinishing() && !activity.isDestroyed()){
            activity.getWindow().setBackgroundDrawable(new ColorDrawable(Color.BLACK));
            opaque=activity.setTranslucent(false);
            Log.i("AmbientEntrance","settled opaque="+opaque);
            settled.run();
        }
    }
    void release(){
        if(released)return;
        released=true;pending=false;removeReady();handler.removeCallbacksAndMessages(null);
        ValueAnimator old=animator;animator=null;if(old!=null)old.cancel();root=null;
    }
}
