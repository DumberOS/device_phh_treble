package com.dumbermini.keynavigator;

public final class AdapterTest {
    private static int checks;
    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
    private static Keypad.Node node(String type, String id, String text) {
        Keypad.Node n = new Keypad.Node();
        n.packageName = Keypad.PACKAGE;
        n.className = "android.widget." + type;
        n.id = id;
        n.text = text;
        n.visible = n.enabled = true;
        n.clickable = "Button".equals(type);
        return n;
    }
    private static Keypad.Node fixture() {
        Keypad.Node root = node("FrameLayout", "", "");
        root.children.add(node("EditText", Keypad.FIELD, ""));
        Keypad.Node grid = node("GridView", Keypad.GRID, "");
        root.children.add(grid);
        for (int i = 0; i < 12; i++) {
            int digit = i < 9 ? i + 1 : i == 10 ? 0 : -1;
            String label = digit >= 0 ? String.valueOf(digit) : i == 9 ? "Delete" : "Identify";
            Keypad.Node button = node("Button", "", digit >= 0 ? label + ". A. B. C." : label);
            Keypad.Node child = node("TextView", "", label);
            child.description = label;
            button.children.add(child);
            button.enabled = digit >= 0;
            grid.children.add(button);
        }
        return root;
    }
    private static Keypad.Node readingFixture(boolean ready) {
        Keypad.Node root = node("FrameLayout", "", "");
        Keypad.Node fragment = node("RelativeLayout", ReadingScreen.FRAGMENT, "");
        root.children.add(fragment);
        Keypad.Node text = node("RelativeLayout", ReadingScreen.TEXT, "");
        fragment.children.add(text);
        Keypad.Node web = node("View", "", "");
        web.className = "android.webkit.WebView";
        web.scrollable = web.scrollForward = true;
        text.children.add(web);
        Keypad.Node container = node("RelativeLayout", ReadingScreen.BUTTON_CONTAINER, "");
        fragment.children.add(container);
        Keypad.Node button = node("Button", ReadingScreen.BUTTON, "Identify with security code");
        button.enabled = ready;
        container.children.add(button);
        return root;
    }

    private static Keypad.Node collapsedFixture() {
        Keypad.Node root = node("FrameLayout", "", "");
        root.children.add(node("ViewGroup", ReadingScreen.FRAGMENT, ""));
        Keypad.Node drawer = node("View", Keypad.DRAWER, "");
        drawer.className = "com.bankid.bus.activities.widgets.BisaSlidingDrawer";
        root.children.add(drawer);
        Keypad.Node handle = node("LinearLayout", Keypad.HANDLE, "");
        handle.clickable = true;
        drawer.children.add(handle);
        Keypad.Node field = node("EditText", Keypad.FIELD, "");
        field.clickable = true;
        handle.children.add(field);
        return root;
    }

    private static void collapsedChecks() {
        Keypad.Node root = collapsedFixture();
        Keypad.Node handle = root.children.get(1).children.get(0);
        Keypad.Node field = handle.children.get(0);
        check(Keypad.collapsedHandle(root) == handle, "Collapsed drawer selects handle, not text field");
        check(Keypad.match(root) == null, "Collapsed drawer cannot submit a PIN");
        check(ReadingScreen.match(root) == null, "Collapsed drawer is distinct from reading screen");
        field.text = "not read by matcher";
        check(Keypad.collapsedHandle(root) == handle, "No field text or label matching needed");
        root.packageName = "another.app";
        check(Keypad.collapsedHandle(root) == null, "Other apps excluded");
        root = collapsedFixture(); root.children.remove(0);
        check(Keypad.collapsedHandle(root) == null, "Requires sign screen context");
        root = collapsedFixture(); root.children.add(node("GridView", Keypad.GRID, ""));
        check(Keypad.collapsedHandle(root) == null, "Visible keypad blocks opening action");
        root.children.get(2).visible = false;
        check(Keypad.collapsedHandle(root) != null, "Hidden keypad does not block collapsed drawer");
        root = collapsedFixture(); root.children.add(node("Button", ReadingScreen.BUTTON, ""));
        check(Keypad.collapsedHandle(root) == null, "Acknowledgement cannot be bypassed");
        root = collapsedFixture(); root.children.get(1).visible = false;
        check(Keypad.collapsedHandle(root) == null, "Hidden drawer excluded");
        root = collapsedFixture(); root.children.get(1).className = "android.widget.FrameLayout";
        check(Keypad.collapsedHandle(root) == null, "Unknown drawer type excluded");
        root = collapsedFixture(); root.children.get(1).children.get(0).enabled = false;
        check(Keypad.collapsedHandle(root) == null, "Disabled handle excluded");
        root = collapsedFixture(); root.children.get(1).children.get(0).children.get(0).clickable = false;
        check(Keypad.collapsedHandle(root) == null, "Nonclickable field excluded");
        root = collapsedFixture(); root.children.get(1).children.get(0).children.get(0).packageName = "another.app";
        check(Keypad.collapsedHandle(root) == null, "Foreign field excluded");
        root = collapsedFixture(); root.children.add(node("EditText", Keypad.FIELD, ""));
        check(Keypad.collapsedHandle(root) == null, "Duplicate fields excluded");
        root = collapsedFixture(); root.children.add(root.children.get(1));
        check(Keypad.collapsedHandle(root) == null, "Duplicate drawers excluded");
        root = collapsedFixture(); root.children.get(1).children.get(0).clickable = false;
        check(Keypad.collapsedHandle(root) == null, "Nonclickable drawer handle excluded");
        root = collapsedFixture(); root.children.get(1).children.get(0).children.get(0).enabled = false;
        check(Keypad.collapsedHandle(root) == null, "Disabled field excludes drawer action");
        check(Keypad.collapsedHandle(fixture()) == null, "Normal PIN keypad unaffected");
        check(Keypad.collapsedHandle(readingFixture(true)) == null, "Reading screen unaffected");
    }

