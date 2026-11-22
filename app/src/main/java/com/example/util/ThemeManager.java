package com.example.util;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.TypedValue;

import com.example.R;

public final class ThemeManager {

    private static final String PREFS_NAME = "gam3ya_theme_prefs";
    private static final String KEY_DARK_MODE = "is_dark_mode";

    private ThemeManager() {
    }

    public static boolean isDarkMode(Context context) {
        SharedPreferences prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_DARK_MODE, false);
    }

    public static void setDarkMode(Context context, boolean darkMode) {
        SharedPreferences prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_DARK_MODE, darkMode).apply();
    }

    public static boolean applyTheme(Activity activity) {
        boolean dark = isDarkMode(activity);
        activity.setTheme(dark ? R.style.Theme_Tahwisha_Dark : R.style.Theme_Tahwisha_Light);
        return dark;
    }

    public static int resolveAttrColor(Context context, int attrResId) {
        TypedValue typedValue = new TypedValue();
        if (context.getTheme().resolveAttribute(attrResId, typedValue, true)) {
            if (typedValue.resourceId != 0) {
                return context.getColor(typedValue.resourceId);
            }
            return typedValue.data;
        }
        return context.getColor(R.color.text_primary);
    }
}
