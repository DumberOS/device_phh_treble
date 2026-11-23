package com.dumbermini.keynavigator;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;

/** Explicitly opted-in single digit/delete test; checks Delete state, never PIN text. */
public final class WisePinProbe extends Instrumentation {
    private UiAutomation automation;
    private boolean approved;
    private boolean deleteOnly;
    @Override public void onCreate(Bundle args) {
        super.onCreate(args);
        approved = args != null && "true".equals(args.getString("empty-field-confirmed"));
        deleteOnly = args != null && "true".equals(args.getString("delete-only"));
        start();
    }
    @Override public void onStart() {
        Bundle result = new Bundle();
        boolean attempted = false;
        Exception failure = null;
        try {
            require(approved || deleteOnly, "Explicit empty-field confirmation is required");
            automation = getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
            AccessibilityServiceInfo info = automation.getServiceInfo();
            info.flags |= AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
                    | AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
                    | AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS;
            automation.setServiceInfo(info);
            if (deleteOnly) {
                try (WisePinScreen screen = screen()) {
                    if (screen.keypad.delete.enabled) require(screen.click(screen.keypad.delete), "Delete rejected");
                }
                require(waitDelete(false), "Field not empty after Delete");
                result.putString("result", "PASS: empty state verified after cleanup; no digit entered");
                finish(-1, result); return;
            }
            try (WisePinScreen screen = screen()) {
                require(!screen.keypad.delete.enabled, "Delete is enabled: field might not be empty; no digit entered");
                attempted = true;
                require(screen.click(screen.keypad.digits[0]), "Bottom-row digit click rejected");
            }
            require(waitDelete(true), "Digit did not enable Delete");
        } catch (Exception e) { failure = e; }
        finally {
            if (attempted) {
                try {
                    try (WisePinScreen screen = screen()) {
                        if (screen.keypad.delete.enabled)
                            require(screen.click(screen.keypad.delete), "Delete click rejected");
                    }
                    require(waitDelete(false), "Field did not return to empty state");
                } catch (Exception e) { failure = e; }
            }
        }
        result.putString("result", failure == null
                ? "PASS: bottom-row digit click enabled Delete; delete click restored empty state; no PIN submitted"
                : "FAIL: " + failure.getMessage());
        finish(failure == null ? -1 : 1, result);
    }

    private WisePinScreen screen() {
        require(pinActivityResumed(), "Wise PIN activity is not resumed");
        AccessibilityNodeInfo root = null;
        for (AccessibilityWindowInfo window : automation.getWindows()) {
            try {
                if (root == null && window.isFocused() && window.getType() == AccessibilityWindowInfo.TYPE_APPLICATION)
                    root = window.getRoot();
            } finally { window.recycle(); }
        }
        require(root != null, "No focused application root");
        if (!WiseKeypad.PACKAGE.contentEquals(root.getPackageName())) {
            root.recycle(); throw new IllegalStateException("Wise is no longer foreground");
        }
        WisePinScreen screen = WisePinScreen.read(root, WiseKeypad.PIN_ACTIVITY);
        if (screen.keypad == null) { screen.close(); throw new IllegalStateException("Unknown Wise PIN layout"); }
        return screen;
    }

    private boolean pinActivityResumed() {
        // This test-only shell query confirms activity metadata without needing to
        // wait for a new window event. The production service uses accessibility events.
        String component = WiseKeypad.PACKAGE + "/" + WiseKeypad.PIN_ACTIVITY;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new ParcelFileDescriptor.AutoCloseInputStream(
                        automation.executeShellCommand("dumpsys activity activities"))))) {
            String line;
            while ((line = reader.readLine()) != null)
                if (line.trim().startsWith("topResumedActivity=")) return line.contains(" " + component + " ");
        } catch (IOException error) { throw new IllegalStateException("Cannot confirm resumed activity", error); }
        return false;
    }

    private boolean waitDelete(boolean enabled) {
        long deadline = SystemClock.uptimeMillis() + 3000;
        do {
            SystemClock.sleep(100);
            try (WisePinScreen screen = screen()) { if (screen.keypad.delete.enabled == enabled) return true; }
        } while (SystemClock.uptimeMillis() < deadline);
        return false;
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
