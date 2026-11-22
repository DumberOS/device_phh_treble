package com.dumbermini.keynavigator;

import java.util.ArrayList;
import java.util.List;

/** Exact observed Wise passcode layout, including its unlabelled bottom-row keys. */
final class WiseKeypad {
    static final String PACKAGE = "com.transferwise.android";
    static final String CONTAINER = PACKAGE + ":id/container";
    static final String HEADING = "Enter your passcode";
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

    /** Structural candidates allow the reader to inspect only heading/keycap text. */
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

    static WiseKeypad match(Keypad.Node root) {
        Layout layout = layout(root);
        if (layout == null || !layout.heading.visible || !HEADING.equals(layout.heading.text.trim())) return null;
        for (int i = 0; i < 9; i++) {
            Keypad.Node key = layout.keys.get(i), label = layout.labels.get(i);
            if (!key.enabled || label == null || !Integer.toString(i + 1).equals(label.text.trim())) return null;
        }
        // Compose exposes the bottom row even when its rendered children are clipped
        // away. Never infer it from coordinates alone: require the complete PIN layout.
        for (int i = 9; i < 12; i++) {
            Keypad.Node label = layout.labels.get(i);
            if (label != null && !label.text.trim().isEmpty()
                    && !(i == 10 && "0".equals(label.text.trim()))) return null;
        }
        if (!knownRole(layout.keys.get(9), "Delete") || !knownRole(layout.keys.get(11), "OK")) return null;
        if (!layout.keys.get(10).enabled) return null;
        return new WiseKeypad(layout);
    }

    private static boolean knownRole(Keypad.Node key, String expected) {
        if (!key.description.trim().isEmpty() && !expected.equals(key.description.trim())) return false;
        for (Keypad.Node child : key.children) if (!knownRole(child, expected)) return false;
        return true;
    }
}
