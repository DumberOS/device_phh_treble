package com.dumbermini.keynavigator;

import java.util.List;

/** Directional geometry, independent of Android and of the target application. */
final class SpatialNavigation {
    enum Direction { LEFT, RIGHT, UP, DOWN }

    static final class Box {
        final int left, top, right, bottom;
        Box(int left, int top, int right, int bottom) {
            this.left = left; this.top = top; this.right = right; this.bottom = bottom;
        }
        double x() { return (left + (double) right) / 2; }
        double y() { return (top + (double) bottom) / 2; }
        boolean same(Box other) {
            return other != null && left == other.left && top == other.top
                    && right == other.right && bottom == other.bottom;
        }
    }

    static int initial(List<Box> boxes) {
        int best = -1;
        for (int i = 0; i < boxes.size(); i++) {
            Box b = boxes.get(i);
            if (b.right <= b.left || b.bottom <= b.top) continue;
            if (best < 0 || b.top < boxes.get(best).top
                    || (b.top == boxes.get(best).top && b.left < boxes.get(best).left)) best = i;
        }
        return best;
    }

    static int closest(List<Box> boxes, Box anchor) {
        int best = -1;
        double distance = Double.POSITIVE_INFINITY;
        for (int i = 0; i < boxes.size(); i++) {
            Box box = boxes.get(i);
            if (box.right <= box.left || box.bottom <= box.top) continue;
            double dx = box.x() - anchor.x(), dy = box.y() - anchor.y();
            double candidate = dx * dx + dy * dy;
            if (candidate < distance) { best = i; distance = candidate; }
        }
        return best;
    }

    /** External full-width tab bars can cover list rows still marked visible by the app. */
    static Box withoutEdgeBars(Box viewport, List<Box> bars) {
        int top = viewport.top, bottom = viewport.bottom;
        for (Box bar : bars) {
            if (bar.left > viewport.left || bar.right < viewport.right || bar.bottom <= bar.top
                    || bar.bottom - bar.top >= (viewport.bottom - viewport.top) / 2) continue;
            if (bar.top <= viewport.top && bar.bottom > top) top = Math.min(bottom, bar.bottom);
            if (bar.bottom >= viewport.bottom && bar.top < bottom) bottom = Math.max(top, bar.top);
        }
        return new Box(viewport.left, top, viewport.right, bottom);
    }

    static boolean rowCenterVisible(Box row, Box viewport) {
        return row.y() >= viewport.top && row.y() < viewport.bottom;
    }

    /** Scroll when at most one more directional target remains inside the container. */
    static boolean nearVerticalEdge(List<Box> boxes, int current, Direction direction) {
        if (current < 0 || current >= boxes.size()
                || (direction != Direction.UP && direction != Direction.DOWN)) return false;
        int next = next(boxes, current, direction);
        return next == current || next(boxes, next, direction) == next;
    }

    static int next(List<Box> boxes, int current, Direction direction) {
        if (current < 0 || current >= boxes.size()) return initial(boxes);
        Box from = boxes.get(current);
        boolean horizontal = direction == Direction.LEFT || direction == Direction.RIGHT;
        int best = -1;
        boolean bestAligned = false;
        double bestScore = Double.POSITIVE_INFINITY;
        for (int i = 0; i < boxes.size(); i++) {
            if (i == current) continue;
            Box to = boxes.get(i);
            if (to.right <= to.left || to.bottom <= to.top || from.same(to)) continue;
            double dx = to.x() - from.x(), dy = to.y() - from.y();
            double forward = direction == Direction.RIGHT ? dx : direction == Direction.LEFT ? -dx
                    : direction == Direction.DOWN ? dy : -dy;
            if (forward <= 0) continue;
            boolean aligned = horizontal ? to.top < from.bottom && to.bottom > from.top
                    : to.left < from.right && to.right > from.left;
            double sideways = horizontal ? dy : dx;
            if (!aligned && Math.abs(sideways) > 2 * forward) continue;
            double score = forward * forward + 4 * sideways * sideways;
            if (best < 0 || (aligned && !bestAligned)
                    || (aligned == bestAligned && score < bestScore)) {
                best = i; bestAligned = aligned; bestScore = score;
            }
        }
        return best < 0 ? current : best; // No implicit wrap or scroll at an edge.
    }
}
