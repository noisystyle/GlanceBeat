package it.noxia.ambientplayer;

import android.content.Context;
import android.util.AtomicFile;
import org.json.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/** Small local event ring: no song, message, app, route, or notification contents. */
final class VisibilityJournal {
    private static final Set<String> EVENTS=Set.of("created","paused","resumed","window_focus_changed","screen_off","music_stopped","music_access_removed","keyguard_gone","system_unlocked","unlock_requested","unlock_cancelled","unlock_succeeded","player_disabled","pocket_covered","pocket_clear","ambient_brightness_changed");
    private static final ThreadPoolExecutor WORKER=new ThreadPoolExecutor(0,1,5,TimeUnit.SECONDS,new ArrayBlockingQueue<>(32),new ThreadPoolExecutor.DiscardOldestPolicy());
    private static final ArrayDeque<JSONObject> rows=new ArrayDeque<>();
    private static boolean loaded;
    private static String presence;
    static void record(Context context,JSONObject state){
        String event=state.optString("event"),kind=state.optString("visibilityKind");
        boolean changed=!Objects.equals(presence,kind);presence=kind;
        if(!EVENTS.contains(event) && !(event.equals("session_changed") && changed))return;
        JSONObject row=new JSONObject();
        try{
            row.put("timeMs",System.currentTimeMillis());row.put("uptimeMs",android.os.SystemClock.uptimeMillis());
            for(String key:List.of("event","mode","locked","interactive","displayState","brightnessOverride","pocketCovered","pocketSensorListening","ambientLightListening","visibilityKind","visibilityClosePending"))
                if(state.has(key))row.put(key,state.get(key));
            row.put("windowFocus",context instanceof android.app.Activity && ((android.app.Activity)context).hasWindowFocus());
        }catch(JSONException ignored){return;}
        File path=new File(context.getFilesDir(),"visibility-history.json");
        WORKER.execute(()->save(path,row));
    }
    private static void save(File path,JSONObject row){
        AtomicFile file=new AtomicFile(path);
        if(!loaded){loaded=true;try{if(path.length()>65536)throw new IOException("oversize");JSONArray old=new JSONArray(new String(file.readFully(),java.nio.charset.StandardCharsets.UTF_8));for(int i=Math.max(0,old.length()-95);i<old.length();i++)rows.add(old.getJSONObject(i));}catch(Exception ignored){}}
        rows.add(row);while(rows.size()>96)rows.removeFirst();
        FileOutputStream stream=null;
        try{stream=file.startWrite();stream.write(new JSONArray(rows).toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));file.finishWrite(stream);}
        catch(Exception ignored){if(stream!=null)file.failWrite(stream);}
    }
    private VisibilityJournal(){}
}
