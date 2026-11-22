package com.dumbermini.keynavigator;

import java.util.List;

/** Picks the closest vertical ancestor; reaching its boundary never scrolls an outer list. */
final class ScrollNavigation {
    static boolean needsTouchScroll(String packageName, String viewId) {
        return "ch.protonmail.android".equals(packageName) && "MailboxList".equals(viewId);
    }

    static int bodyScrollOwner(String packageName, int firstWebView, List<Support> ancestors) {
        if (!"ch.protonmail.android".equals(packageName) || firstWebView < 0
                || firstWebView > ancestors.size()) return -1;
        // Proton expands its email WebView inside a vertically scrolling Compose page.
        for (int i = firstWebView - 1; i >= 0; i--)
            if (ancestors.get(i).verticalContainer()) return i;
        return -1;
    }

    static final class Swipe {
        final float x, fromY, toY;
        Swipe(float x, float fromY, float toY) { this.x = x; this.fromY = fromY; this.toY = toY; }
    }

    static Swipe swipe(int left, int top, int right, int bottom, boolean down) {
        if (right - left < 48 || bottom - top < 96) return null;
        // Avoid headers and floating controls near the bottom of the list.
        float upper = top + (bottom - top) * 0.25f;
        float lower = top + (bottom - top) * 0.70f;
        return new Swipe((left + right) / 2f, down ? lower : upper, down ? upper : lower);
    }

    /** Uptime-based settling: queued events must not restart the quiet period. */
    static final class Settling {
        final long started;
        final int quietMs;
        long deadline, lastEvent = -1;
        Settling(long started, boolean immediate) {
            this.started = started;
            quietMs = immediate ? 40 : 120;
            deadline = started + (immediate ? 60 : 300);
        }
        void onEvent(long eventTime, long now) {
            if (eventTime < started || eventTime < lastEvent) return;
            lastEvent = Math.min(eventTime, now);
            deadline = lastEvent + quietMs;
        }
        long remaining(long now) { return Math.max(0, deadline - now); }
    }

    enum Action { NONE, UP, DOWN, BACKWARD, FORWARD }

    static final class Support {
        final boolean scrollable, enabled, up, down, backward, forward, horizontal;
        Support(boolean scrollable, boolean enabled, boolean up, boolean down,
                boolean backward, boolean forward, boolean horizontal) {
            this.scrollable = scrollable; this.enabled = enabled;
            this.up = up; this.down = down; this.backward = backward;
            this.forward = forward; this.horizontal = horizontal;
        }
        boolean verticalContainer() {
            return up || down || (!horizontal && (scrollable || backward || forward));
        }
    }

    // Ancestors are ordered from the window root to the immediate parent.
    static int nearest(List<Support> ancestors) {
        for (int i = ancestors.size() - 1; i >= 0; i--)
            if (ancestors.get(i).verticalContainer()) return i;
        return -1;
    }

    /** One-row jumps retain overlap; missing, single-row, or reversed metadata falls back. */
    static int adjacentRow(int first, int last, int rowCount, boolean down) {
        if (first < 0 || last <= first || last >= rowCount) return -1;
        int target = first + (down ? 1 : -1);
        return target >= 0 && target < rowCount ? target : -1;
    }

    static Action action(Support support, boolean down) {
        if (!support.enabled) return Action.NONE;
        if (down) return support.down ? Action.DOWN : support.forward ? Action.FORWARD : Action.NONE;
        return support.up ? Action.UP : support.backward ? Action.BACKWARD : Action.NONE;
    }
}
