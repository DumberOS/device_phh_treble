package com.dumbermini.keynavigator;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.graphics.Rect;
import android.app.KeyguardManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.widget.Toast;
import java.util.IdentityHashMap;
import java.util.Map;
import java.io.FileDescriptor;
import java.io.PrintWriter;

public final class BankIdKeyService extends AccessibilityService {
    private final KeyPresses presses = new KeyPresses();
    private String lastResult = "Waiting for a key";
    private boolean readingGestureInProgress;
    private AppNavigator navigator;
    private AppSettings settings;
    private final SharedPreferences.OnSharedPreferenceChangeListener settingsListener = (preferences, key) -> {
        if (navigator != null) { navigator.clear(); navigator.scheduleRefresh(); }
    };

    @Override public void onCreate() {
        super.onCreate();
        settings = new AppSettings(this);
        settings.register(settingsListener);
    }

    @Override protected void onServiceConnected() {
        if (navigator != null) navigator.destroy();
        navigator = new AppNavigator(this, settings);
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        int type = event.getEventType();
        if (navigator != null && type == AccessibilityEvent.TYPE_VIEW_FOCUSED) navigator.onInputFocusChanged();
        if (navigator != null && type == AccessibilityEvent.TYPE_VIEW_SCROLLED) navigator.onScrolled(event);
        if (navigator != null && (type == AccessibilityEvent.TYPE_WINDOWS_CHANGED
                || type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                || settings.enabled(string(event.getPackageName())))) {
            navigator.scheduleRefresh();
        }
    }

    @Override protected boolean onKeyEvent(KeyEvent event) {
        int key = event.getKeyCode(), device = event.getDeviceId();
        long downTime = event.getDownTime();
        if (event.getAction() == KeyEvent.ACTION_UP) {
            return presses.release(device, key, downTime);
        }
        if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
        if (event.getRepeatCount() > 0) return presses.contains(device, key, downTime);
        presses.begin(device, key);
        if (event.isCanceled() || event.isAltPressed() || event.isCtrlPressed()
                || event.isMetaPressed() || event.isShiftPressed()) return false;
        int digit = digitFor(key);
        boolean back = key == KeyEvent.KEYCODE_BACK;
        boolean delete = back || key == KeyEvent.KEYCODE_DEL || key == KeyEvent.KEYCODE_FORWARD_DEL;
        boolean confirm = key == KeyEvent.KEYCODE_ENTER || key == KeyEvent.KEYCODE_NUMPAD_ENTER
                || key == KeyEvent.KEYCODE_DPAD_CENTER;
        boolean navigation = AppNavigator.handles(key);
        if (digit < 0 && !delete && !confirm && !navigation) return false;
        lastResult = "Received a mapped key";
        KeyguardManager keyguard = getSystemService(KeyguardManager.class);
        if (keyguard != null && keyguard.isKeyguardLocked()) {
            if (navigator != null) navigator.clear();
            return false;
        }
        // Keep other apps on the fast worker path without reading their tree here.
        if (navigation && navigator != null && navigator.handle(key, false)) {
            presses.consume(device, key, downTime);
            lastResult = "Spatial navigation handled key";
            return true;
        }
        if (handleBankIdKey(event)) return true;
        if (handleWiseKey(event)) return true;
        if (navigation && navigator != null && navigator.handle(key, true)) {
            presses.consume(device, key, downTime);
            lastResult = "Additional spatial navigation handled key";
            return true;
        }
        return false;
    }

