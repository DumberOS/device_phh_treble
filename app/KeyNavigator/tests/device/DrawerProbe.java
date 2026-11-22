package com.dumbermini.keynavigator.probe;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.List;

/** One guarded drawer-handle click only. Never enters or confirms a PIN. */
public final class DrawerProbe extends Instrumentation {
    private static final String PREFIX = "com.bankid.bus:id/";
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            UiAutomation automation = getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
            AccessibilityServiceInfo info = automation.getServiceInfo();
            info.flags |= AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
                    | AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS;
            automation.setServiceInfo(info);
            AccessibilityNodeInfo root = automation.getRootInActiveWindow();
            if (root == null) throw new IllegalStateException("No active root");
            try {
                if (!"com.bankid.bus".contentEquals(root.getPackageName()))
                    throw new IllegalStateException("BankID is not foreground");
                if (visibleCount(root, "ButtonGrid") != 0 || visibleCount(root, "AcknowledgementButton") != 0
                        || visibleCount(root, "sign_fragment") != 1 || visibleCount(root, "SecurityCodeEditText") != 1)
                    throw new IllegalStateException("Not a collapsed keypad screen");
                List<AccessibilityNodeInfo> drawers = root.findAccessibilityNodeInfosByViewId(PREFIX + "KeypadDrawer");
                try {
                    if (drawers.size() != 1 || !drawers.get(0).isVisibleToUser()
                            || !"com.bankid.bus.activities.widgets.BisaSlidingDrawer".contentEquals(drawers.get(0).getClassName()))
                        throw new IllegalStateException("Unknown drawer");
                    List<AccessibilityNodeInfo> handles = drawers.get(0).findAccessibilityNodeInfosByViewId(PREFIX + "handle");
                    try {
                        if (handles.size() != 1 || !handles.get(0).isVisibleToUser() || !handles.get(0).isEnabled()
                                || visibleCount(handles.get(0), "SecurityCodeEditText") != 1)
                            throw new IllegalStateException("Unknown handle");
                        result.putBoolean("handle_click_accepted", handles.get(0).performAction(AccessibilityNodeInfo.ACTION_CLICK));
                    } finally { for (AccessibilityNodeInfo node : handles) node.recycle(); }
                } finally { for (AccessibilityNodeInfo node : drawers) node.recycle(); }
            } finally { root.recycle(); }
            long deadline = SystemClock.uptimeMillis() + 2000;
            boolean opened = false;
            while (!opened && SystemClock.uptimeMillis() < deadline) {
                SystemClock.sleep(100);
                root = automation.getRootInActiveWindow();
                if (root != null) {
                    try {
                        if (!"com.bankid.bus".contentEquals(root.getPackageName())) break;
                        opened = visibleCount(root, "ButtonGrid") == 1;
                    } finally { root.recycle(); }
                }
            }
            result.putBoolean("keypad_opened", opened);
            finish(opened ? -1 : 1, result);
        } catch (Exception e) {
            result.putString("error", e.getClass().getSimpleName() + ": " + e.getMessage()); finish(1, result);
        }
    }
    private int visibleCount(AccessibilityNodeInfo root, String id) {
        List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByViewId(PREFIX + id);
        int count = 0;
        for (AccessibilityNodeInfo node : nodes) {
            try { if (node.isVisibleToUser()) count++; } finally { node.recycle(); }
        }
        return count;
    }
}
