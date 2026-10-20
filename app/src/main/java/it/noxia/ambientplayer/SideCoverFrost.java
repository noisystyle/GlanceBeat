package it.noxia.ambientplayer;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;

/** Finite artwork preparation: the outer edge becomes deeply frosted, without a frame-time blur. */
final class SideCoverFrost {
    static Bitmap create(Bitmap artwork,boolean left) {
        return create(artwork,left,0);
    }
    static Bitmap deep(Bitmap artwork,boolean left) {return create(artwork,left,40);}
    private static Bitmap create(Bitmap artwork,boolean left,int extraBlur) {
        final int size=256,stride=size+1;
        Bitmap readable=artwork.getConfig()==Bitmap.Config.HARDWARE?artwork.copy(Bitmap.Config.ARGB_8888,false):artwork;
        if(readable==null)return null;
        Bitmap result=Bitmap.createBitmap(size,size,Bitmap.Config.ARGB_8888);
        new Canvas(result).drawBitmap(readable,null,new Rect(0,0,size,size),new Paint(Paint.FILTER_BITMAP_FLAG));
        if(readable!=artwork)readable.recycle();
        int[] pixels=new int[size*size],next=new int[pixels.length];
        int[][] sums=new int[3][stride*stride];
        result.getPixels(pixels,0,size,0,0,size,size);
        for(int pass=0;pass<3;pass++) {
            if(Thread.currentThread().isInterrupted()){result.recycle();return null;}
            for(int channel=0;channel<3;channel++) {
                int[] table=sums[channel];java.util.Arrays.fill(table,0);int shift=(2-channel)*8;
                for(int y=0;y<size;y++) {
                    int row=0;
                    for(int x=0;x<size;x++) {row+=(pixels[y*size+x]>>shift)&255;table[(y+1)*stride+x+1]=table[y*stride+x+1]+row;}
                }
            }
            for(int x=0;x<size;x++) {
                float outward=(left?size-1-x:x)/(size*.62f);
                float frost=Math.min(1,outward);int radius=3+extraBlur+Math.round(32*frost*frost);
                int x0=Math.max(0,x-radius),x1=Math.min(size,x+radius+1);
                for(int y=0;y<size;y++) {
                    int y0=Math.max(0,y-radius),y1=Math.min(size,y+radius+1),count=(x1-x0)*(y1-y0),color=0xff000000;
                    for(int channel=0;channel<3;channel++) {
                        int[] table=sums[channel];int sum=table[y1*stride+x1]-table[y0*stride+x1]-table[y1*stride+x0]+table[y0*stride+x0];
                        color|=(sum/count)<<((2-channel)*8);
                    }
                    next[y*size+x]=color;
                }
            }
            int[] swap=pixels;pixels=next;next=swap;
        }
        // Feather the rounded silhouette itself; a sharp clipping path would undo this softness.
        for(int y=0;y<size;y++)for(int x=0;x<size;x++) {
            float qx=Math.abs(x+.5f-size/2f)-(size/2f-60),qy=Math.abs(y+.5f-size/2f)-(size/2f-60);
            float outside=(float)Math.hypot(Math.max(0,qx),Math.max(0,qy))+Math.min(Math.max(qx,qy),0)-60;
            float edge=Math.max(0,Math.min(1,-outside/24));edge=edge*edge*(3-2*edge);
            pixels[y*size+x]=(pixels[y*size+x]&0xffffff)|(Math.round(255*edge)<<24);
        }
        result.setPixels(pixels,0,size,0,0,size,size);return result;
    }
    private SideCoverFrost() { }
}
