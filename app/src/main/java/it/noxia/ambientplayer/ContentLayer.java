package it.noxia.ambientplayer;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.view.animation.PathInterpolator;

/** Owns small raster layers so an interrupted transition can retain its exact visible blend. */
final class ContentLayer {
    private final int width,height;
    private final boolean opaque;
    private final Runnable redraw,settled;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private final RectF bounds=new RectF();
    private Bitmap current,previous;
    private ValueAnimator animation;
    private float progress=1,travel;
    private boolean revealOpacity,horizontal;
    private Integer lastTint;
    ContentLayer(int width,int height,boolean opaque,Runnable redraw,Runnable settled) {
        this.width=width;this.height=height;this.opaque=opaque;this.redraw=redraw;this.settled=settled;
    }
    Bitmap snapshot() {
        Bitmap visible=Bitmap.createBitmap(width*3,height*3,Bitmap.Config.ARGB_8888);
        Canvas canvas=new Canvas(visible);canvas.scale(3,3);draw(canvas,null,1);return visible;
    }
    boolean running() { return animation!=null; }
    void replace(Bitmap next,boolean animate,float distance,long delay) {replace(next,animate,distance,delay,false);}
    void slide(Bitmap next,boolean animate,float distance){replace(next,animate,distance,0,true);}
    private void replace(Bitmap next,boolean animate,float distance,long delay,boolean sideways) {
        Bitmap visible=current;
        if (running()) {
            visible=Bitmap.createBitmap(width*3,height*3,Bitmap.Config.ARGB_8888);
            Canvas snapshot=new Canvas(visible);snapshot.scale(3,3);
            draw(snapshot,null,1);
            android.util.Log.i("AmbientContent","retarget_from_visible_blend");
        }
        cancel();
        recycle(previous,visible);recycle(current,visible);
        previous=visible;current=next;progress=0;travel=distance;revealOpacity=false;horizontal=sideways;
        if (!animate || previous==null) { finish();redraw.run();return; }
        startAnimation(sideways?560:distance==0?420:620,delay);
    }
    /** A new neighbor starts transparent and deeply blurred, then resolves to its usual frost. */
    void revealFrom(Bitmap blurred,Bitmap next,boolean animate) {
        cancel();recycle(previous,null);recycle(current,null);
        previous=blurred;current=next;progress=0;travel=0;revealOpacity=true;horizontal=false;
        if(!animate || previous==null){finish();redraw.run();return;}
        startAnimation(760,0);
    }
    private void startAnimation(long duration,long delay) {
        animation=ValueAnimator.ofFloat(0,1);
        animation.setStartDelay(delay);
        animation.setDuration(duration);
        animation.setInterpolator(horizontal?new PathInterpolator(.35f,0,.15f,1):new PathInterpolator(.2f,0f,0f,1f));
        animation.addUpdateListener(value -> { progress=(float)value.getAnimatedValue();redraw.run(); });
        animation.addListener(new AnimatorListenerAdapter() {
            private boolean cancelled;
            @Override public void onAnimationCancel(Animator value) { cancelled=true; }
            @Override public void onAnimationEnd(Animator value) {
                if (animation==value) animation=null;
                if (!cancelled) {
                    recycle(previous,current);previous=null;progress=1;
                    android.util.Log.i("AmbientContent","layer_finished");settled.run();
                }
            }
        });
        android.util.Log.i("AmbientContent","layer_started motion="+travel+" delay="+delay);
        animation.start();settled.run();
    }
    void draw(Canvas canvas,Integer tint,float alpha) {
        if (!java.util.Objects.equals(tint,lastTint)) {
            lastTint=tint;
            paint.setColorFilter(tint==null?null:new PorterDuffColorFilter(tint,PorterDuff.Mode.SRC_IN));
        }
        if(revealOpacity)alpha*=progress;
        int layer=opaque && alpha<1?canvas.saveLayerAlpha(0,0,width,height,Math.round(255*alpha)):-1;
        if (layer>=0) alpha=1;
        // Opaque art dissolves over its old image; transparent text fades both layers.
        drawBitmap(canvas,previous,-travel*progress,alpha*(opaque && current!=null?1:1-progress));
        drawBitmap(canvas,current,travel*(1-progress),alpha*progress);
        if (layer>=0) canvas.restoreToCount(layer);
    }
    private void drawBitmap(Canvas canvas,Bitmap bitmap,float y,float alpha) {
        if (bitmap==null || bitmap.isRecycled() || alpha<=0) return;
        bounds.set(horizontal?y:0,horizontal?0:y,width+(horizontal?y:0),height+(horizontal?0:y));paint.setAlpha(Math.round(255*Math.min(1,alpha)));
        canvas.drawBitmap(bitmap,null,bounds,paint);
    }
    private void cancel() { if(animation!=null)animation.cancel();animation=null; }
    void finish() { cancel();recycle(previous,current);previous=null;progress=1;revealOpacity=false; }
    void release() { finish();recycle(current,null);current=null; }
    private static void recycle(Bitmap bitmap,Bitmap keep) {
        if(bitmap!=null && bitmap!=keep && !bitmap.isRecycled())bitmap.recycle();
    }
}
