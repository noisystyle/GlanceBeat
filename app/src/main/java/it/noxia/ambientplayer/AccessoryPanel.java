package it.noxia.ambientplayer;

import android.content.Context;
import android.widget.FrameLayout;

/** View contract for optional local integrations; no panel exists in the public edition. */
abstract class AccessoryPanel extends FrameLayout {
    AccessoryPanel(Context context) { super(context); }
    abstract void refresh(AccessoryBattery battery);
    abstract void shown();
    abstract void viewport(int width, int height, float scale);
}
