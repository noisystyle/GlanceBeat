package it.noxia.ambientplayer;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Visible queue thumbnails only. Bounded decoding/cache, cancelable work, no AOD polling. */
final class QueueArtwork {
    interface Ready { void accept(Bitmap bitmap,boolean cached); }
    final class Ticket {
        volatile boolean canceled;
        volatile HttpURLConnection connection;
        Future<?> future;
        void cancel() {
            canceled=true;
            if(future!=null){future.cancel(true);if(future instanceof Runnable)worker.remove((Runnable)future);}
            HttpURLConnection open=connection;if(open!=null)open.disconnect();tickets.remove(this);
        }
    }
    private final Context context;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ThreadPoolExecutor worker=new ThreadPoolExecutor(2,2,10,TimeUnit.SECONDS,new LinkedBlockingQueue<>());
    private final Set<Ticket> tickets=new HashSet<>();
    private final LruCache<String,Bitmap> cache=new LruCache<>(2*1024*1024) {
        @Override protected int sizeOf(String key,Bitmap bitmap){return bitmap.getAllocationByteCount();}
    };
    private boolean visible;
    QueueArtwork(Context context){this.context=context.getApplicationContext();worker.allowCoreThreadTimeOut(true);}
    void resume(){visible=true;}
    int cachedCount(){return cache.snapshot().size();}
    int pendingCount(){return tickets.size();}
    void pause(){visible=false;for(Ticket ticket:Set.copyOf(tickets))ticket.cancel();main.removeCallbacksAndMessages(null);}
    Ticket load(Uri uri,Ready ready) {
        if(!visible || uri==null)return null;
        String key=uri.toString();Bitmap hit=cache.get(key);
        if(hit!=null){ready.accept(hit,true);return null;}
        Ticket ticket=new Ticket();tickets.add(ticket);
        ticket.future=worker.submit(()->{
            Bitmap result=null;
            try {
                byte[] data=read(uri,ticket);
                if(data!=null && !ticket.canceled) {
                    BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;
                    BitmapFactory.decodeByteArray(data,0,data.length,bounds);
                    if(bounds.outWidth>0 && bounds.outHeight>0) {
                        BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=1;
                        while(Math.max(bounds.outWidth,bounds.outHeight)/options.inSampleSize>320)options.inSampleSize*=2;
                        options.inPreferredConfig=Bitmap.Config.RGB_565;
                        result=BitmapFactory.decodeByteArray(data,0,data.length,options);
                    }
                }
            } catch(Exception unavailable) {
                android.util.Log.d("AmbientArtwork","thumbnail_unavailable type="+unavailable.getClass().getSimpleName());
            }
            finally {if(ticket.connection!=null)ticket.connection.disconnect();ticket.connection=null;}
            Bitmap bitmap=result;
            if(ticket.canceled)return;
            main.post(()->{
                tickets.remove(ticket);
                if(!visible || ticket.canceled)return;
                if(bitmap!=null)cache.put(key,bitmap);
                ready.accept(bitmap,false);
            });
        });return ticket;
    }
    @android.annotation.SuppressLint("Recycle") // Both stream branches are closed by try(input), including size-limit exits.
    private byte[] read(Uri uri,Ticket ticket) throws Exception {
        InputStream input;
        if("content".equals(uri.getScheme()))input=context.getContentResolver().openInputStream(uri);
        else if("https".equals(uri.getScheme())) {
            URL url=new URL(uri.toString());HttpURLConnection connection=null;
            for(int redirects=0;redirects<4;redirects++) {
                if(ticket.canceled || !"https".equals(url.getProtocol()))return null;
                connection=(HttpURLConnection)url.openConnection();ticket.connection=connection;
                connection.setConnectTimeout(2000);connection.setReadTimeout(3000);connection.setInstanceFollowRedirects(false);
                int status=connection.getResponseCode();
                if(status>=300 && status<400) {
                    String location=connection.getHeaderField("Location");connection.disconnect();
                    if(location==null)return null;url=new URL(url,location);continue;
                }
                if(status!=200 || connection.getContentLengthLong()>2*1024*1024){connection.disconnect();return null;}
                break;
            }
            if(connection==null)return null;input=connection.getInputStream();
        } else return null;
        if(input==null)return null;
        try(input;ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            byte[] buffer=new byte[8192];int length;
            while(!ticket.canceled && (length=input.read(buffer))!=-1) {
                if(out.size()+length>2*1024*1024)return null;out.write(buffer,0,length);
            }
            return ticket.canceled?null:out.toByteArray();
        } finally {if(ticket.connection!=null)ticket.connection.disconnect();ticket.connection=null;}
    }
}
