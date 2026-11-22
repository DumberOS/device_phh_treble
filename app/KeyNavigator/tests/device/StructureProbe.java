package com.dumbermini.keynavigator.probe;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

/** Read-only structure and action inspection. Never retrieves node text or descriptions. */
public final class StructureProbe extends Instrumentation {
    private String packageName;
    private int count;
    private final StringBuilder output = new StringBuilder();
    @Override public void onCreate(Bundle args) {
        super.onCreate(args); packageName = args.getString("package", ""); start();
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
                read(root, 0);
            } finally { root.recycle(); }
            result.putString("structure", output.toString());
            finish(-1, result);
        } catch (Exception e) {
            result.putString("error", e.getClass().getSimpleName() + ": " + e.getMessage());
            finish(1, result);
        }
    }
    private void read(AccessibilityNodeInfo node, int depth) {
        if (++count > 600 || depth > 60) throw new IllegalStateException("Tree limit");
        if (!packageName.equals(String.valueOf(node.getPackageName()))) return;
        Rect bounds = new Rect(); node.getBoundsInScreen(bounds);
        AccessibilityNodeInfo.CollectionItemInfo item = node.getCollectionItemInfo();
        AccessibilityNodeInfo.CollectionInfo collection = node.getCollectionInfo();
        output.append("visible=").append(node.isVisibleToUser())
                .append(" row=").append(item == null ? -1 : item.getRowIndex())
                .append(" rows=").append(collection == null ? -1 : collection.getRowCount())
                .append(" cols=").append(collection == null ? -1 : collection.getColumnCount()).append(' ');
        output.append("depth=").append(depth).append(" class=").append(node.getClassName())
                .append(" id=").append(node.getViewIdResourceName()).append(" bounds=").append(bounds.toShortString())
                .append(" scrollable=").append(node.isScrollable()).append(" enabled=").append(node.isEnabled())
                .append(" clickable=").append(node.isClickable()).append(" actions=");
        for (AccessibilityNodeInfo.AccessibilityAction action : node.getActionList()) output.append(action.getId()).append(',');
        output.append('\n');
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                try { read(child, depth + 1); } finally { child.recycle(); }
            }
        }
    }
}
