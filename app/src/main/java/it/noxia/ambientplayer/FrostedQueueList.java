package it.noxia.ambientplayer;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RenderEffect;
import android.graphics.RuntimeShader;
import android.graphics.Shader;
import android.os.Build;
import androidx.recyclerview.widget.RecyclerView;

/** GPU frost at the viewport edges; the central list is sampled once and stays sharp. */
@android.annotation.SuppressLint("ViewConstructor") // Programmatic queue child.
final class FrostedQueueList extends RecyclerView {
    private static final String FROST="""
        uniform shader content;
        uniform float2 size;
        uniform float2 edges;
        uniform float radius;
        uniform float2 axis;
        uniform float finalPass;
        uniform float header;
        half4 tap(float2 p) { return content.eval(clamp(p,float2(0),size-float2(1))); }
        half4 main(float2 p) {
            float bottomFade=smoothstep(0.0,edges.y,size.y-p.y);
            float headerBlur=1.0-smoothstep(header-edges.x,header+edges.x,p.y);
            float strength=max(headerBlur,1.0-bottomFade);
            if(strength<=0.0) return content.eval(p);
            float fade=smoothstep(0.0,edges.x*0.5,p.y)*bottomFade;
            float2 step=axis*radius*strength*0.25;
            half4 color=tap(p)*0.13303901;
            color+=(tap(p+step*1.0)+tap(p-step*1.0))*0.12584951;
            color+=(tap(p+step*2.0)+tap(p-step*2.0))*0.10652931;
            color+=(tap(p+step*3.0)+tap(p-step*3.0))*0.08069224;
            color+=(tap(p+step*4.0)+tap(p-step*4.0))*0.05469397;
            color+=(tap(p+step*5.0)+tap(p-step*5.0))*0.03317357;
            color+=(tap(p+step*6.0)+tap(p-step*6.0))*0.01800487;
            color+=(tap(p+step*7.0)+tap(p-step*7.0))*0.00874446;
            color+=(tap(p+step*8.0)+tap(p-step*8.0))*0.00380033;
            color+=(tap(p+step*9.0)+tap(p-step*9.0))*0.00147793;
            color+=(tap(p+step*10.0)+tap(p-step*10.0))*0.00051432;
            if(finalPass<0.5) return color;
            color.rgb=mix(color.rgb,half3(0.13,0.15,0.18)*color.a,0.16*strength);
            return color*fade;
        }
        """;
    private final Paint mask=new Paint(Paint.ANTI_ALIAS_FLAG);
    private float scale=1;
    private float header;
    private boolean gpuFrost;
    FrostedQueueList(Context context){super(context);mask.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));}
    boolean frostEnabled(){return gpuFrost;}
    float headerExtent(){return header;}
    void setHeaderExtent(float value){if(Math.abs(value-header)>.5f){header=value;prepareEdges();}}
    void setSceneScale(float value){if(Math.abs(value-scale)>.01f){scale=value;prepareEdges();}}
    @Override protected void onSizeChanged(int w,int h,int oldW,int oldH){super.onSizeChanged(w,h,oldW,oldH);prepareEdges();}
    private void prepareEdges() {
        int width=getWidth(),height=getHeight();if(width<=0 || height<=0)return;
        float top=Math.min(24*scale,height*.2f),bottom=Math.min(52*scale,height*.25f);
        gpuFrost=false;
        if(Build.VERSION.SDK_INT>=33)try {
            RuntimeShader horizontal=new RuntimeShader(FROST),vertical=new RuntimeShader(FROST);
            for(RuntimeShader shader:new RuntimeShader[]{horizontal,vertical}) {
                shader.setFloatUniform("size",width,height);shader.setFloatUniform("edges",top,bottom);
                shader.setFloatUniform("radius",5*scale);shader.setFloatUniform("header",header);
            }
            horizontal.setFloatUniform("axis",1,0);horizontal.setFloatUniform("finalPass",0);
            vertical.setFloatUniform("axis",0,1);vertical.setFloatUniform("finalPass",1);
            setRenderEffect(RenderEffect.createChainEffect(RenderEffect.createRuntimeShaderEffect(vertical,"content"),
                RenderEffect.createRuntimeShaderEffect(horizontal,"content")));gpuFrost=true;
        } catch(IllegalArgumentException unavailable) {android.util.Log.w("AmbientQueue","edge_frost_unavailable");setRenderEffect(null);}
        mask.setShader(new LinearGradient(0,0,0,height,new int[]{Color.TRANSPARENT,Color.WHITE,Color.WHITE,Color.TRANSPARENT},
            new float[]{0,top*.5f/height,1-bottom/height,1},Shader.TileMode.CLAMP));
        invalidate();
    }
    @Override protected void dispatchDraw(Canvas canvas) {
        if(gpuFrost || mask.getShader()==null){super.dispatchDraw(canvas);return;}
        // Android 12 fallback keeps the same soft clipping without a runtime shader.
        int layer=canvas.saveLayer(0,0,getWidth(),getHeight(),null);
        super.dispatchDraw(canvas);canvas.drawRect(0,0,getWidth(),getHeight(),mask);canvas.restoreToCount(layer);
    }
}
