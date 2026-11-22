package com.dumbermini.keynavigator;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.SharedPreferences;
import android.provider.Settings;

final class AccessibilityConfig {
    private static final String INITIALIZED = "accessibility_initialized_v1";

    static boolean canConfigure(Context context) {
        return context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED;
    }

    static boolean isEnabled(Context context) {
        return Settings.Secure.getInt(context.getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED, 0) != 0
                && ServiceList.enabled(Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES));
    }

    static boolean setEnabled(Context context, boolean enabled) { return setEnabled(context, enabled, false); }

    private static boolean setEnabled(Context context, boolean enabled, boolean retireLegacy) {
        if (!canConfigure(context)) return false;
        try {
            String current = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            String updated = ServiceList.update(current, enabled, retireLegacy);
            if (!Settings.Secure.putString(context.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, updated)) return false;
            if (enabled || updated.isEmpty())
                return Settings.Secure.putInt(context.getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED, enabled ? 1 : 0);
            return true;
        } catch (SecurityException denied) { return false; }
    }

    static void initializeOnce(Context context) {
        if ((context.getApplicationInfo().flags & ApplicationInfo.FLAG_SYSTEM) == 0 || !canConfigure(context)) return;
        SharedPreferences setup = context.getSharedPreferences("setup", Context.MODE_PRIVATE);
        if (setup.getBoolean(INITIALIZED, false)) return;
        if (setEnabled(context, true, true)) setup.edit().putBoolean(INITIALIZED, true).commit();
    }

    static Intent detailsIntent(Context context) {
        // Service-detail routing requires a signature-only permission. This fallback
        // also works for development installs without the ROM privileged grant.
        return new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
    }
}