    private static void readingChecks() {
        Keypad.Node root = readingFixture(false);
        ReadingScreen screen = ReadingScreen.match(root);
        check(screen != null, "Disabled acknowledgement screen matches");
        check(screen.actionFor(true, false) == ReadingScreen.Action.SWIPE_UP,
                "Down scrolls text while acknowledgement is disabled");
        check(screen.actionFor(false, true) == ReadingScreen.Action.CONSUME,
                "Centre cannot activate disabled acknowledgement");
        check(screen.actionFor(false, false) == ReadingScreen.Action.PASS_THROUGH,
                "Other keys pass through on reading screen");
        screen.button.enabled = true;
        check(screen.actionFor(false, true) == ReadingScreen.Action.CONTINUE,
                "Centre invokes BankID's own button; BankID enforces reading completion");
        check(screen.actionFor(true, false) == ReadingScreen.Action.SWIPE_UP,
                "Scroll despite misleading enabled flag");
        screen.scrollTarget.scrollForward = false;
        check(screen.actionFor(false, true) == ReadingScreen.Action.CONTINUE,
                "Centre activates enabled acknowledgement after reaching bottom");
        check(screen.actionFor(true, false) == ReadingScreen.Action.SWIPE_UP,
                "Down remains a swipe and never activates acknowledgement");
        root = readingFixture(false);
        root.children.get(0).children.get(0).children.get(0).scrollForward = false;
        screen = ReadingScreen.match(root);
        check(screen != null && !screen.scrollTarget.scrollForward, "No forward action at text bottom");
        check(screen.actionFor(true, false) == ReadingScreen.Action.SWIPE_UP,
                "Bottom-of-text swipe lets BankID observe its touch-based acknowledgement");
        screen.button.enabled = true;
        check(screen.actionFor(false, true) == ReadingScreen.Action.CONTINUE,
                "Ready button works at text bottom");
        root = readingFixture(false); root.packageName = "another.app";
        check(ReadingScreen.match(root) == null, "Reading matcher rejects other apps");
        root = readingFixture(false); root.children.get(0).visible = false;
        check(ReadingScreen.match(root) == null, "Hidden reading screen rejected");
        root = readingFixture(false); root.children.get(0).children.remove(0);
        check(ReadingScreen.match(root) == null, "Missing text container rejected");
        root = readingFixture(false); root.children.get(0).children.get(1).children.clear();
        check(ReadingScreen.match(root) == null, "Missing acknowledgement rejected");
        root = readingFixture(false); root.children.add(node("EditText", Keypad.FIELD, ""));
        check(ReadingScreen.match(root) == null, "PIN screen cannot match reading screen");
        root = readingFixture(false); root.children.add(node("GridView", Keypad.GRID, ""));
        check(ReadingScreen.match(root) == null, "Keypad cannot match reading screen");
        root = readingFixture(false); root.children.add(root.children.get(0));
        check(ReadingScreen.match(root) == null, "Ambiguous fragments rejected");
        root = readingFixture(false);
        Keypad.Node text = root.children.get(0).children.get(0);
        text.children.add(text.children.get(0));
        check(ReadingScreen.match(root) == null, "Ambiguous scroll targets rejected");
        root = readingFixture(false);
        root.children.get(0).children.get(1).children.get(0).clickable = false;
        check(ReadingScreen.match(root) == null, "Nonclickable acknowledgement rejected");
        root = readingFixture(false);
        root.children.get(0).children.get(1).children.get(0).packageName = "another.app";
        check(ReadingScreen.match(root) == null, "Foreign acknowledgement rejected");
        root = readingFixture(true);
        root.children.get(0).children.get(0).children.clear();
        check(ReadingScreen.match(root) == null, "No WebView means no safe swipe target");
        root = readingFixture(true);
        root.children.get(0).children.get(1).children.get(0).text = "Sign with fingerprint";
        check(ReadingScreen.match(root) == null, "Only PIN-entry acknowledgement is supported");
        root = readingFixture(true);
        root.children.get(0).children.get(1).children.get(0).text = "Identifiera med säkerhetskod";
        check(ReadingScreen.match(root) != null, "Swedish PIN-entry acknowledgement");
        root = readingFixture(false);
        text = root.children.get(0).children.get(0);
        Keypad.Node inner = text.children.get(0);
        Keypad.Node host = node("View", "", "");
        host.className = "android.webkit.WebView";
        host.children.add(inner);
        text.children.clear(); text.children.add(host);
        screen = ReadingScreen.match(root);
        check(screen != null && screen.scrollTarget == inner, "Use Chromium virtual WebView, not host");
        check(ReadingScreen.match(fixture()) == null, "Existing keypad is unaffected");
    }

