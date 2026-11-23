package com.dumbermini.keynavigator;

public final class WisePinWindowTest {
    private static int checks;
    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
    private static boolean confirmed(WisePinWindow tracker, int window) {
        return WiseKeypad.PIN_ACTIVITY.equals(tracker.activityForWindow(WiseKeypad.PACKAGE, window));
    }
    public static void main(String[] args) {
        WisePinWindow tracker = new WisePinWindow();
        check(!confirmed(tracker, 10), "Startup without an activity event is unconfirmed");
        tracker.onWindowState(WiseKeypad.PACKAGE, "com.wise.ui.AmountActivity", 10, true);
        check(!confirmed(tracker, 10), "Unrelated Wise activity is not a PIN screen");
        tracker.onWindowState(WiseKeypad.PACKAGE, WiseKeypad.PIN_ACTIVITY, 10, true);
        check(confirmed(tracker, 10), "Exact activity confirms its window");
        check(!confirmed(tracker, 11), "Another window cannot inherit the PIN identity");
        check(tracker.activityForWindow("other.app", 10).isEmpty(), "Another package cannot inherit identity");
        tracker.onWindowState(WiseKeypad.PACKAGE, "android.view.View", 10, false);
        check(confirmed(tracker, 10), "Pane/widget notifications do not replace the activity identity");
        tracker.onWindowState("android", "android.app.Dialog", 11, false);
        check(!confirmed(tracker, 11) && confirmed(tracker, 10), "Dialog window is separate from the PIN window");
        tracker.onWindowRemoved(11);
        check(confirmed(tracker, 10), "Closing a dialog preserves the underlying PIN window");
        tracker.onWindowState(WiseKeypad.PACKAGE, "com.wise.ui.AmountActivity", 10, true);
        check(!confirmed(tracker, 10), "New activity in the same window invalidates PIN identity");
        tracker.onWindowState(WiseKeypad.PACKAGE, WiseKeypad.PIN_ACTIVITY, 10, true);
        tracker.onWindowRemoved(10);
        check(!confirmed(tracker, 10), "Removed window cannot remain trusted");
        tracker.onWindowState("other.app", WiseKeypad.PIN_ACTIVITY, 10, true);
        check(!confirmed(tracker, 10), "Activity name also requires Wise package");
        tracker.onWindowState(WiseKeypad.PACKAGE, WiseKeypad.PIN_ACTIVITY, -1, true);
        check(!confirmed(tracker, -1), "Invalid window ID cannot be confirmed");
        tracker.onWindowState(WiseKeypad.PACKAGE, WiseKeypad.PIN_ACTIVITY, 12, false);
        check(confirmed(tracker, 12), "Known PIN activity can also run in a smaller window");
        tracker.reset();
        check(!confirmed(tracker, 12), "Reconnection resets previous identities");
        System.out.println("Passed " + checks + " Wise window identity checks");
    }
}
