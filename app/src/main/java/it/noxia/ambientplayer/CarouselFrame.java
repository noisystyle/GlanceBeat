package it.noxia.ambientplayer;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

/** Frozen raster cards survive metadata callbacks while the finger moves the whole row. */
final class CarouselFrame {
    record Card(Bitmap sharp,Bitmap left,Bitmap right) {
        void release(){recycle(sharp);recycle(left);recycle(right);}
    }
    private Card previous,next;
    private final Card current;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private final Path clip=new Path();
    private final RectF bounds=new RectF(-60,0,60,120);
    CarouselFrame(Card previous,Card current,Card next){this.previous=previous;this.current=current;this.next=next;}
    void draw(Canvas canvas,float top,float spacing,float travel,float fraction) {
        drawCard(canvas,previous,-spacing+travel,top,spacing,fraction);
        drawCard(canvas,current,travel,top,spacing,fraction);
        drawCard(canvas,next,spacing+travel,top,spacing,fraction);
    }
    private void drawCard(Canvas canvas,Card card,float center,float top,float spacing,float fraction) {
        if(card==null)return;
        float distance=Math.min(1,Math.abs(center)/spacing);
        float sharp=(float)Math.pow(1-distance,3);
        float alpha=(.55f+.45f*fraction)*(1-distance)+(.25f+.63f*fraction)*distance;
        canvas.save();canvas.translate(center,top);
        Bitmap frost=center<0?card.left:card.right;
        if(frost!=null) {paint.setAlpha(Math.round(255*alpha*(1-sharp)));canvas.drawBitmap(frost,null,bounds,paint);}
        else sharp=Math.max(sharp,.38f);
        if(card.sharp!=null && sharp>0) {
            canvas.save();clip.reset();clip.addRoundRect(bounds,28,28,Path.Direction.CW);canvas.clipPath(clip);
            paint.setAlpha(Math.round(255*alpha*sharp));canvas.drawBitmap(card.sharp,null,bounds,paint);canvas.restore();
        }
        canvas.restore();
    }
    boolean hasDestination(int direction){return (direction>0?previous:next)!=null;}
    void replaceDestination(int direction,Card card){if(direction>0){if(previous!=null)previous.release();previous=card;}else{if(next!=null)next.release();next=card;}}
    void ensureDestination(int direction,Card fallback){if(direction>0){if(previous==null)previous=fallback;else fallback.release();}else{if(next==null)next=fallback;else fallback.release();}}
    Bitmap destinationCopy(int direction){Card target=direction>0?previous:next;return target==null?null:copy(target.sharp);}
    void release(){if(previous!=null)previous.release();if(current!=null)current.release();if(next!=null)next.release();}
    static Bitmap copy(Bitmap value){return value==null || value.isRecycled()?null:value.copy(Bitmap.Config.ARGB_8888,false);}
    private static void recycle(Bitmap value){if(value!=null && !value.isRecycled())value.recycle();}
}
