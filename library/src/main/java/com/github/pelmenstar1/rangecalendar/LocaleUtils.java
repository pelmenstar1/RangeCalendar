package com.github.pelmenstar1.rangecalendar;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;

import org.jetbrains.annotations.NotNull;

import java.util.Locale;

final class LocaleUtils {
    @NotNull
    public static Locale getLocale(@NotNull Context context) {
        Resources res = context.getResources();
        Configuration config = res.getConfiguration();

        if(Build.VERSION.SDK_INT >= 24) {
            return config.getLocales().get(0);
        } else {
            //noinspection deprecation
            return config.locale;
        }
    }
}
