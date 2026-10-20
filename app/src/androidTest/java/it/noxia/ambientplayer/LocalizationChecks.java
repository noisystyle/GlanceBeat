package it.noxia.ambientplayer;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Build;
import android.os.Bundle;
import android.os.LocaleList;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

/** Renders actual widgets with both locales and checks fallback, plurals and accessibility. */
final class LocalizationChecks {
    private final Instrumentation host;
    private final JSONArray checks=new JSONArray();
    private LocalizationChecks(Instrumentation host){this.host=host;}
    static void run(Instrumentation host){new LocalizationChecks(host).run();}
    private void check(String name,boolean ok)throws Exception {
        if(!ok)throw new AssertionError(name);
        checks.put(new JSONObject().put("name",name).put("passed",true));
    }
    static List<String> texts(View view){
        List<String> result=new ArrayList<>();
        if(view instanceof TextView text)result.add(text.getText().toString());
        if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++)result.addAll(texts(group.getChildAt(i)));
        return result;
    }
    private Context localized(Context activity,String language){
        Configuration config=new Configuration(activity.getResources().getConfiguration());
        config.setLocales(LocaleList.forLanguageTags(language));
        return new ContextThemeWrapper(activity.createConfigurationContext(config),R.style.AppTheme);
    }
    private void save(View view,String name)throws Exception {
        Bitmap[] image={null};host.runOnMainSync(()->{image[0]=Bitmap.createBitmap(view.getWidth(),view.getHeight(),Bitmap.Config.ARGB_8888);view.draw(new Canvas(image[0]));});
        try(var out=new FileOutputStream(new File(host.getTargetContext().getFilesDir(),name))){image[0].compress(Bitmap.CompressFormat.PNG,100,out);}
        image[0].recycle();
    }
    private void run(){
        Bundle result=new Bundle();PreviewActivity activity=null;
        try {
            if(!Build.FINGERPRINT.contains("generic") && !Build.MODEL.contains("sdk"))throw new AssertionError("Emulator only");
            activity=(PreviewActivity)host.startActivitySync(new Intent(host.getTargetContext(),PreviewActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            PreviewActivity preview=activity;
            Thread.sleep(450);
            check("preview_hides_status_bar",!preview.getWindow().getDecorView().getRootWindowInsets().isVisible(android.view.WindowInsets.Type.statusBars()));
            check("preview_hides_navigation_bar",!preview.getWindow().getDecorView().getRootWindowInsets().isVisible(android.view.WindowInsets.Type.navigationBars()));
            for(String language:List.of("en","it","fr-FR")){
                boolean it=language.equals("it");Context context=localized(activity,language);String prefix=language+"_";
                View[] settings={null};PlayerView[] player={null};QueuePanel[] queue={null};
                host.runOnMainSync(()->{settings[0]=LayoutInflater.from(context).inflate(R.layout.settings,null);preview.setContentView(settings[0]);});
                Thread.sleep(250);
                check(prefix+"settings_button_is_translated",texts(settings[0]).contains(it?"Prova il player":"Preview player"));
                check(prefix+"unlock_help_mentions_both_gestures",context.getString(R.string.unlock_body).contains(it?"scorri verso l’alto":"swipe up") && context.getString(R.string.unlock_body).contains(it?"tocca la freccia":"tap the upward arrow"));
                check(prefix+"support_button_is_translated",texts(settings[0]).contains(it?"Offrimi un caffè":"Buy me a coffee"));
                check(prefix+"settings_music_heading_is_translated",texts(settings[0]).contains(it?"App musicali":"Music apps"));
                check(prefix+"service_label_is_translated",context.getString(R.string.continuity_title).equals(it?"GlanceBeat · Continuità":"GlanceBeat · Continuity"));
                for(int amount:new int[]{0,1,2}){
                    String expected=it?amount+(amount==1?" brano a seguire":" brani a seguire"):amount+(amount==1?" track up next":" tracks up next");
                    check(prefix+"queue_plural_"+amount,UiText.quantity(context,R.plurals.queue_following,amount).equals(expected));
                }
                check(prefix+"formatted_battery_accessibility",context.getString(R.string.phone_battery_description,42).equals(it?"Batteria 42 per cento":"Battery 42 percent"));
                check(prefix+"unknown_provider_name_is_localized",MusicSources.name(context,null).equals(it?"L’app musicale":"The music app"));
                var untitled=new MusicQueue.Entry(new QueueSelection.Item(1,null),"","",null,null);
                check(prefix+"untitled_track_is_localized",untitled.title(context).equals(it?"Brano senza titolo":"Untitled track"));
                var named=new MusicQueue.Entry(new QueueSelection.Item(2,null),"Titolo originale","Artista",null,null);
                check(prefix+"provider_metadata_is_preserved",named.title(context).equals("Titolo originale"));
                if(!language.startsWith("fr")){
                    save(settings[0],"localization-"+language+"-settings.png");
                    host.runOnMainSync(()->((androidx.core.widget.NestedScrollView)settings[0]).fullScroll(View.FOCUS_DOWN));
                    Thread.sleep(200);save(settings[0],"localization-"+language+"-support.png");
                }
                host.runOnMainSync(()->{queue[0]=new QueuePanel(context,0xff9cddca,()->{},()->{},()->{});queue[0].setVisibility(View.VISIBLE);preview.setContentView(queue[0]);});
                Thread.sleep(150);
                check(prefix+"queue_heading_is_translated",texts(queue[0]).contains(it?"Coda":"Queue"));
                check(prefix+"queue_empty_state_is_translated",texts(queue[0]).contains(it?"La coda non è disponibile.":"The queue is unavailable."));
                host.runOnMainSync(()->{player[0]=new PlayerView(context);preview.setContentView(player[0]);player[0].setActive(true);});
                Thread.sleep(450);
                check(prefix+"player_talkback_is_translated",player[0].getContentDescription().toString().startsWith(it?"Player musicale attivo.":"Music player active."));
                AccessibilityNodeInfo[] info={null};host.runOnMainSync(()->info[0]=player[0].createAccessibilityNodeInfo());
                List<String> labels=info[0].getActionList().stream().map(x->String.valueOf(x.getLabel())).toList();
                check(prefix+"queue_accessibility_action_is_translated",labels.contains(it?"Mostra la coda":"Show queue"));
                check(prefix+"unlock_accessibility_action_is_translated",labels.contains(it?"Sblocca":"Unlock"));
                boolean[] optional={false};host.runOnMainSync(()->optional[0]=AccessoryLocaleChecks.verify(context,it));
                check(prefix+"edition_accessory_localization",optional[0]);
            }
            try(var fd=host.getUiAutomation().executeShellCommand("cmd appops set "+host.getTargetContext().getPackageName()+" SYSTEM_ALERT_WINDOW allow");var in=new java.io.FileInputStream(fd.getFileDescriptor())){in.readAllBytes();}
            try(var fd=host.getUiAutomation().executeShellCommand("cmd notification allow_listener "+host.getTargetContext().getPackageName()+"/it.noxia.ambientplayer.MusicSessionService");var in=new java.io.FileInputStream(fd.getFileDescriptor())){in.readAllBytes();}
            SettingsActivity settings=(SettingsActivity)host.startActivitySync(new Intent(host.getTargetContext(),SettingsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            Thread.sleep(400);
            check("settings_restore_status_bar_after_preview",settings.getWindow().getDecorView().getRootWindowInsets().isVisible(android.view.WindowInsets.Type.statusBars()));
            Intent[] supportIntent={null};
            Instrumentation.ActivityMonitor monitor=new Instrumentation.ActivityMonitor(){
                @Override public Instrumentation.ActivityResult onStartActivity(Intent intent){
                    if(Intent.ACTION_VIEW.equals(intent.getAction())){supportIntent[0]=new Intent(intent);return new Instrumentation.ActivityResult(android.app.Activity.RESULT_CANCELED,null);}
                    return null;
                }
            };
            host.addMonitor(monitor);
            try{
                check("support_does_not_open_automatically",supportIntent[0]==null);
                host.runOnMainSync(()->settings.findViewById(R.id.support_project).performClick());
                check("support_tap_opens_exact_profile",supportIntent[0]!=null && "https://buymeacoffee.com/marcoprattv".equals(supportIntent[0].getDataString()));
                check("support_uses_external_browsable_intent",supportIntent[0].hasCategory(Intent.CATEGORY_BROWSABLE) && supportIntent[0].getComponent()==null);
            }finally{host.removeMonitor(monitor);host.runOnMainSync(settings::finish);}
            Thread.sleep(450);
            check("resumed_preview_hides_status_bar_again",!preview.getWindow().getDecorView().getRootWindowInsets().isVisible(android.view.WindowInsets.Type.statusBars()));
            result.putString("status","passed");
        }catch(Throwable error){result.putString("status","failed");result.putString("error",error.toString());}
        finally {
            if(activity!=null){PreviewActivity preview=activity;host.runOnMainSync(preview::finish);}
            try(var out=new FileOutputStream(new File(host.getTargetContext().getFilesDir(),"localization.json"))){out.write(new JSONObject().put("checks",checks).put("success","passed".equals(result.getString("status"))).put("error",result.getString("error","")).toString(2).getBytes());}catch(Exception ignored){}
            host.finish("passed".equals(result.getString("status"))?-1:0,result);
        }
    }
}
