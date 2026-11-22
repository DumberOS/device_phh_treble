package com.dumbermini.keynavigator.probe;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.os.Bundle;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.List;
import java.util.ArrayList;

/** Read-only keycap inspection. Never reads the code field or clicks any control. */
public final class KeypadLabelsProbe extends Instrumentation {
    private boolean wise;
    @Override public void onCreate(Bundle args) { super.onCreate(args); wise = args != null && "wise".equals(args.getString("app")); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            UiAutomation automation = getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
            AccessibilityServiceInfo info = automation.getServiceInfo();
            info.flags |= AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS | AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS;
            automation.setServiceInfo(info);
            AccessibilityNodeInfo root = automation.getRootInActiveWindow();
            if (root == null) throw new IllegalStateException("No root");
            StringBuilder output = new StringBuilder();
            try {
                if (wise) {
                    if (!"com.transferwise.android".contentEquals(root.getPackageName())) throw new IllegalStateException("Wise not foreground");
                    if (!wiseLabels(root, output, 0)) throw new IllegalStateException("Wise keypad group not found");
                    result.putString("keycaps", output.toString()); finish(-1, result); return;
                }
                if (!"com.bankid.bus".contentEquals(root.getPackageName())) throw new IllegalStateException("BankID not foreground");
                List<AccessibilityNodeInfo> grids = root.findAccessibilityNodeInfosByViewId("com.bankid.bus:id/ButtonGrid");
                try {
                    if (grids.size() != 1 || grids.get(0).getChildCount() != 12) throw new IllegalStateException("Unexpected grid");
                    for (int i = 0; i < 12; i++) {
                        AccessibilityNodeInfo button = grids.get(0).getChild(i);
                        if (button == null) throw new IllegalStateException("Missing button");
                        try {
                            output.append("keycap ").append(i).append(": ");
                            labels(button, output, 0); output.append('\n');
                        } finally { button.recycle(); }
                    }
                } finally { for (AccessibilityNodeInfo grid : grids) grid.recycle(); }
            } finally { root.recycle(); }
            result.putString("keycaps", output.toString()); finish(-1, result);
        } catch (Exception e) {
            result.putString("error", e.getMessage()); finish(1, result);
        }
    }
    private boolean wiseLabels(AccessibilityNodeInfo node, StringBuilder output, int depth) {
        if (depth > 30) return false;
        List<AccessibilityNodeInfo> keys = new ArrayList<>();
        try {
            for (int i = 0; i < node.getChildCount(); i++) {
                AccessibilityNodeInfo child = node.getChild(i);
                if (child == null) continue;
                boolean button = false;
                for (int j = 0; j < child.getChildCount(); j++) {
                    AccessibilityNodeInfo grandchild = child.getChild(j);
                    if (grandchild != null) {
                        try { button |= "android.widget.Button".contentEquals(grandchild.getClassName()); }
                        finally { grandchild.recycle(); }
                    }
                }
                if (child.isClickable() && button) keys.add(child);
                else child.recycle();
            }
            if (keys.size() == 12) {
                // Only the static heading and keycaps; never inspect the PIN indicator.
                for (int i = 0; i < node.getChildCount(); i++) {
                    AccessibilityNodeInfo child = node.getChild(i);
                    if (child != null) {
                        try {
                            if ("android.widget.TextView".contentEquals(child.getClassName()))
                                output.append("heading=").append(child.getText()).append('\n');
                        } finally { child.recycle(); }
                    }
                }
                for (int i = 0; i < keys.size(); i++) {
                    output.append("keycap ").append(i).append(": ");
                    labels(keys.get(i), output, 0); output.append('\n');
                }
                return true;
            }
        } finally { for (AccessibilityNodeInfo key : keys) key.recycle(); }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                try { if (wiseLabels(child, output, depth + 1)) return true; }
                finally { child.recycle(); }
            }
        }
        return false;
    }
    private void labels(AccessibilityNodeInfo node, StringBuilder output, int depth) {
        if (depth > 3 || node.isPassword() || node.isEditable()
                || "com.bankid.bus:id/SecurityCodeEditText".equals(node.getViewIdResourceName())) return;
        output.append("[text=").append(node.getText()).append(" description=").append(node.getContentDescription()).append(']');
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) { try { labels(child, output, depth + 1); } finally { child.recycle(); } }
        }
    }
}
