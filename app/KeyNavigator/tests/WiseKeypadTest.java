package com.dumbermini.keynavigator;

public final class WiseKeypadTest {
    private static int checks;
    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
    private static Keypad.Node node(String type, String text) {
        Keypad.Node node = new Keypad.Node();
        node.packageName = WiseKeypad.PACKAGE; node.className = type; node.text = text;
        node.visible = true; node.enabled = true;
        return node;
    }
    private static Keypad.Node fixture() {
        Keypad.Node root = node("android.widget.FrameLayout", "");
        root.id = WiseKeypad.CONTAINER;
        root.children.add(node("android.widget.TextView", WiseKeypad.HEADING));
        Keypad.Node indicator = node("android.view.View", "");
        root.children.add(indicator);
        for (int i = 0; i < 12; i++) {
            Keypad.Node key = node("android.view.View", ""); key.clickable = true;
            if (i < 9) key.children.add(node("android.widget.TextView", Integer.toString(i + 1)));
            key.children.add(node("android.widget.Button", ""));
            if (i == 9 || i == 11) key.enabled = false;
            root.children.add(key);
        }
        return root;
    }
    public static void main(String[] args) {
        Keypad.Node root = fixture();
        WiseKeypad keypad = WiseKeypad.match(root);
        check(keypad != null, "Observed clipped Wise PIN layout matches");
        for (int digit = 1; digit <= 9; digit++)
            check(keypad.digits[digit] == root.children.get(digit + 1), "Correct physical digit " + digit);
        check(keypad.digits[0] == root.children.get(12), "Unlabelled zero is bottom centre");
        check(keypad.delete == root.children.get(11), "Delete is bottom left, not OK");
        check(keypad.confirm == root.children.get(13), "OK is bottom right");
        for (int i = 11; i < 14; i++) root.children.get(i).visible = false;
        check(WiseKeypad.match(root) != null, "Off-screen bottom-row parents remain usable");
        root.children.get(12).children.add(node("android.widget.TextView", "0"));
        check(WiseKeypad.match(root) != null, "Visible zero label also matches");
        root.children.get(1).text = "Do not inspect a PIN indicator";
        check(WiseKeypad.match(root) != null, "PIN indicator is irrelevant to matching");
        root = fixture(); root.children.get(0).text = "Enter an amount";
        check(WiseKeypad.match(root) == null, "Reject other numerical screens");
        root = fixture(); root.packageName = "another.app";
        check(WiseKeypad.match(root) == null, "Reject other apps");
        root = fixture(); root.id = "other_container";
        check(WiseKeypad.match(root) == null, "Require known Wise container");
        root = fixture(); root.children.remove(13);
        check(WiseKeypad.match(root) == null, "Reject partial keypad");
        root = fixture(); root.children.get(2).children.get(0).text = "9";
        check(WiseKeypad.match(root) == null, "Reject reordered digits");
        root = fixture(); root.children.get(12).enabled = false;
        check(WiseKeypad.match(root) == null, "Reject disabled digit row");
        root = fixture(); root.children.get(12).children.add(node("android.widget.TextView", "Help"));
        check(WiseKeypad.match(root) == null, "Reject unknown bottom-row action");
        root = fixture(); root.children.get(11).children.get(0).description = "Delete";
        root.children.get(13).children.get(0).description = "OK";
        check(WiseKeypad.match(root) != null, "Validate icon labels when exposed");
        root.children.get(11).children.get(0).description = "OK";
        check(WiseKeypad.match(root) == null, "Reject swapped Delete and OK labels");
        root = fixture(); root.children.add(fixture());
        check(WiseKeypad.match(root) == null, "Reject ambiguous keypads");
        System.out.println("Passed " + checks + " Wise keypad checks");
    }
}
