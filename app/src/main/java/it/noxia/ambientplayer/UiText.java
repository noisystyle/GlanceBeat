package it.noxia.ambientplayer;

import android.content.Context;
import android.content.res.Configuration;
import android.os.LocaleList;

/** Use the translated text's plural rules when the device language falls back to English. */
final class UiText {
    static String quantity(Context context,int resource,int amount) {
        String language=context.getString(R.string.resource_language);
        if(context.getResources().getConfiguration().getLocales().get(0).getLanguage().equals(language))
            return context.getResources().getQuantityString(resource,amount,amount);
        Configuration translated=new Configuration(context.getResources().getConfiguration());
        translated.setLocales(LocaleList.forLanguageTags(language));
        return context.createConfigurationContext(translated).getResources().getQuantityString(resource,amount,amount);
    }
    private UiText() { }
}
