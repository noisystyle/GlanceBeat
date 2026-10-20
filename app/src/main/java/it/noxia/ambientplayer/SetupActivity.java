package it.noxia.ambientplayer;

import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.progressindicator.LinearProgressIndicator;

/** Two explicit required permissions. Back always leaves setup without granting anything. */
public final class SetupActivity extends AppCompatActivity {
    private int step;
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        step=state==null?(SetupPermissions.music(this)?1:0):state.getInt("setup_step",0);
        WindowCompat.setDecorFitsSystemWindows(getWindow(),false);
        setContentView(R.layout.setup);
        View root=findViewById(R.id.setup_root);
        ViewCompat.setOnApplyWindowInsetsListener(root,(view,insets)->{
            Insets bars=insets.getInsets(WindowInsetsCompat.Type.systemBars()|WindowInsetsCompat.Type.displayCutout());
            view.setPadding(bars.left,bars.top,bars.right,bars.bottom);return insets;
        });
        boolean light=MaterialColors.isColorLight(MaterialColors.getColor(this,com.google.android.material.R.attr.colorSurface,0));
        WindowCompat.getInsetsController(getWindow(),root).setAppearanceLightStatusBars(light);
        WindowCompat.getInsetsController(getWindow(),root).setAppearanceLightNavigationBars(light);
        View content=findViewById(R.id.setup_content);FrameLayout.LayoutParams params=(FrameLayout.LayoutParams)content.getLayoutParams();
        params.width=Math.min(getResources().getDisplayMetrics().widthPixels,Math.round(640*getResources().getDisplayMetrics().density));content.setLayoutParams(params);
        findViewById(R.id.setup_permission).setOnClickListener(v->openPermission());
        findViewById(R.id.setup_next).setOnClickListener(v->advance());
        findViewById(R.id.setup_back).setOnClickListener(v->{step=0;refresh();});
        refresh();
    }
    void refresh(){
        // Revoke on step two: return to the missing prerequisite.
        if(step==1 && !SetupPermissions.music(this))step=0;
        boolean granted=step==0?SetupPermissions.music(this):SetupPermissions.overlay(this);
        ((TextView)findViewById(R.id.setup_step)).setText(getString(R.string.setup_step,step+1,2));
        ((LinearProgressIndicator)findViewById(R.id.setup_progress)).setProgressCompat(step==0?50:100,false);
        ((TextView)findViewById(R.id.setup_permission_title)).setText(step==0?R.string.setup_music_title:R.string.setup_overlay_title);
        ((TextView)findViewById(R.id.setup_permission_body)).setText(step==0?R.string.setup_music_body:R.string.setup_overlay_body);
        ((TextView)findViewById(R.id.setup_status)).setText(granted?R.string.setup_granted:R.string.setup_required);
        ((MaterialButton)findViewById(R.id.setup_permission)).setText(granted?R.string.manage_access:R.string.setup_grant);
        MaterialButton next=findViewById(R.id.setup_next);next.setText(step==0?R.string.setup_continue:R.string.setup_finish);
        next.setEnabled(step==0?granted:SetupPermissions.ready(this));
        findViewById(R.id.setup_back).setVisibility(step==0?View.GONE:View.VISIBLE);
    }
    private void advance(){
        if(step==0){if(SetupPermissions.music(this))step=1;refresh();return;}
        // The button may be stale if a permission was revoked while this activity stayed visible.
        if(!SetupPermissions.ready(this)){refresh();return;}
        startActivity(new Intent(this,SettingsActivity.class));finish();
    }
    private void openPermission(){
        if(step==1){
            try{startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getPackageName())));}
            catch(ActivityNotFoundException unavailable){unavailable();}return;
        }
        try{startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,new ComponentName(this,MusicSessionService.class).flattenToString()));}
        catch(ActivityNotFoundException missing){
            try{startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));}
            catch(ActivityNotFoundException unavailable){unavailable();}
        }
    }
    private void unavailable(){Toast.makeText(this,R.string.access_unavailable,Toast.LENGTH_LONG).show();}
    @Override protected void onResume(){super.onResume();refresh();}
    @Override protected void onSaveInstanceState(Bundle state){state.putInt("setup_step",step);super.onSaveInstanceState(state);}
}
