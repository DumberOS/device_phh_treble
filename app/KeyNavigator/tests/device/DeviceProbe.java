package com.dumbermini.keynavigator.probe;

import android.app.Instrumentation;
import android.app.UiAutomation;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.List;

/** Explicit opt-in device test: one dummy digit, a hold, and Back. Never confirms. */
public final class DeviceProbe extends Instrumentation {
    private UiAutomation automation;

    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        start();
    }

    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            automation = getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
            status("Waiting up to 180 seconds for an empty BankID PIN prompt");
            waitState(false, 180000);
            status("Empty PIN prompt found; testing one physical-path digit");
            key(2, false); // Linux KEY_1 on the verified keypad.
            waitState(true, 4000);
            requireLength(1);
            key(158, false); // Linux KEY_BACK, mapped by the adapter to Delete.
            waitState(false, 4000);
            status("Digit and Back passed; testing held-key suppression");
            key(2, true);
            waitState(true, 4000);
            requireLength(1);
            key(158, true);
            waitState(false, 4000);
            result.putString("result", "PASS: digit click, Back deletion, held digit once, held Back once; PIN left empty; no confirmation sent");
            finish(-1, result);
        } catch (Exception failure) {
            result.putString("result", "FAIL: " + failure.getClass().getSimpleName() + ": " + failure.getMessage());
            finish(1, result);
        }
    }

    private void status(String value) {
        Bundle status = new Bundle();
        status.putString("progress", value);
        sendStatus(0, status);
    }

    private void key(int scanCode, boolean hold) throws Exception {
        // Check immediately before each mutation; no input if the target prompt disappeared.
        if (state() != (scanCode == 158 ? 1 : 0)) throw new IllegalStateException("Prompt changed before key");
        // Host runner uses rooted ADB only for evdev injection; the installed adapter is unprivileged.
        status("REQUEST_KEY " + scanCode + " " + (hold ? "hold" : "tap"));
        SystemClock.sleep(1800);
    }

    private void waitState(boolean populated, long timeout) {
        long deadline = SystemClock.uptimeMillis() + timeout;
        while (SystemClock.uptimeMillis() < deadline) {
            if (state() == (populated ? 1 : 0)) return;
            SystemClock.sleep(100);
        }
        throw new IllegalStateException("Timed out waiting for " + (populated ? "one digit" : "empty PIN"));
    }

    // -1 means no supported prompt; 0 empty; 1 populated. Never prints or retains field text.
    private int state() {
        AccessibilityNodeInfo root = automation.getRootInActiveWindow();
        if (root == null) return -1;
        try {
            if (!"com.bankid.bus".contentEquals(root.getPackageName())) return -1;
            List<AccessibilityNodeInfo> grids = root.findAccessibilityNodeInfosByViewId("com.bankid.bus:id/ButtonGrid");
            try {
                if (grids.size() != 1 || grids.get(0).getChildCount() != 12) return -1;
                AccessibilityNodeInfo delete = grids.get(0).getChild(9);
                AccessibilityNodeInfo confirm = grids.get(0).getChild(11);
                try {
                    if (delete == null || confirm == null || !delete.isVisibleToUser() || confirm.isEnabled()) return -1;
                    return delete.isEnabled() ? 1 : 0;
                } finally {
                    if (delete != null) delete.recycle();
                    if (confirm != null) confirm.recycle();
                }
            } finally {
                for (AccessibilityNodeInfo grid : grids) grid.recycle();
            }
        } finally {
            root.recycle();
        }
    }

    private void requireLength(int expected) {
        AccessibilityNodeInfo root = automation.getRootInActiveWindow();
        if (root == null) throw new IllegalStateException("Prompt disappeared");
        try {
            List<AccessibilityNodeInfo> fields = root.findAccessibilityNodeInfosByViewId("com.bankid.bus:id/SecurityCodeEditText");
            try {
                if (fields.size() != 1 || fields.get(0).getText() == null
                        || fields.get(0).getText().length() != expected)
                    throw new IllegalStateException("Unexpected dummy-digit count");
            } finally {
                for (AccessibilityNodeInfo field : fields) field.recycle();
            }
        } finally {
            root.recycle();
        }
    }
}
