package it.noxia.ambientplayer;
import android.content.Context;
final class AccessoryLocaleChecks {
    static boolean verify(Context context,boolean italian){return AccessorySupport.createPanel(context,0xff9cddca,()->{},()->{})==null;}
}
