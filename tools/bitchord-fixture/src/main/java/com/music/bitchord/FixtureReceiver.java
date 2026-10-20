package com.music.bitchord;
import android.content.*;
public class FixtureReceiver extends BroadcastReceiver {
 @Override public void onReceive(Context c,Intent i){
  if(i.getBooleanExtra("query_open",false)){setResultData(Integer.toString(FavoriteFixture.fullPlayerLaunches));return;}
  if(i.getBooleanExtra("query_shuffle",false)){setResultData(Integer.toString(FavoriteFixture.shuffleCommands));return;}
  if(i.hasExtra("stars"))FavoriteFixture.stars=i.getBooleanExtra("stars",false);
  if(i.hasExtra("shuffle_offered"))FavoriteFixture.shuffleOffered=i.getBooleanExtra("shuffle_offered",false);
  if(i.hasExtra("shuffled"))FavoriteFixture.shuffled=i.getBooleanExtra("shuffled",false);
  if(i.hasExtra("shuffle_icon"))FavoriteFixture.shuffleIcon=i.getIntExtra("shuffle_icon",0);
  if(i.getBooleanExtra("query",false)){setResultData(Integer.toString(FavoriteFixture.commands));return;}
  if(i.hasExtra("session_active")){FavoriteFixture.session.setActive(i.getBooleanExtra("session_active",true));return;}
  if(i.hasExtra("ack"))FavoriteFixture.ack=i.getBooleanExtra("ack",true);
  if(i.hasExtra("favorite"))FavoriteFixture.favorite=i.getBooleanExtra("favorite",false);
  if(i.hasExtra("playing"))FavoriteFixture.playing=i.getBooleanExtra("playing",false);
  if(i.hasExtra("metadata_id")){FavoriteFixture.metadataId=i.getLongExtra("metadata_id",100);FavoriteFixture.metadata();}
  if(i.hasExtra("state_id"))FavoriteFixture.stateId=i.getLongExtra("state_id",100);
  if(i.hasExtra("extra_id"))FavoriteFixture.extraId=i.getLongExtra("extra_id",100);
  if(i.hasExtra("icon"))FavoriteFixture.icon=i.getIntExtra("icon",0);
  if(!i.getBooleanExtra("metadata_only",false))FavoriteFixture.state();
 }
}