    public static void main(String[] args) {
        readingChecks();
        collapsedChecks();
        Keypad.Node root = fixture();
        Keypad keypad = Keypad.match(root);
        check(keypad != null, "Observed keypad must match even with disabled delete/confirm");
        for (int digit = 0; digit < 10; digit++)
            check(keypad.digits[digit].children.get(0).text.equals(String.valueOf(digit)), "Digit mapping");
        check(!keypad.delete.enabled && !keypad.confirm.enabled, "Preserve disabled state");
        root.packageName = "another.app";
        check(Keypad.match(root) == null, "Never handle another app");
        root = fixture(); root.children.get(0).visible = false;
        check(Keypad.match(root) == null, "Hidden PIN screen");
        root = fixture(); root.children.remove(0);
        check(Keypad.match(root) == null, "Missing PIN field");
        root = fixture(); root.children.get(1).visible = false;
        check(Keypad.match(root) == null, "Hidden keypad");
        root = fixture(); root.children.get(1).children.remove(0);
        check(Keypad.match(root) == null, "Incomplete keypad");
        root = fixture(); root.children.get(1).children.get(0).children.get(0).text = "2";
        root.children.get(1).children.get(0).children.get(0).description = "2";
        check(Keypad.match(root) == null, "Wrong/duplicate digit");
        root = fixture(); root.children.get(1).children.get(5).clickable = false;
        check(Keypad.match(root) == null, "Non-clickable digit");
        root = fixture(); root.children.get(1).children.get(5).enabled = false;
        check(Keypad.match(root) == null, "Disabled digit");
        root = fixture(); root.children.get(1).children.get(11).text = "Unknown action";
        root.children.get(1).children.get(11).children.clear();
        check(Keypad.match(root) == null, "Unknown confirmation");
        root = fixture(); root.children.add(root.children.get(1));
        check(Keypad.match(root) == null, "Ambiguous keypad");
        root = fixture(); root.children.get(1).children.get(11).text = "Skriv under";
        root.children.get(1).children.get(11).children.clear();
        check(Keypad.match(root) != null, "Swedish confirmation");

        root = fixture(); root.children.get(1).children.get(11).text = "Approve";
        root.children.get(1).children.get(11).children.clear();
        keypad = Keypad.match(root);
        check(keypad != null && !keypad.confirm.enabled, "Approval keypad permits digits before confirmation is enabled");
        root.children.get(1).children.get(11).text = "Godkänn";
        check(Keypad.match(root) != null, "Swedish approval keypad recognised");
        root.children.get(1).children.get(11).text = "Approve with fingerprint";
        check(Keypad.match(root) == null, "Approval labels must match exactly");
        root.children.get(1).children.get(11).text = "Approve";
        root.children.get(1).children.get(5).enabled = false;
        check(Keypad.match(root) == null, "Approval still validates every digit button");

        KeyPresses presses = new KeyPresses();
        check(!presses.contains(2, 7, 100), "Unclaimed repeat passes through");
        presses.consume(2, 7, 100);
        check(presses.contains(2, 7, 100), "Claimed repeat consumed");
        check(!presses.contains(3, 7, 100), "Other device independent");
        check(!presses.release(2, 7, 200), "Old key-up cannot release new press");
        check(presses.release(2, 7, 100), "Release consumed even after screen changed");
        check(!presses.release(2, 7, 100), "Release removed");
        presses.consume(2, 7, 300);
        presses.begin(2, 7);
        check(!presses.contains(2, 7, 300), "Fresh press clears stale state");
        presses.consume(2, 7, 400);
        presses.clear();
        check(!presses.contains(2, 7, 400), "Disconnect clears state");
        System.out.println("Passed " + checks + " adapter checks");
    }
}
