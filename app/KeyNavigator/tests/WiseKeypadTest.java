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
        root.children.add(node("android.widget.TextView", "Enter your passcode"));
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
    private static WiseKeypad match(Keypad.Node root) {
        return WiseKeypad.match(root, WiseKeypad.PIN_ACTIVITY);
    }
    public static void main(String[] args) {
        Keypad.Node root = fixture();
        WiseKeypad keypad = match(root);
        check(keypad != null, "Observed clipped Wise PIN layout matches");
        for (int digit = 1; digit <= 9; digit++)
            check(keypad.digits[digit] == root.children.get(digit + 1), "Correct physical digit " + digit);
        check(keypad.digits[0] == root.children.get(12), "Unlabelled zero is bottom centre");
        check(keypad.delete == root.children.get(11), "Delete is bottom left, not OK");
        check(keypad.confirm == root.children.get(13), "OK is bottom right");
        for (int i = 11; i < 14; i++) root.children.get(i).visible = false;
        check(match(root) != null, "Off-screen bottom-row parents remain usable");
        root.children.get(12).children.add(node("android.widget.TextView", "0"));
        check(match(root) != null, "Visible zero label also matches");
        root.children.get(1).text = "Do not inspect a PIN indicator";
        check(match(root) != null, "PIN indicator is irrelevant to matching");
        root = fixture(); root.children.get(0).text = "Enter an amount";
        check(WiseKeypad.match(root, "com.wise.ui.transfer.AmountActivity") == null, "Reject other numerical activities");
        check(WiseKeypad.match(root, "") == null, "No matching from layout alone without confirmed activity");
        check(WiseKeypad.match(root, null) == null, "No matching with missing activity");
        for (String heading : new String[] {"Ange din kod", "Wpisz kod dostępu", "أدخل رمز المرور", "", "任意の翻訳"}) {
            root = fixture(); root.children.get(0).text = heading;
            check(match(root) != null, "Heading wording does not control recognition");
        }
        for (String numbers : new String[] {"١٢٣٤٥٦٧٨٩٠", "१२३४५६७८९०", "１２３４５６７８９０"}) {
            root = fixture();
            for (int i = 0; i < 9; i++) root.children.get(i + 2).children.get(0).text = numbers.substring(i, i + 1);
            root.children.get(12).children.add(node("android.widget.TextView", numbers.substring(9)));
            check(match(root) != null, "Localized numerals retain their numeric meaning");
        }
        root = fixture(); root.children.get(2).children.get(0).text = "12";
        check(match(root) == null, "Reject multi-digit labels");
        root = fixture(); root.packageName = "another.app";
        check(match(root) == null, "Reject other apps");
        root = fixture(); root.id = "other_container";
        check(match(root) == null, "Require known Wise container");
        root = fixture(); root.children.remove(13);
        check(match(root) == null, "Reject partial keypad");
        root = fixture(); root.children.get(2).children.get(0).text = "9";
        check(match(root) == null, "Reject reordered digits");
        root = fixture(); root.children.get(12).enabled = false;
        check(match(root) == null, "Reject disabled digit row");
        root = fixture(); root.children.get(12).children.add(node("android.widget.TextView", "Help"));
        check(match(root) == null, "Reject unknown bottom-row action");
        root = fixture(); root.children.get(11).children.get(0).description = "Ta bort";
        root.children.get(13).children.get(0).description = "Bekräfta";
        check(match(root) != null, "Translated icon descriptions are irrelevant");
        root.children.get(11).children.add(node("android.widget.TextView", "Usuń"));
        root.children.get(13).children.add(node("android.widget.TextView", "Potwierdź"));
        check(match(root) != null, "Translated action labels are irrelevant");
        root = fixture(); root.children.add(fixture());
        check(match(root) == null, "Reject ambiguous keypads");
        System.out.println("Passed " + checks + " Wise keypad checks");
    }
}
