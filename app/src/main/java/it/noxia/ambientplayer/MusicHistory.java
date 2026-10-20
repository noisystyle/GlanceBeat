package it.noxia.ambientplayer;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.media.MediaMetadata;
import android.media.session.MediaSession;
import android.util.LruCache;
import java.util.LinkedHashMap;
import java.util.Objects;

/** Bounded links actually published by this session, including items later trimmed from its queue. */
final class MusicHistory {
    private static MediaSession.Token token;
    private record Link(MusicQueue.Entry previous,String title,String artist) { }
    private static final LinkedHashMap<QueueSelection.Item,Link> predecessors=new LinkedHashMap<>();
    private static final LruCache<String,Bitmap> pictures=new LruCache<>(2*1024*1024) {
        @Override protected int sizeOf(String key,Bitmap bitmap){return bitmap.getAllocationByteCount();}
    };
    static void observe(MusicQueue.Snapshot fresh) {
        if(!Objects.equals(token,fresh.token())){clear();token=fresh.token();}
        if(token==null)return;
        for(int i=1;i<fresh.entries().size();i++) {
            MusicQueue.Entry before=fresh.entries().get(i-1),current=fresh.entries().get(i);
            predecessors.put(current.key(),new Link(new MusicQueue.Entry(before.key(),before.title(),before.artist(),null,before.artworkUri()),current.title(),current.artist()));
        }
        while(predecessors.size()>64)predecessors.remove(predecessors.keySet().iterator().next());
        if(fresh.current()<0)return;
        MusicQueue.Entry entry=fresh.entries().get(fresh.current());
        String key=pictureKey(entry);if(pictures.get(key)!=null)return;
        MediaMetadata metadata=MusicSessionService.current==null?null:MusicSessionService.current.getMetadata();
        Bitmap art=entry.artwork();
        if(metadata!=null && Objects.equals(entry.title(),metadata.getString(MediaMetadata.METADATA_KEY_TITLE))) {
            Bitmap published=metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
            if(published==null)published=metadata.getBitmap(MediaMetadata.METADATA_KEY_ART);
            if(published!=null)art=published;
        }
        if(art!=null && !art.isRecycled()) {
            try {
                Bitmap readable=art.getConfig()==Bitmap.Config.HARDWARE?art.copy(Bitmap.Config.ARGB_8888,false):art;
                if(readable==null)return;
                Bitmap small=Bitmap.createBitmap(256,256,Bitmap.Config.ARGB_8888);
                new Canvas(small).drawBitmap(readable,null,new Rect(0,0,256,256),new Paint(Paint.FILTER_BITMAP_FLAG));
                if(readable!=art)readable.recycle();pictures.put(key,small);
            }catch(RuntimeException unavailable){android.util.Log.d("AmbientCovers","history_artwork_unavailable");}
        }
    }
    static MusicQueue.Entry previous(MusicQueue.Snapshot fresh) {
        if(!Objects.equals(token,fresh.token()) || fresh.current()<0)return null;
        if(fresh.current()>0)return fresh.entries().get(fresh.current()-1);
        MusicQueue.Entry current=fresh.entries().get(fresh.current());Link link=predecessors.get(current.key());
        if(link==null || !Objects.equals(current.title(),link.title()) || !Objects.equals(current.artist(),link.artist()))return null;
        MusicQueue.Entry entry=link.previous();
        return new MusicQueue.Entry(entry.key(),entry.title(),entry.artist(),pictures.get(pictureKey(entry)),entry.artworkUri());
    }
    private static String pictureKey(MusicQueue.Entry entry){return entry.key()+"\n"+entry.title()+"\n"+entry.artist();}
    static void clear(){token=null;predecessors.clear();pictures.evictAll();}
    private MusicHistory() { }
}
