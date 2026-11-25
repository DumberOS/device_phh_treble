package com.dumbermini.keynavigator;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Rect;
import android.graphics.Path;
import android.os.Build;
import android.os.Handler;
import android.os.Bundle;
import android.os.HandlerThread;
import java.util.concurrent.atomic.AtomicBoolean;
import java.io.StringWriter;
import android.os.Looper;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

/** Opt-in spatial navigation; no text, descriptions or field values are read. */
final class AppNavigator {
    // Match our depth-first walk and finish each batch before requesting the next child.
    // Interruptible prefetch can otherwise be cancelled by our own next request.
    private static final int TREE_PREFETCH = AccessibilityNodeInfo.FLAG_PREFETCH_DESCENDANTS_DEPTH_FIRST
            | AccessibilityNodeInfo.FLAG_PREFETCH_UNINTERRUPTIBLE;
    private final AccessibilityService service;
    private final AppSettings settings;
    private final SelectionOverlay overlay;
    private final HandlerThread worker = new HandlerThread("KeyNavigator");
    private final Handler handler;
    private final AtomicBoolean keyPending = new AtomicBoolean();
    private volatile int activeWindowId = -1;
    private volatile String readyPackage = "";
    private volatile boolean numberMovementReady;
    private volatile int focusGeneration;
    private volatile int checkedFocusGeneration;
    private volatile boolean destroyed;
    private volatile long selectionRevision;
    private volatile String diagnostics = "Navigation starting";
    private List<AccessibilityNodeInfo> owned = new ArrayList<>();
    private List<Target> targets = new ArrayList<>();
    private Target selected;
    private String activePackage = "";
    private boolean scheduled;
    private boolean textEntryFocused;
    private AccessibilityNodeInfo scrollingContainer;
    private SpatialNavigation.Box scrollAnchor;
    private ScrollNavigation.Settling scrollSettling;
    private boolean scrollGestureInProgress;
    private long scrollGeneration;
    private long lastScrollToHighlightMs;
    private String status = "Waiting for an enabled app";
    private final Runnable refreshTask = () -> { scheduled = false; refresh(); };
    private final BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) clear();
            else scheduleRefresh();
        }
    };

    AppNavigator(AccessibilityService service, AppSettings settings) {
        this.service = service;
        this.settings = settings;
        overlay = new SelectionOverlay(service);
        worker.start();
        handler = new Handler(worker.getLooper());
        IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_USER_PRESENT);
        if (Build.VERSION.SDK_INT >= 33) service.registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        else service.registerReceiver(screenReceiver, filter);
        scheduleRefresh();
    }

    static boolean handles(int key) {
        return key == KeyEvent.KEYCODE_DPAD_LEFT || key == KeyEvent.KEYCODE_DPAD_RIGHT
                || key == KeyEvent.KEYCODE_DPAD_UP || key == KeyEvent.KEYCODE_DPAD_DOWN
                || key == KeyEvent.KEYCODE_DPAD_CENTER || movementOnlyKey(key);
    }

    private static boolean movementOnlyKey(int key) {
        return key == KeyEvent.KEYCODE_2 || key == KeyEvent.KEYCODE_5
                || key == KeyEvent.KEYCODE_NUMPAD_2 || key == KeyEvent.KEYCODE_NUMPAD_5;
    }

    void onInputFocusChanged() {
        // Pass number keys through immediately while the worker checks the new input focus.
        focusGeneration++;
        scheduleRefresh();
    }

    void scheduleRefresh() {
        if (destroyed) return;
        if (Looper.myLooper() != handler.getLooper()) { handler.post(this::scheduleRefresh); return; }
        if (!scheduled) { scheduled = true; handler.postDelayed(refreshTask, 60); }
    }

    private long lastRefreshMs, maxRefreshMs, lastKeyMs, maxKeyMs;

    // Android waits only 500 ms for this result. Never read the UI tree or act here.
    private volatile long maxDispatchMs;
    boolean handle(int key, boolean includeCustomApps) {
        long started = SystemClock.uptimeMillis();
        try { return enqueueKey(key, includeCustomApps); }
        finally { maxDispatchMs = Math.max(maxDispatchMs, SystemClock.uptimeMillis() - started); }
    }

    private boolean enqueueKey(int key, boolean includeCustomApps) {
        final String expectedPackage = readyPackage;
        // Custom controls get first refusal on PIN digits (including 2/5), Centre and
        // reading-screen Down. Capture the package once across this decision.
        if (destroyed || expectedPackage.isEmpty()
                || (!includeCustomApps && AppPolicy.hasCustomControls(expectedPackage))
                || !settings.enabled(expectedPackage)
                || (movementOnlyKey(key) && (!numberMovementReady || focusGeneration != checkedFocusGeneration))) return false;
        boolean focused = false;
        for (AccessibilityWindowInfo window : service.getWindows()) {
            try {
                if (window.isFocused() && window.getType() != AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY)
                    focused = window.getId() == activeWindowId;
            } finally { window.recycle(); }
        }
        if (!focused) { scheduleRefresh(); return false; }
        // Consume promptly and allow only one pending action, including while a scan is running.
        if (keyPending.compareAndSet(false, true)) {
            final long expectedSelection = selectionRevision;
            handler.post(() -> {
                long started = SystemClock.uptimeMillis();
                try {
                    if (!destroyed && expectedPackage.equals(activePackage)) handleKey(key, expectedPackage, expectedSelection);
                } finally {
                    lastKeyMs = SystemClock.uptimeMillis() - started;
                    maxKeyMs = Math.max(maxKeyMs, lastKeyMs);
                    updateDiagnostics();
                    keyPending.set(false);
                }
            });
        }
        return true;
    }

    private boolean handleKey(int key, String expectedPackage, long expectedSelection) {
        boolean sameSelection = refresh();
        if (!expectedPackage.equals(activePackage)) return false;
        if (movementOnlyKey(key)) {
            if (selected == null || selected.node.isEditable()
                    || textEntryFocused) return false;
        }
        if (selected == null) { status = "No visible targets"; return true; }
        if (scrollingContainer != null) {
            status = "Waiting for list scroll to settle";
            return true;
        }
        if (key == KeyEvent.KEYCODE_DPAD_CENTER) {
            if (selected.action == 0) { status = "Scrollable region selected; use Up/Down to scroll"; return true; }
            // Do not click a replacement element when the highlighted one disappeared or moved.
            if (!sameSelection || expectedSelection != selectionRevision) { status = "Selection changed; press Centre again"; return true; }
            boolean clicked = selected.node.performAction(selected.action);
            status = clicked ? "Activation accepted" : "Activation rejected";
            if (clicked) overlay.hide();
            scheduleRefresh();
            return true;
        }
        SpatialNavigation.Direction direction = key == KeyEvent.KEYCODE_DPAD_LEFT
                ? SpatialNavigation.Direction.LEFT : key == KeyEvent.KEYCODE_DPAD_RIGHT
                ? SpatialNavigation.Direction.RIGHT : key == KeyEvent.KEYCODE_DPAD_UP
                        || key == KeyEvent.KEYCODE_2 || key == KeyEvent.KEYCODE_NUMPAD_2
                ? SpatialNavigation.Direction.UP : SpatialNavigation.Direction.DOWN;
        if ((key == KeyEvent.KEYCODE_DPAD_UP || key == KeyEvent.KEYCODE_DPAD_DOWN)
                && nearScrollEdge(direction) && scrollList(direction == SpatialNavigation.Direction.DOWN)) {
            return true;
        }
        List<SpatialNavigation.Box> boxes = boxes();
        selected = targets.get(SpatialNavigation.next(boxes, targets.indexOf(selected), direction));
        overlay.show(selected.bounds);
        status = "Selection moved";
        return true;
    }

    private List<SpatialNavigation.Box> boxes() {
        List<SpatialNavigation.Box> boxes = new ArrayList<>();
        for (Target target : targets) boxes.add(target.box());
        return boxes;
    }

    private boolean nearScrollEdge(SpatialNavigation.Direction direction) {
        AccessibilityNodeInfo container = scrollContainer(selected);
        if (container == null) return false;
        List<AccessibilityNodeInfo> rows = new ArrayList<>();
        List<SpatialNavigation.Box> inside = new ArrayList<>();
        Rect viewport = new Rect();
        container.getBoundsInScreen(viewport);
        List<SpatialNavigation.Box> bars = new ArrayList<>();
        for (Target target : targets) {
            if (target.node.equals(container) || target.ancestors.contains(container)) continue;
            for (AccessibilityNodeInfo ancestor : target.ancestors) {
                AccessibilityNodeInfo.CollectionInfo info = ancestor.getCollectionInfo();
                if (info != null && info.getRowCount() == 1 && info.getColumnCount() > 1
                        && ancestor.isVisibleToUser()) {
                    Rect bar = new Rect();
                    ancestor.getBoundsInScreen(bar);
                    bars.add(new SpatialNavigation.Box(bar.left, bar.top, bar.right, bar.bottom));
                }
            }
        }
        SpatialNavigation.Box visible = SpatialNavigation.withoutEdgeBars(
                new SpatialNavigation.Box(viewport.left, viewport.top, viewport.right, viewport.bottom), bars);
        viewport.set(visible.left, visible.top, visible.right, visible.bottom);
        AccessibilityNodeInfo selectedRow = collectionRow(selected, container);
        int current = -1;
        for (Target target : targets) {
            if (target.node.equals(container) || target.ancestors.contains(container)) {
                AccessibilityNodeInfo row = collectionRow(target, container);
                int index = rows.indexOf(row);
                if (index < 0) {
                    Rect bounds = new Rect();
                    row.getBoundsInScreen(bounds);
                    if (!row.equals(selectedRow) && !SpatialNavigation.rowCenterVisible(
                            new SpatialNavigation.Box(bounds.left, bounds.top, bounds.right, bounds.bottom), visible)) continue;
                    if (!bounds.intersect(viewport) || bounds.isEmpty()) continue;
                    index = rows.size();
                    rows.add(row);
                    inside.add(new SpatialNavigation.Box(bounds.left, bounds.top, bounds.right, bounds.bottom));
                }
                if (target == selected) current = index;
            }
        }
        return SpatialNavigation.nearVerticalEdge(inside, current, direction);
    }

    private static AccessibilityNodeInfo collectionRow(Target target, AccessibilityNodeInfo container) {
        // An email's avatar, star and attachment are controls within one collection row.
        // They must not count as extra rows between selection and the visible edge.
        int parent = target.ancestors.indexOf(container);
        if (parent >= 0 && container.getCollectionInfo() != null) {
            for (int i = parent + 1; i < target.ancestors.size(); i++) {
                AccessibilityNodeInfo ancestor = target.ancestors.get(i);
                if (ancestor.getCollectionItemInfo() != null) return ancestor;
            }
            // Compose lists may expose collection size but omit item metadata.
            // A clickable direct child still identifies the enclosing row.
            if (container.getCollectionInfo().getColumnCount() == 1
                    && parent + 1 < target.ancestors.size()) {
                AccessibilityNodeInfo child = target.ancestors.get(parent + 1);
                if (child.isClickable()) return child;
            }
        }
        return target.node;
    }

    private static ScrollNavigation.Support scrollSupport(AccessibilityNodeInfo node) {
        List<AccessibilityNodeInfo.AccessibilityAction> actions = node.getActionList();
        boolean up = actions.contains(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_UP);
        boolean down = actions.contains(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_DOWN);
        String className = node.getClassName() == null ? "" : node.getClassName().toString();
        AccessibilityNodeInfo.CollectionInfo collection = node.getCollectionInfo();
        boolean horizontal = "android.widget.HorizontalScrollView".equals(className)
                || (!up && !down && ScrollNavigation.horizontalPager(
                        String.valueOf(node.getPackageName()), node.getViewIdResourceName(), className))
                || (!up && !down && collection != null && collection.getRowCount() == 1
                        && collection.getColumnCount() > 1)
                || (!up && !down && (actions.contains(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_LEFT)
                || actions.contains(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_RIGHT)));
        // A RecyclerView can merely lay out items inside an outer scrolling page.
        // Its class/collection metadata does not mean it owns scrolling.
        return new ScrollNavigation.Support(node.isScrollable(), node.isEnabled(), up, down,
                actions.contains(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD),
                actions.contains(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD), horizontal);
    }

    private static AccessibilityNodeInfo scrollContainer(Target target) {
        List<ScrollNavigation.Support> supports = new ArrayList<>();
        int firstWebView = -1;
        for (int i = 0; i < target.ancestors.size(); i++) {
            AccessibilityNodeInfo ancestor = target.ancestors.get(i);
            supports.add(scrollSupport(ancestor));
            if (firstWebView < 0 && "android.webkit.WebView".contentEquals(
                    ancestor.getClassName() == null ? "" : ancestor.getClassName())) firstWebView = i;
        }
        if (firstWebView < 0 && "android.webkit.WebView".contentEquals(
                target.node.getClassName() == null ? "" : target.node.getClassName())) firstWebView = supports.size();
        int bodyOwner = ScrollNavigation.bodyScrollOwner(String.valueOf(target.node.getPackageName()), firstWebView, supports);
        if (bodyOwner >= 0) return target.ancestors.get(bodyOwner);
        if (scrollSupport(target.node).verticalContainer()) return target.node;
        int index = ScrollNavigation.nearest(supports);
        return index < 0 ? null : target.ancestors.get(index);
    }

    private boolean scrollList(boolean down) {
        AccessibilityNodeInfo container = scrollContainer(selected);
        if (container == null) return false;
        ScrollNavigation.Action action = ScrollNavigation.action(scrollSupport(container), down);
        int androidAction;
        switch (action) {
            case UP: androidAction = AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_UP.getId(); break;
            case DOWN: androidAction = AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_DOWN.getId(); break;
            case BACKWARD: androidAction = AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD; break;
            case FORWARD: androidAction = AccessibilityNodeInfo.ACTION_SCROLL_FORWARD; break;
            default: return false; // At the actual list boundary, allow ordinary movement.
        }
        // Verified on-device: Proton's mailbox accepts both DOWN/FORWARD but never moves.
        // Limit the touch workaround to this known list, not arbitrary Proton controls.
        if (ScrollNavigation.needsTouchScroll(activePackage, container.getViewIdResourceName()))
            return scrollWithGesture(container, down);
        int row = immediateScrollRow(container, down);
        long scrollStarted = SystemClock.uptimeMillis();
        boolean immediate = false;
        if (row >= 0) {
            Bundle arguments = new Bundle();
            arguments.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_ROW_INT, row);
            arguments.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_COLUMN_INT, 0);
            immediate = container.performAction(
                    AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_TO_POSITION.getId(), arguments);
        }
        boolean accepted = immediate || container.performAction(androidAction);
        status = accepted ? (immediate ? "Row jump accepted: " + row
                : down ? "List scroll down accepted" : "List scroll up accepted") : "List scroll rejected";
        if (accepted) {
            clearScrollAnchor();
            scrollingContainer = AccessibilityNodeInfo.obtain(container);
            scrollAnchor = selected.box();
            scrollSettling = new ScrollNavigation.Settling(scrollStarted, immediate);
            overlay.clearHighlight();
            scheduleRefresh();
            handler.postDelayed(settleScroll, scrollSettling.remaining(SystemClock.uptimeMillis()));
        }
        return accepted;
    }

    private boolean scrollWithGesture(AccessibilityNodeInfo container, boolean down) {
        Rect bounds = new Rect();
        container.getBoundsInScreen(bounds);
        android.util.DisplayMetrics display = service.getResources().getDisplayMetrics();
        if (!bounds.intersect(0, 0, display.widthPixels, display.heightPixels)) return false;
        ScrollNavigation.Swipe swipe = ScrollNavigation.swipe(bounds.left, bounds.top, bounds.right, bounds.bottom, down);
        if (swipe == null) return false;
        Path path = new Path();
        path.moveTo(swipe.x, swipe.fromY);
        path.lineTo(swipe.x, swipe.toY);
        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(path, 0, 200)).build();
        clearScrollAnchor();
        scrollingContainer = AccessibilityNodeInfo.obtain(container);
        final long request = scrollGeneration;
        scrollAnchor = selected.box();
        scrollSettling = new ScrollNavigation.Settling(SystemClock.uptimeMillis(), false);
        scrollGestureInProgress = true;
        overlay.clearHighlight();
        boolean accepted = service.dispatchGesture(gesture, new AccessibilityService.GestureResultCallback() {
            @Override public void onCompleted(GestureDescription ignored) { finished(false); }
            @Override public void onCancelled(GestureDescription ignored) { finished(true); }
            private void finished(boolean cancelled) {
                if (scrollingContainer == null || scrollGeneration != request) return;
                scrollGestureInProgress = false;
                status = cancelled ? "List swipe cancelled" : "List swipe completed";
                handler.removeCallbacks(settleScroll);
                handler.postDelayed(settleScroll, scrollSettling.remaining(SystemClock.uptimeMillis()));
            }
        }, handler);
        if (!accepted) clearScrollAnchor();
        status = accepted ? "List swipe dispatched" : "List swipe rejected";
        return accepted;
    }

    private static int immediateScrollRow(AccessibilityNodeInfo container, boolean down) {
        if (!container.getActionList().contains(
                AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_TO_POSITION)) return -1;
        AccessibilityNodeInfo.CollectionInfo collection = container.getCollectionInfo();
        if (collection == null || collection.getColumnCount() != 1) return -1;
        Rect viewport = new Rect();
        container.getBoundsInScreen(viewport);
        int first = -1, last = -1, firstComplete = -1, previousTop = Integer.MIN_VALUE;
        for (int i = 0; i < container.getChildCount(); i++) {
            AccessibilityNodeInfo child = container.getChild(i);
            if (child == null) continue;
            try {
                Rect bounds = new Rect();
                child.getBoundsInScreen(bounds);
                if (!child.isVisibleToUser() || !bounds.intersect(viewport)
                        || bounds.height() <= 2 || bounds.width() <= 2) continue;
                AccessibilityNodeInfo.CollectionItemInfo item = child.getCollectionItemInfo();
                // Only use unambiguous, ascending single-column rows; other layouts fall back.
                if (item == null || item.getRowSpan() != 1 || item.getRowIndex() <= last
                        || bounds.top < previousTop) return -1;
                if (first < 0) first = item.getRowIndex();
                Rect parentBounds = new Rect();
                child.getBoundsInParent(parentBounds);
                if (firstComplete < 0 && ScrollNavigation.completeRowHeight(
                        bounds.height(), parentBounds.height())) firstComplete = item.getRowIndex();
                last = item.getRowIndex();
                previousTop = bounds.top;
            } finally { child.recycle(); }
        }
        return ScrollNavigation.adjacentRow(first, last, firstComplete, collection.getRowCount(), down);
    }

    private final Runnable settleScroll = this::finishScroll;

    private void finishScroll() {
        // One final refresh anchors to the settled list before returning to normal navigation.
        if (scrollGestureInProgress) return;
        if (scrollingContainer != null) {
            long remaining = scrollSettling.remaining(SystemClock.uptimeMillis());
            if (remaining > 0) handler.postDelayed(settleScroll, remaining);
            else refresh();
        }
    }

    void onScrolled(AccessibilityEvent event) {
        if (destroyed) return;
        AccessibilityEvent copy = AccessibilityEvent.obtain(event);
        handler.post(() -> {
            try { handleScrolled(copy); } finally { copy.recycle(); }
        });
    }

    private void handleScrolled(AccessibilityEvent event) {
        if (scrollingContainer == null) return;
        AccessibilityNodeInfo source = event.getSource();
        try {
            if (source != null && source.equals(scrollingContainer)) {
                long now = SystemClock.uptimeMillis();
                scrollSettling.onEvent(event.getEventTime(), now);
                handler.removeCallbacks(settleScroll);
                handler.postDelayed(settleScroll, scrollSettling.remaining(now));
            }
        } finally { if (source != null) source.recycle(); }
    }

    private void clearScrollAnchor() {
        scrollGeneration++;
        handler.removeCallbacks(settleScroll);
        if (scrollingContainer != null) scrollingContainer.recycle();
        scrollingContainer = null; scrollAnchor = null; scrollSettling = null;
        scrollGestureInProgress = false;
    }

    private static Target closestInContainer(List<Target> targets, AccessibilityNodeInfo container,
            SpatialNavigation.Box anchor) {
        List<Target> inside = new ArrayList<>();
        List<SpatialNavigation.Box> boxes = new ArrayList<>();
        for (Target target : targets) {
            if (target.node.equals(container) || target.ancestors.contains(container)) {
                inside.add(target); boxes.add(target.box());
            }
        }
        int index = SpatialNavigation.closest(boxes, anchor);
        return index < 0 ? null : inside.get(index);
    }

    private AccessibilityNodeInfo foregroundRoot() {
        AccessibilityNodeInfo root = null;
        boolean focused = false;
        for (AccessibilityWindowInfo window : service.getWindows()) {
            try {
                if (!focused && window.isFocused()
                        && window.getType() != AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY) {
                    focused = true;
                    activeWindowId = window.getId();
                    // Window.getRoot bypasses cache. Fetch just the root here; child reads
                    // batch only the subtrees that actually need repopulating.
                    root = Build.VERSION.SDK_INT >= 33 ? window.getRoot(0) : window.getRoot();
                }
            } finally { window.recycle(); }
        }
        return focused ? root : Build.VERSION.SDK_INT >= 33
                ? service.getRootInActiveWindow(TREE_PREFETCH) : service.getRootInActiveWindow();
    }

    /** Returns whether the previously highlighted target still occupies the same bounds. */
    private boolean refresh() {
        long started = SystemClock.uptimeMillis();
        try { return refreshTree(); }
        finally {
            lastRefreshMs = SystemClock.uptimeMillis() - started;
            maxRefreshMs = Math.max(maxRefreshMs, lastRefreshMs);
            updateDiagnostics();
        }
    }

    private boolean refreshTree() {
        KeyguardManager keyguard = service.getSystemService(KeyguardManager.class);
        android.os.PowerManager power = service.getSystemService(android.os.PowerManager.class);
        if ((keyguard != null && keyguard.isKeyguardLocked()) || (power != null && !power.isInteractive())) {
            clear(); return false;
        }
        AccessibilityNodeInfo root = foregroundRoot();
        String packageName = root == null || root.getPackageName() == null ? "" : root.getPackageName().toString();
        if (root == null || !settings.enabled(packageName)) {
            if (root != null) root.recycle();
            clear(); return false;
        }
        // Do not follow transient nodes or show a new highlight during an animated scroll.
        if (packageName.equals(activePackage) && scrollingContainer != null
                && (scrollGestureInProgress || scrollSettling.remaining(SystemClock.uptimeMillis()) > 0)) {
            root.recycle();
            overlay.clearHighlight();
            return false;
        }
        List<AccessibilityNodeInfo> nextOwned = new ArrayList<>();
        List<Target> nextTargets = new ArrayList<>();
        try {
            int focusVersion = focusGeneration;
            AccessibilityNodeInfo input = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
            try {
                textEntryFocused = input != null && (input.isEditable() || input.isPassword()
                        || "android.widget.EditText".contentEquals(input.getClassName() == null ? "" : input.getClassName()));
                if (textEntryFocused) numberMovementReady = false;
                checkedFocusGeneration = focusVersion;
            } finally { if (input != null) input.recycle(); }
            Rect viewport = new Rect();
            root.getBoundsInScreen(viewport);
            android.util.DisplayMetrics metrics = service.getResources().getDisplayMetrics();
            if (!viewport.intersect(0, 0, metrics.widthPixels, metrics.heightPixels)) viewport.setEmpty();
            collect(root, packageName, 0, viewport, new ArrayList<>(), nextOwned, nextTargets);
            Target previous = packageName.equals(activePackage) ? selected : null, next = null;
            if (!packageName.equals(activePackage)) clearScrollAnchor();
            if (scrollingContainer != null) {
                next = closestInContainer(nextTargets, scrollingContainer, scrollAnchor);
                if (next == null) clearScrollAnchor();
            }
            for (Target target : nextTargets) {
                if (next != null) break;
                if (previous != null && previous.node.equals(target.node)
                        && previous.action == target.action) { next = target; break; }
            }
            boolean same = next != null && previous != null && previous.node.equals(next.node)
                    && previous.bounds.equals(next.bounds) && scrollingContainer == null;
            if (next == null && previous != null) {
                AccessibilityNodeInfo container = scrollContainer(previous);
                if (container != null) next = closestInContainer(nextTargets, container, previous.box());
            }
            releaseNodes();
            owned = nextOwned; targets = nextTargets; selected = next; activePackage = packageName;
            if (selected == null && !targets.isEmpty()) selected = targets.get(SpatialNavigation.initial(boxes()));
            if (selected == null) overlay.hide(); else overlay.show(selected.bounds);
            if (scrollingContainer != null) {
                lastScrollToHighlightMs = SystemClock.uptimeMillis() - scrollSettling.started;
                clearScrollAnchor();
            }
            if (!same) selectionRevision++;
            return same;
        } catch (IllegalStateException e) {
            if (nextOwned != owned) for (AccessibilityNodeInfo node : nextOwned) node.recycle();
            clear();
            status = "Tree changed or exceeded limits";
            return false;
        }
    }

    private static void collect(AccessibilityNodeInfo node, String packageName, int depth, Rect viewport,
            List<AccessibilityNodeInfo> ancestors,
            List<AccessibilityNodeInfo> owned, List<Target> targets) {
        owned.add(node);
        if (depth > 60 || owned.size() > 600) throw new IllegalStateException("Tree limit");
        if (!packageName.contentEquals(node.getPackageName() == null ? "" : node.getPackageName())) return;
        Rect bounds = new Rect();
        node.getBoundsInScreen(bounds);
        int action = node.isClickable() && node.getActionList().contains(AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK)
                ? AccessibilityNodeInfo.ACTION_CLICK
                : node.isEditable() && node.getActionList().contains(AccessibilityNodeInfo.AccessibilityAction.ACTION_FOCUS)
                ? AccessibilityNodeInfo.ACTION_FOCUS : 0;
        Rect targetViewport = viewport;
        if (action != 0 && node.isEnabled() && node.isVisibleToUser() && bounds.intersect(targetViewport)
                && bounds.width() > 2 && bounds.height() > 2) {
            // Equal-bounds actionable descendants replace redundant clickable wrappers.
            for (int i = targets.size() - 1; i >= 0; i--) {
                if (targets.get(i).bounds.equals(bounds)) targets.remove(i);
            }
            targets.add(new Target(node, bounds, action, ancestors));
        }
        // A virtual parent may be marked invisible while its children are visible.
        // Carry the visible scroll viewport down the tree, including expanded WebViews.
        Rect childViewport = viewport;
        if (scrollSupport(node).verticalContainer()) {
            childViewport = new Rect(viewport);
            Rect scrollBounds = new Rect();
            node.getBoundsInScreen(scrollBounds);
            if (!childViewport.intersect(scrollBounds)) childViewport.setEmpty();
        }
        ancestors.add(node);
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = Build.VERSION.SDK_INT >= 33
                    ? node.getChild(i, TREE_PREFETCH) : node.getChild(i);
            if (child != null) collect(child, packageName, depth + 1, childViewport, ancestors, owned, targets);
        }
        ancestors.remove(ancestors.size() - 1);
        // A text-only portion of a page must remain reachable even with no clickable rows.
        // Represent the scroll owner itself rather than moving selection to unrelated chrome.
        if (node.isEnabled() && node.isVisibleToUser() && scrollSupport(node).verticalContainer()) {
            node.getBoundsInScreen(bounds);
            if (bounds.intersect(targetViewport) && bounds.width() > 2 && bounds.height() > 2) {
                boolean hasTarget = false;
                for (Target target : targets) {
                    if (target.node.equals(node) || target.ancestors.contains(node)) { hasTarget = true; break; }
                }
                if (!hasTarget) targets.add(new Target(node, bounds, 0, ancestors));
            }
        }
    }

    private void releaseNodes() {
        for (AccessibilityNodeInfo node : owned) node.recycle();
        owned.clear(); targets.clear(); selected = null;
    }

    void clear() {
        readyPackage = "";
        numberMovementReady = false;
        if (Looper.myLooper() != handler.getLooper()) { handler.post(this::clear); return; }
        activeWindowId = -1;
        handler.removeCallbacks(refreshTask); scheduled = false;
        clearScrollAnchor(); textEntryFocused = false;
        overlay.hide(); releaseNodes(); activePackage = "";
    }

    void destroy() {
        destroyed = true;
        readyPackage = "";
        service.unregisterReceiver(screenReceiver);
        handler.post(() -> { clear(); worker.quitSafely(); });
    }

    void dump(PrintWriter writer) { writer.print(diagnostics); }

    private void updateDiagnostics() {
        numberMovementReady = selected != null && !selected.node.isEditable()
                && !textEntryFocused;
        readyPackage = destroyed ? "" : activePackage;
        StringWriter buffer = new StringWriter();
        dumpState(new PrintWriter(buffer));
        diagnostics = buffer.toString();
    }

    private void dumpState(PrintWriter writer) {
        writer.println("Timing ms: refresh last=" + lastRefreshMs + " max=" + maxRefreshMs
                + "; action last=" + lastKeyMs + " max=" + maxKeyMs + "; dispatch max=" + maxDispatchMs);
        writer.println("Last scroll request to highlight: " + lastScrollToHighlightMs + " ms");
        writer.println("Spatial navigation: " + status + "; package=" + activePackage + "; targets=" + targets.size());
        if (selected != null) writer.println("Selected bounds: " + selected.bounds.toShortString()
                + "; kind=" + (selected.action == 0 ? "scroll region" : "control"));
        writer.println("Text entry focused: " + textEntryFocused);
        if (selected != null) {
            AccessibilityNodeInfo container = scrollContainer(selected);
            if (container != null) writer.println("Selected scroll container: class=" + container.getClassName()
                    + " id=" + container.getViewIdResourceName()
                    + " up=" + ScrollNavigation.action(scrollSupport(container), false)
                    + " down=" + ScrollNavigation.action(scrollSupport(container), true));
        }
    }

    private static final class Target {
        final AccessibilityNodeInfo node;
        final Rect bounds;
        final int action;
        final List<AccessibilityNodeInfo> ancestors;
        Target(AccessibilityNodeInfo node, Rect bounds, int action, List<AccessibilityNodeInfo> ancestors) {
            this.node = node; this.bounds = bounds; this.action = action;
            this.ancestors = new ArrayList<>(ancestors);
        }
        SpatialNavigation.Box box() { return new SpatialNavigation.Box(bounds.left, bounds.top, bounds.right, bounds.bottom); }
    }
}
