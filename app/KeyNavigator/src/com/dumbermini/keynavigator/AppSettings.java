package com.dumbermini.keynavigator;

import android.content.Context;
import android.content.SharedPreferences;

final class AppSettings {
    private final SharedPreferences preferences;

    AppSettings(Context context) {
        preferences = context.getSharedPreferences("app_controls", Context.MODE_PRIVATE);
    }

    AppPolicy.Mode mode(String packageName) {
        if (packageName == null || packageName.isEmpty()) return AppPolicy.Mode.OFF;
        Boolean value = preferences.contains(packageName) ? preferences.getBoolean(packageName, false) : null;
        return AppPolicy.mode(packageName, value);
    }

    // Per-app switches govern only standard navigation, including BankID and Wise.
    boolean enabled(String packageName) { return AppPolicy.spatialEnabled(mode(packageName)); }

    void setEnabled(String packageName, boolean enabled) {
        preferences.edit().putBoolean(packageName, enabled).apply();
    }

    void register(SharedPreferences.OnSharedPreferenceChangeListener listener) {
        preferences.registerOnSharedPreferenceChangeListener(listener);
    }

    void unregister(SharedPreferences.OnSharedPreferenceChangeListener listener) {
        preferences.unregisterOnSharedPreferenceChangeListener(listener);
    }
}
