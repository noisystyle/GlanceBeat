package it.noxia.ambientplayer;
import android.app.*;
import android.content.*;
import android.os.*;
import java.io.*;
import java.util.regex.*;
import java.util.function.BooleanSupplier;
import org.json.*;
/** Emulator-only navigation. Five silent providers, no streaming/account/production commands. */
final class MusicLaunchChecks {
 private final Instrumentation host;private final JSONArray checks=new JSONArray();private SettingsActivity activity;private String source;
 private static final String[] PROVIDERS={MusicSources.SPOTIFY,MusicSources.APPLE,MusicSources.TIDAL,MusicSources.DEEZER,MusicSources.AMAZON};
 private static final String[] LINKS={"https://open.spotify.com/track/4cOdK2wGLETKBW3PvgPWqT","https://music.apple.com/it/song/test/123","https://tidal.com/browse/track/123","https://www.deezer.com/track/123","https://music.amazon.it/tracks/B012345678"};
 private MusicLaunchChecks(Instrumentation h){host=h;}static void run(Instrumentation h){new MusicLaunchChecks(h).run();}
 private void main(Runnable r){host.runOnMainSync(r);}
 private String shell(String s)throws Exception{try(var fd=host.getUiAutomation().executeShellCommand(s);var in=new FileInputStream(fd.getFileDescriptor())){return new String(in.readAllBytes());}}
 private void fixture(String args)throws Exception{shell("am broadcast -n "+source+"/it.noxia.musicfixture.FixtureReceiver "+args);Thread.sleep(160);}
 private String launch()throws Exception{Matcher m=Pattern.compile("data=\"([^\"]*)\"").matcher(shell("am broadcast -n "+source+"/it.noxia.musicfixture.FixtureReceiver --ez query_launch true"));if(!m.find())throw new AssertionError("No launch result");return m.group(1);}
 private int launches()throws Exception{return Integer.parseInt(launch().split(",",3)[0]);}
 private void check(String name,boolean ok)throws Exception{checks.put(new JSONObject().put("name",name).put("passed",ok));if(!ok)throw new AssertionError(name);}
 private void await(BooleanSupplier ready)throws Exception{long deadline=SystemClock.uptimeMillis()+5000;boolean[] ok={false};do{main(()->ok[0]=ready.getAsBoolean());if(!ok[0])Thread.sleep(40);}while(!ok[0] && SystemClock.uptimeMillis()<deadline);if(!ok[0])throw new AssertionError("Provider selection timed out");}
 private void foreground()throws Exception{shell("am start -n "+host.getTargetContext().getPackageName()+"/it.noxia.ambientplayer.SettingsActivity");Thread.sleep(150);}
 private boolean open(MusicLaunch.Request request)throws Exception{foreground();boolean[] sent={false};main(()->sent[0]=MusicLaunch.open(activity,request,MusicSessionService.current));Thread.sleep(300);return sent[0];}
 private void run(){Bundle result=new Bundle();try{
  if(!Build.FINGERPRINT.contains("generic") && !Build.MODEL.contains("sdk"))throw new AssertionError("Emulator only");
  shell("locksettings clear --old 1234");shell("locksettings set-disabled true");shell("input keyevent KEYCODE_WAKEUP");shell("wm dismiss-keyguard");
  shell("cmd appops set "+host.getTargetContext().getPackageName()+" SYSTEM_ALERT_WINDOW allow");
  main(()->PlayerPreferences.setEnabled(host.getTargetContext(),true));shell("cmd notification allow_listener "+host.getTargetContext().getPackageName()+"/it.noxia.ambientplayer.MusicSessionService");
  activity=(SettingsActivity)host.startActivitySync(new Intent(host.getTargetContext(),SettingsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
  for(int i=0;i<PROVIDERS.length;i++){
   if(source!=null)fixture("--ez playing false");source=PROVIDERS[i];fixture("--ez playing true --ei activity_mode 0");await(()->MusicSessionService.current!=null && source.equals(MusicSessionService.current.getPackageName()));
   String label=MusicSources.name(source);MusicLaunch.Request request=MusicLaunch.resolve(activity,MusicSessionService.current);
   check(label+"_uses_provider_session_activity",request!=null && request.sessionActivity()!=null && request.direct()==null);
   int before=launches();check(label+"_session_player_launch_is_delivered",open(request) && launches()==before+1 && launch().split(",",3)[1].equals("true"));
   before=launches();check(label+"_warm_session_player_launch_is_delivered",open(request) && launches()==before+1 && launch().split(",",3)[1].equals("true"));
   fixture("--ei activity_mode 1 --es media_uri "+LINKS[i]);request=MusicLaunch.resolve(activity,MusicSessionService.current);
   before=launches();check(label+"_canceled_session_uses_exact_track_link",open(request) && launches()==before+1 && launch().endsWith(LINKS[i]) && launch().split(",",3)[1].equals("false"));
   fixture("--ei activity_mode 3");request=MusicLaunch.resolve(activity,MusicSessionService.current);check(label+"_broadcast_intent_cannot_replace_player_activity",request!=null && request.sessionActivity()==null && request.track()!=null);
   fixture("--ei activity_mode 2");request=MusicLaunch.resolve(activity,MusicSessionService.current);
   String currentLink=LINKS[i].replace("123","456").replace("B012345678","B087654321").replace("4cOdK2wGLETKBW3PvgPWqT","3cOdK2wGLETKBW3PvgPWqT");fixture("--es media_uri "+currentLink);
   before=launches();check(label+"_unlock_uses_live_track_instead_of_captured_link",open(request) && launches()==before+1 && launch().endsWith(currentLink));
   fixture("--es media_uri https://example.org/unrelated");check(label+"_does_not_fall_back_to_app_home_when_song_unavailable",MusicLaunch.resolve(activity,MusicSessionService.current)==null);
  }
  fixture("--ez playing false");source=MusicSources.SPOTIFY;fixture("--ez playing true --ei activity_mode 0");await(()->MusicSources.SPOTIFY.equals(MusicSessionService.current.getPackageName()));MusicLaunch.Request old=MusicLaunch.resolve(activity,MusicSessionService.current);
  fixture("--ez playing false");source=MusicSources.APPLE;fixture("--ez playing true --ei activity_mode 0");await(()->MusicSources.APPLE.equals(MusicSessionService.current.getPackageName()));int before=launches();check("source_switch_while_unlocking_cannot_open_old_provider",!open(old) && launches()==before);
  result.putString("status","passed");
 }catch(Throwable failure){result.putString("status","failed");result.putString("error",failure.toString());}
 finally{try{for(String p:PROVIDERS){source=p;fixture("--ez playing false");}}catch(Exception ignored){}main(()->{if(activity!=null)activity.finish();});try(var out=new FileOutputStream(new File(host.getTargetContext().getFilesDir(),"music-launch.json"))){out.write(new JSONObject().put("checks",checks).put("success","passed".equals(result.getString("status"))).put("error",result.getString("error","")).toString(2).getBytes());}catch(Exception ignored){}}
 host.finish("passed".equals(result.getString("status"))?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
 }
}
