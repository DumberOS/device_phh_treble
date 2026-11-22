package com.dumbermini.keynavigator;

import java.util.ArrayList;
import java.util.List;

/** Matches the observed BankID keypad, without reading or retaining PIN text. */
final class Keypad {
    static final String PACKAGE = "com.bankid.bus";
    static final String GRID = PACKAGE + ":id/ButtonGrid";
    static final String FIELD = PACKAGE + ":id/SecurityCodeEditText";
    static final String DRAWER = PACKAGE + ":id/KeypadDrawer";
    static final String HANDLE = PACKAGE + ":id/handle";

    static final class Node {
        String packageName = "", id = "", text = "", description = "", className = "";
        boolean visible, clickable, enabled, scrollable, scrollForward;
        final List<Node> children = new ArrayList<>();
    }

    final Node[] digits = new Node[10];
    final Node delete;
    final Node confirm;

    private Keypad(Node grid) {
        for (int i = 0; i < 9; i++) digits[i + 1] = grid.children.get(i);
        digits[0] = grid.children.get(10);
        delete = grid.children.get(9);
        confirm = grid.children.get(11);
    }

    static Keypad match(Node root) {
        if (root == null || !PACKAGE.equals(root.packageName)) return null;
        List<Node> grids = new ArrayList<>(), fields = new ArrayList<>();
        collect(root, GRID, grids);
        collect(root, FIELD, fields);
        if (grids.size() != 1 || fields.size() != 1) return null;
        Node grid = grids.get(0), field = fields.get(0);
        if (!field.enabled || !"android.widget.EditText".equals(field.className)
                || grid.children.size() != 12) return null;
        // Validate every position, not just the requested digit. Unknown layouts pass through.
        for (int i = 0; i < 12; i++) {
            Node button = grid.children.get(i);
            if (!PACKAGE.equals(button.packageName) || !button.visible || !button.clickable
                    || !"android.widget.Button".equals(button.className)) return null;
            int digit = i < 9 ? i + 1 : i == 10 ? 0 : -1;
            if (digit >= 0 && (!button.enabled || !hasDigit(button, digit))) return null;
        }
        Node confirm = grid.children.get(11);
        // Confirmation must have an explicitly recognised label; never click an unknown action.
        if (!hasLabel(confirm, "Identify") && !hasLabel(confirm, "Sign") && !hasLabel(confirm, "Approve")
                && !hasLabel(confirm, "Identifiera") && !hasLabel(confirm, "Skriv under")
                && !hasLabel(confirm, "Godkänn")) {
            return null;
        }
        return new Keypad(grid);
    }

    /** The collapsed drawer's handle opens PIN entry; never use it on an open keypad. */
    static Node collapsedHandle(Node root) {
        if (root == null || !PACKAGE.equals(root.packageName)) return null;
        List<Node> grids = new ArrayList<>(), acknowledgements = new ArrayList<>();
        collect(root, GRID, grids);
        collect(root, ReadingScreen.BUTTON, acknowledgements);
        if (!grids.isEmpty() || !acknowledgements.isEmpty()
                || unique(root, ReadingScreen.FRAGMENT) == null) return null;
        Node drawer = unique(root, DRAWER), field = unique(root, FIELD);
        if (drawer == null || !drawer.enabled
                || !"com.bankid.bus.activities.widgets.BisaSlidingDrawer".equals(drawer.className)) return null;
        Node handle = unique(drawer, HANDLE);
        if (handle == null || !handle.enabled || !handle.clickable
                || !"android.widget.LinearLayout".equals(handle.className)
                || field == null || unique(handle, FIELD) != field
                || !field.enabled || !field.clickable
                || !"android.widget.EditText".equals(field.className)) return null;
        return handle;
    }

    private static Node unique(Node root, String id) {
        List<Node> matches = new ArrayList<>();
        collect(root, id, matches);
        return matches.size() == 1 ? matches.get(0) : null;
    }

    private static void collect(Node node, String id, List<Node> matches) {
        if (!node.visible) return;
        if (id.equals(node.id) && PACKAGE.equals(node.packageName)) matches.add(node);
        for (Node child : node.children) collect(child, id, matches);
    }

    private static boolean hasDigit(Node node, int digit) {
        return hasLabel(node, Integer.toString(digit));
    }

    private static boolean hasLabel(Node node, String label) {
        if (!node.visible) return false;
        if (label.equals(node.text.trim()) || label.equals(node.description.trim())) return true;
        for (Node child : node.children) if (hasLabel(child, label)) return true;
        return false;
    }
}
