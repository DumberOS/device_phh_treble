package com.dumbermini.keynavigator.probe;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

/** Explicit opt-in single scroll test. No clicks, text or descriptions. */
public final class ScrollProbe extends Instrumentation {
    private String packageName, targetId;
    private int action;
    private boolean verticalCollection;
    private int count;
    private final StringBuilder output = new StringBuilder();
    @Override public void onCreate(Bundle args) {
        super.onCreate(args);
        packageName = args.getString("package", "");
        targetId = args.getString("id", "");
        verticalCollection = "vertical-collection".equals(args.getString("selector", ""));
        String direction = args.getString("direction", "down");
        action = "forward".equals(direction) ? AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                : "backward".equals(direction) ? AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
                : "up".equals(direction) ? AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_UP.getId()
                : AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_DOWN.getId();
        start();
    }
    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            if (packageName.isEmpty()) throw new IllegalArgumentException("Specify -e package PACKAGE");
            UiAutomation automation = getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
            AccessibilityServiceInfo info = automation.getServiceInfo();
            info.flags |= AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
                    | AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
                    | AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS;
            automation.setServiceInfo(info);
            AccessibilityNodeInfo root = null;
            long deadline = SystemClock.uptimeMillis() + 5000;
            while (root == null && SystemClock.uptimeMillis() < deadline) {
                for (AccessibilityWindowInfo window : automation.getWindows()) {
                    try {
                        if (root == null && window.isFocused() && window.getType() == AccessibilityWindowInfo.TYPE_APPLICATION)
                            root = window.getRoot();
                    } finally { window.recycle(); }
                }
                if (root == null) SystemClock.sleep(100);
            }
            if (root == null) throw new IllegalStateException("No focused application root");
            try {
                if (!packageName.equals(String.valueOf(root.getPackageName())))
                    throw new IllegalStateException("Requested app is not foreground");
                AccessibilityNodeInfo target = find(root, 0);
                if (target == null) throw new IllegalStateException("Scroll target absent");
                try {
                    output.append("BEFORE\n"); read(target, 0);
                    output.append("accepted=").append(target.performAction(action)).append('\n');
                    SystemClock.sleep(1200);
                    target.refresh();
                    output.append("AFTER\n"); count = 0; read(target, 0);
                } finally { target.recycle(); }
            } finally { root.recycle(); }
            result.putString("structure", output.toString());
            finish(-1, result);
        } catch (Exception e) {
            result.putString("error", e.getClass().getSimpleName() + ": " + e.getMessage());
            finish(1, result);
        }
    }
    private AccessibilityNodeInfo find(AccessibilityNodeInfo node, int depth) {
        if (++count > 600 || depth > 60) throw new IllegalStateException("Tree limit");
        AccessibilityNodeInfo.CollectionInfo collection = node.getCollectionInfo();
        if ((!verticalCollection && targetId.equals(node.getViewIdResourceName()))
                || (verticalCollection && node.isScrollable() && collection != null
                    && collection.getColumnCount() == 1 && collection.getRowCount() > 1))
            return AccessibilityNodeInfo.obtain(node);
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child == null) continue;
            try {
                AccessibilityNodeInfo result = find(child, depth + 1);
                if (result != null) return result;
            } finally { child.recycle(); }
        }
        return null;
    }
    private void read(AccessibilityNodeInfo node, int depth) {
        Rect bounds = new Rect(); node.getBoundsInScreen(bounds);
        output.append("id=").append(node.getViewIdResourceName()).append(" bounds=").append(bounds.toShortString())
                .append(" actions=");
        for (AccessibilityNodeInfo.AccessibilityAction action : node.getActionList()) output.append(action.getId()).append(',');
        output.append('\n');
        if (depth == 0) for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) { try { read(child, 1); } finally { child.recycle(); } }
        }
    }
}
