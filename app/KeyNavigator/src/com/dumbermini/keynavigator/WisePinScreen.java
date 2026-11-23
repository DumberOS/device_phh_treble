package com.dumbermini.keynavigator;

import android.view.accessibility.AccessibilityNodeInfo;
import java.util.IdentityHashMap;
import java.util.Map;

/** Owns a Wise tree snapshot; never reads the PIN indicator or editable values. */
final class WisePinScreen implements AutoCloseable {
    private final Map<Keypad.Node, AccessibilityNodeInfo> nodes = new IdentityHashMap<>();
    WiseKeypad keypad;

    static WisePinScreen read(AccessibilityNodeInfo root, String activityClass) {
        WisePinScreen screen = new WisePinScreen();
        if (!WiseKeypad.PIN_ACTIVITY.equals(activityClass)) { root.recycle(); return screen; }
        try {
            Keypad.Node tree = screen.readNode(root, 0);
            WiseKeypad.Layout layout = WiseKeypad.layout(tree);
            if (layout != null) {
                // Only numeric keycaps: no heading, Delete/OK wording or PIN values.
                for (int i = 0; i < 9; i++) {
                    Keypad.Node label = layout.labels.get(i);
                    if (label != null) screen.readLabel(label);
                }
                Keypad.Node zero = layout.labels.get(10);
                if (zero != null) screen.readLabel(zero);
                screen.keypad = WiseKeypad.match(tree, activityClass);
            }
            return screen;
        } catch (RuntimeException error) { screen.close(); throw error; }
    }

    private Keypad.Node readNode(AccessibilityNodeInfo info, int depth) {
        Keypad.Node node = new Keypad.Node();
        nodes.put(node, info);
        if (depth > 35 || nodes.size() > 300) throw new IllegalStateException("Wise tree limit");
        node.packageName = value(info.getPackageName());
        node.id = value(info.getViewIdResourceName());
        node.className = value(info.getClassName());
        node.visible = info.isVisibleToUser();
        node.enabled = info.isEnabled();
        node.clickable = info.isClickable();
        if (WiseKeypad.PACKAGE.equals(node.packageName)) {
            for (int i = 0; i < info.getChildCount(); i++) {
                AccessibilityNodeInfo child = info.getChild(i);
                if (child != null) node.children.add(readNode(child, depth + 1));
            }
        }
        return node;
    }

    private void readLabel(Keypad.Node node) {
        AccessibilityNodeInfo info = nodes.get(node);
        if (!info.isEditable() && !info.isPassword()) node.text = value(info.getText());
    }

    boolean click(Keypad.Node target) {
        AccessibilityNodeInfo info = nodes.get(target);
        return target.enabled && info != null
                && info.getActionList().contains(AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK)
                && info.performAction(AccessibilityNodeInfo.ACTION_CLICK);
    }

    private static String value(CharSequence value) { return value == null ? "" : value.toString(); }

    @Override public void close() {
        for (AccessibilityNodeInfo info : nodes.values()) info.recycle();
        nodes.clear();
    }
}