    private boolean handleBankIdKey(KeyEvent event) {
        int key = event.getKeyCode(), device = event.getDeviceId();
        long downTime = event.getDownTime();
        int digit = digitFor(key);
        boolean back = key == KeyEvent.KEYCODE_BACK;
        boolean delete = back || key == KeyEvent.KEYCODE_DEL || key == KeyEvent.KEYCODE_FORWARD_DEL;
        boolean confirm = key == KeyEvent.KEYCODE_ENTER || key == KeyEvent.KEYCODE_NUMPAD_ENTER
                || key == KeyEvent.KEYCODE_DPAD_CENTER;
        boolean down = key == KeyEvent.KEYCODE_DPAD_DOWN;
        boolean centre = key == KeyEvent.KEYCODE_DPAD_CENTER;
        if (digit < 0 && !delete && !confirm && !down) return false;

        try (Snapshot snapshot = new Snapshot()) {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root == null) { lastResult = "No active window"; return false; }
            if (!Keypad.PACKAGE.contentEquals(root.getPackageName() == null ? "" : root.getPackageName())) {
                root.recycle();
                return false;
            }
            Keypad.Node tree = snapshot.read(root, 0);
            Keypad keypad = Keypad.match(tree);
            if (keypad == null) {
                Keypad.Node collapsedHandle = centre ? Keypad.collapsedHandle(tree) : null;
                if (collapsedHandle != null) {
                    presses.consume(device, key, downTime);
                    AccessibilityNodeInfo handle = snapshot.nodes.get(collapsedHandle);
                    boolean accepted = handle.getActionList().contains(
                            AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK)
                            && handle.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                    lastResult = accepted ? "Keypad opening accepted" : "Keypad opening rejected";
                    if (!accepted) Toast.makeText(this, R.string.action_failed, Toast.LENGTH_SHORT).show();
                    return true;
                }
                ReadingScreen reading = ReadingScreen.match(tree);
                if (reading != null && (down || centre)) {
                    ReadingScreen.Action action = reading.actionFor(down, centre);
                    if (action == ReadingScreen.Action.PASS_THROUGH) return false;
                    presses.consume(device, key, downTime);
                    if (readingGestureInProgress) {
                        lastResult = "Reading swipe in progress";
                        return true;
                    }
                    if (action == ReadingScreen.Action.CONSUME) {
                        lastResult = "Reading screen: waiting for acknowledgement to become available";
                        return true;
                    }
                    if (action == ReadingScreen.Action.SWIPE_UP) {
                        swipeReadingText(snapshot.nodes.get(reading.scrollTarget), root);
                        return true;
                    }
                    // BankID's own click handler enforces its reading requirement. Its visual
                    // greyed-out state and WebView forward action are not reliable state signals.
                    boolean accepted = snapshot.nodes.get(reading.button)
                            .performAction(AccessibilityNodeInfo.ACTION_CLICK);
                    lastResult = accepted ? "Reading continue accepted" : "Reading continue rejected";
                    if (!accepted) Toast.makeText(this, R.string.action_failed, Toast.LENGTH_SHORT).show();
                    return true;
                }
                lastResult = Keypad.PACKAGE.equals(tree.packageName)
                        ? "BankID layout not recognised; " + snapshot.keypadSummary()
                        : "Another app is active";
                return false;
            }
            if (down) return false;
            Keypad.Node target = digit >= 0 ? keypad.digits[digit]
                    : delete ? keypad.delete : keypad.confirm;
            // BankID disables Delete when empty: preserve Back's normal navigation then.
            if (back && !target.enabled) { lastResult = "Empty field: Back passed through"; return false; }
            presses.consume(device, key, downTime);
            boolean accepted = target.enabled && snapshot.nodes.get(target)
                    .performAction(AccessibilityNodeInfo.ACTION_CLICK);
            lastResult = !target.enabled ? "Disabled button" : accepted ? "Click accepted" : "Click rejected";
            if (target.enabled && !accepted) {
                Toast.makeText(this, R.string.action_failed, Toast.LENGTH_SHORT).show();
            }
            return true;
        } catch (IllegalStateException e) {
            lastResult = "Tree changed or exceeded limits";
            // A changing/oversized tree is not a recognised screen. Never log its contents.
            return presses.contains(device, key, downTime);
        }
    }

    private boolean handleWiseKey(KeyEvent event) {
        int key = event.getKeyCode(), device = event.getDeviceId();
        long downTime = event.getDownTime();
        int digit = digitFor(key);
        boolean back = key == KeyEvent.KEYCODE_BACK;
        boolean delete = back || key == KeyEvent.KEYCODE_DEL || key == KeyEvent.KEYCODE_FORWARD_DEL;
        boolean confirm = key == KeyEvent.KEYCODE_DPAD_CENTER || key == KeyEvent.KEYCODE_ENTER
                || key == KeyEvent.KEYCODE_NUMPAD_ENTER;
        if (digit < 0 && !delete && !confirm) return false;
        AccessibilityNodeInfo root = focusedRoot();
        if (root == null) return false;
        if (!WiseKeypad.PACKAGE.contentEquals(root.getPackageName())) { root.recycle(); return false; }
        try (WisePinScreen screen = WisePinScreen.read(root)) {
            if (screen.keypad == null) { lastResult = "Wise PIN layout not recognised"; return false; }
            Keypad.Node target = digit >= 0 ? screen.keypad.digits[digit]
                    : delete ? screen.keypad.delete : screen.keypad.confirm;
            if (back && !target.enabled) { lastResult = "Wise empty field: Back passed through"; return false; }
            presses.consume(device, key, downTime);
            boolean accepted = screen.click(target);
            lastResult = !target.enabled ? "Wise button disabled"
                    : accepted ? "Wise keypad click accepted" : "Wise keypad click rejected";
            if (target.enabled && !accepted) Toast.makeText(this, R.string.wise_action_failed, Toast.LENGTH_SHORT).show();
            return true;
        } catch (IllegalStateException e) {
            lastResult = "Wise tree changed or exceeded limits";
            return presses.contains(device, key, downTime);
        }
    }

    private AccessibilityNodeInfo focusedRoot() {
        AccessibilityNodeInfo root = null;
        boolean focused = false;
        for (AccessibilityWindowInfo window : getWindows()) {
            try {
                if (!focused && window.isFocused()
                        && window.getType() != AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY) {
                    focused = true;
                    root = android.os.Build.VERSION.SDK_INT >= 33 ? window.getRoot(0) : window.getRoot();
                }
            } finally { window.recycle(); }
        }
        return focused ? root : getRootInActiveWindow();
    }

    private void swipeReadingText(AccessibilityNodeInfo text, AccessibilityNodeInfo root) {
        Rect bounds = new Rect(), window = new Rect();
        text.getBoundsInScreen(bounds);
        root.getBoundsInScreen(window);
        android.util.DisplayMetrics display = getResources().getDisplayMetrics();
        if (!bounds.intersect(window) || !bounds.intersect(0, 0, display.widthPixels, display.heightPixels)
                || bounds.width() < 48 || bounds.height() < 48) {
            lastResult = "Reading text bounds unavailable";
            return;
        }
        float x = bounds.exactCenterX();
        Path path = new Path();
        path.moveTo(x, bounds.top + bounds.height() * 0.8f);
        path.lineTo(x, bounds.top + bounds.height() * 0.2f);
        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(path, 0, 350)).build();
        readingGestureInProgress = true;
        boolean accepted = dispatchGesture(gesture, new GestureResultCallback() {
            @Override public void onCompleted(GestureDescription gesture) {
                readingGestureInProgress = false;
                lastResult = "Reading swipe completed";
            }
            @Override public void onCancelled(GestureDescription gesture) {
                readingGestureInProgress = false;
                lastResult = "Reading swipe cancelled";
            }
        }, null);
        if (!accepted) {
            readingGestureInProgress = false;
            lastResult = "Reading swipe rejected";
            Toast.makeText(this, R.string.action_failed, Toast.LENGTH_SHORT).show();
        } else lastResult = "Reading swipe dispatched";
    }

    private static int digitFor(int key) {
        if (key >= KeyEvent.KEYCODE_0 && key <= KeyEvent.KEYCODE_9) return key - KeyEvent.KEYCODE_0;
        if (key >= KeyEvent.KEYCODE_NUMPAD_0 && key <= KeyEvent.KEYCODE_NUMPAD_9)
            return key - KeyEvent.KEYCODE_NUMPAD_0;
        return -1;
    }

    @Override public void onInterrupt() {
        presses.clear();
        if (navigator != null) navigator.clear();
    }
    @Override protected void dump(FileDescriptor fd, PrintWriter writer, String[] args) {
        writer.println("Key Navigator: " + lastResult);
        android.accessibilityservice.AccessibilityServiceInfo info = getServiceInfo();
        if (info == null) { writer.println("Accessibility service is connecting"); return; }
        writer.println("BankID custom controls enabled: true");
        writer.println("BankID standard navigation enabled: " + settings.enabled(Keypad.PACKAGE));
        writer.println("Wise custom controls enabled: true");
        writer.println("Wise standard navigation enabled: " + settings.enabled(WiseKeypad.PACKAGE));
        if (navigator != null) navigator.dump(writer);
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            writer.println("Accessibility tool: " + info.isAccessibilityTool());
        }
        for (AccessibilityWindowInfo window : getWindows()) {
            try {
                writer.println("Window: id=" + window.getId() + " type=" + window.getType()
                        + " active=" + window.isActive() + " focused=" + window.isFocused());
            } finally { window.recycle(); }
        }
        try (Snapshot snapshot = new Snapshot()) {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root == null) {
                for (AccessibilityWindowInfo window : getWindows()) {
                    try {
                        if (root == null && window.isFocused()
                                && window.getType() == AccessibilityWindowInfo.TYPE_APPLICATION) {
                            root = window.getRoot();
                            writer.println("Focused application root available: " + (root != null));
                        }
                    } finally { window.recycle(); }
                }
            }
            if (root == null) { writer.println("Current window: unavailable"); return; }
            if ("se.bankgirot.swish".equals(string(root.getPackageName()))) {
                writer.println("Current window: Swish (read-only inspection)");
                int[] counts = new int[2];
                try {
                    dumpSwishStructure(writer, root, 0, counts);
                    writer.println("Swish nodes=" + counts[0] + " visible enabled clickable=" + counts[1]);
                } finally { root.recycle(); }
                return;
            }
            if (WiseKeypad.PACKAGE.equals(string(root.getPackageName()))) {
                try (WisePinScreen screen = WisePinScreen.read(root)) {
                    writer.println("Current window: Wise");
                    writer.println("Wise keypad recognised: " + (screen.keypad != null));
                    if (screen.keypad != null) writer.println("Wise Delete enabled: " + screen.keypad.delete.enabled);
                }
                return;
            }
            if (!Keypad.PACKAGE.equals(string(root.getPackageName()))) {
                root.recycle();
                writer.println("Current window: another app");
                return;
            }
            Keypad.Node tree = snapshot.read(root, 0);
            Keypad keypad = Keypad.match(tree);
            writer.println("Current window: BankID; " + snapshot.keypadSummary());
            writer.println("Keypad recognised: " + (keypad != null));
            writer.println("Collapsed keypad recognised: " + (Keypad.collapsedHandle(tree) != null));
            ReadingScreen reading = ReadingScreen.match(tree);
            writer.println("Reading screen recognised: " + (reading != null));
            if (reading != null) {
                writer.println("Reading continue enabled: " + reading.button.enabled);
                writer.println("Reading scroll available: " + reading.scrollTarget.scrollForward);
            }
            if (keypad != null) writer.println("Delete enabled: " + keypad.delete.enabled);
            else if (reading == null) {
                snapshot.dumpStructure(writer, tree, "");
            }
        } catch (IllegalStateException e) {
            writer.println("Current window: tree changed or exceeded limits");
        }
    }

    private static void dumpSwishStructure(PrintWriter writer, AccessibilityNodeInfo node,
            int depth, int[] counts) {
        if (depth > 40 || ++counts[0] > 300) throw new IllegalStateException("Tree limit");
        if (!"se.bankgirot.swish".equals(string(node.getPackageName()))) return;
        Rect bounds = new Rect();
        node.getBoundsInScreen(bounds);
        boolean target = node.isVisibleToUser() && node.isEnabled() && node.isClickable()
                && !bounds.isEmpty();
        if (target) counts[1]++;
        writer.println("Node: depth=" + depth + " class=" + string(node.getClassName())
                + " id=" + string(node.getViewIdResourceName())
                + " visible=" + node.isVisibleToUser() + " enabled=" + node.isEnabled()
                + " clickable=" + node.isClickable() + " focusable=" + node.isFocusable()
                + " clickAction=" + node.getActionList().contains(
                        AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK)
                + " scrollable=" + node.isScrollable() + " bounds=" + bounds.toShortString());
        // Never read labels, descriptions, or editable field values in this inspector.
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                try { dumpSwishStructure(writer, child, depth + 1, counts); }
                finally { child.recycle(); }
            }
        }
    }
    @Override public boolean onUnbind(Intent intent) {
        presses.clear();
        readingGestureInProgress = false;
        if (navigator != null) { navigator.destroy(); navigator = null; }
        return super.onUnbind(intent);
    }

    @Override public void onDestroy() {
        if (navigator != null) { navigator.destroy(); navigator = null; }
        settings.unregister(settingsListener);
        super.onDestroy();
    }

    private static String string(CharSequence value) { return value == null ? "" : value.toString(); }

    private static final class Snapshot implements AutoCloseable {
        final Map<Keypad.Node, AccessibilityNodeInfo> nodes = new IdentityHashMap<>();

        Keypad.Node read(AccessibilityNodeInfo info, int depth) {
            Keypad.Node node = new Keypad.Node();
            nodes.put(node, info);
            if (depth > 40 || nodes.size() > 300) throw new IllegalStateException("Tree limit");
            node.packageName = string(info.getPackageName());
            node.id = string(info.getViewIdResourceName());
            node.className = string(info.getClassName());
            node.visible = info.isVisibleToUser();
            node.enabled = info.isEnabled();
            node.clickable = info.isClickable();
            node.scrollable = info.isScrollable();
            node.scrollForward = info.getActionList().contains(
                    AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD);
            // Ignore PIN field content entirely, including when BankID does not flag it as a password.
            if (Keypad.PACKAGE.equals(node.packageName) && !info.isPassword() && !Keypad.FIELD.equals(node.id)
                    && !"android.widget.EditText".equals(node.className)) {
                node.text = string(info.getText());
                node.description = string(info.getContentDescription());
            }
            if (Keypad.PACKAGE.equals(node.packageName) && node.visible) {
                for (int i = 0; i < info.getChildCount(); i++) {
                    AccessibilityNodeInfo child = info.getChild(i);
                    if (child != null) node.children.add(read(child, depth + 1));
                }
            }
            return node;
        }

        @Override public void close() {
            for (AccessibilityNodeInfo node : nodes.values()) node.recycle();
            nodes.clear();
        }

        void dumpStructure(PrintWriter writer, Keypad.Node node, String indent) {
            writer.println(indent + "Node: " + node.className + " id=" + node.id
                    + " visible=" + node.visible + " enabled=" + node.enabled
                    + " clickable=" + node.clickable + " scrollable=" + node.scrollable
                    + " scrollForward=" + node.scrollForward);
            for (Keypad.Node child : node.children) dumpStructure(writer, child, indent + "  ");
        }

        String keypadSummary() {
            for (Keypad.Node node : nodes.keySet()) {
                if (Keypad.GRID.equals(node.id)) {
                    return "grid children=" + node.children.size();
                }
            }
            return "no keypad grid";
        }
    }
}
