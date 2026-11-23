package com.dumbermini.keynavigator;

/** Binds the untranslated PIN activity identity to its accessibility window. */
final class WisePinWindow {
    private int pinWindowId = -1;

    void onWindowState(String packageName, String className, int windowId, boolean fullScreen) {
        if (windowId < 0) return;
        if (WiseKeypad.PACKAGE.equals(packageName) && WiseKeypad.PIN_ACTIVITY.equals(className)) {
            pinWindowId = windowId;
        } else if (windowId == pinWindowId && (fullScreen || !WiseKeypad.PACKAGE.equals(packageName))) {
            reset();
        }
        // Pane/widget notifications in the same activity do not replace its identity.
    }

    void onWindowRemoved(int windowId) { if (windowId == pinWindowId) reset(); }
    void reset() { pinWindowId = -1; }

    String activityForWindow(String packageName, int windowId) {
        return pinWindowId >= 0 && windowId == pinWindowId && WiseKeypad.PACKAGE.equals(packageName)
                ? WiseKeypad.PIN_ACTIVITY : "";
    }
}
