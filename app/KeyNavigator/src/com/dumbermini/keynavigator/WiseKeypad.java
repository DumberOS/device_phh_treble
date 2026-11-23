package com.dumbermini.keynavigator;

import java.util.ArrayList;
import java.util.List;

/** Exact observed Wise passcode layout, including its unlabelled bottom-row keys. */
final class WiseKeypad {
    static final String PACKAGE = "com.transferwise.android";
    static final String CONTAINER = PACKAGE + ":id/container";
    static final String PIN_ACTIVITY = "com.wise.ui.app_security.pin.PinEntryActivity";
    final Keypad.Node[] digits = new Keypad.Node[10];
    final Keypad.Node delete;
    final Keypad.Node confirm;

    private WiseKeypad(Layout layout) {
        for (int i = 0; i < 9; i++) digits[i + 1] = layout.keys.get(i);
        digits[0] = layout.keys.get(10);
        delete = layout.keys.get(9);
        confirm = layout.keys.get(11);
    }

    static final class Layout {
        Keypad.Node heading;
        final List<Keypad.Node> keys = new ArrayList<>();
        final List<Keypad.Node> labels = new ArrayList<>();
    }

    /** Structural candidates allow the reader to inspect only numeric keycaps. */
    static Layout layout(Keypad.Node root) {
        if (root == null || !PACKAGE.equals(root.packageName)) return null;
        List<Layout> found = new ArrayList<>();
        collect(root, false, found);
        return found.size() == 1 ? found.get(0) : null;
    }

    private static void collect(Keypad.Node node, boolean inContainer, List<Layout> found) {
        if (!PACKAGE.equals(node.packageName)) return;
        inContainer |= CONTAINER.equals(node.id);
        if (inContainer) {
            Layout layout = new Layout();
            int headings = 0;
            for (Keypad.Node child : node.children) {
                if (!PACKAGE.equals(child.packageName)) continue;
                if ("android.widget.TextView".equals(child.className)) { layout.heading = child; headings++; }
                if (!child.clickable || !"android.view.View".equals(child.className)) continue;
                int buttons = 0;
                Keypad.Node label = null;
                int labels = 0;
                for (Keypad.Node part : child.children) {
                    if (!PACKAGE.equals(part.packageName)) continue;
                    if ("android.widget.Button".equals(part.className)) buttons++;
                    if ("android.widget.TextView".equals(part.className)) { label = part; labels++; }
                }
                if (buttons == 1 && labels <= 1) {
                    layout.keys.add(child);
                    layout.labels.add(label);
                }
            }
            if (headings == 1 && layout.keys.size() == 12) found.add(layout);
        }
        for (Keypad.Node child : node.children) collect(child, inContainer, found);
    }

    static WiseKeypad match(Keypad.Node root, String activityClass) {
        if (!PIN_ACTIVITY.equals(activityClass)) return null;
        Layout layout = layout(root);
        if (layout == null || !layout.heading.visible) return null;
        for (int i = 0; i < 9; i++) {
            Keypad.Node key = layout.keys.get(i), label = layout.labels.get(i);
            if (!key.enabled || label == null || !isDigit(label.text, i + 1)) return null;
        }
        // Compose exposes the bottom row even when its rendered children are clipped
        // away. Never infer it from coordinates alone: require the complete PIN layout.
        Keypad.Node zeroLabel = layout.labels.get(10);
        if (zeroLabel != null && !zeroLabel.text.trim().isEmpty() && !isDigit(zeroLabel.text, 0)) return null;
        if (!layout.keys.get(10).enabled) return null;
        return new WiseKeypad(layout);
    }

    private static boolean isDigit(String text, int expected) {
        String label = text.trim();
        return label.codePointCount(0, label.length()) == 1 && Character.digit(label.codePointAt(0), 10) == expected;
    }
}
