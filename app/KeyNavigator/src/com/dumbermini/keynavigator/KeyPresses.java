package com.dumbermini.keynavigator;

import java.util.HashMap;
import java.util.Map;

/** Tracks only currently held keys, including their release after a window transition. */
final class KeyPresses {
    private final Map<Long, Long> held = new HashMap<>();

    private static long id(int device, int key) {
        return ((long) device << 32) | (key & 0xffffffffL);
    }

    void begin(int device, int key) { held.remove(id(device, key)); }
    void consume(int device, int key, long downTime) { held.put(id(device, key), downTime); }
    boolean contains(int device, int key, long downTime) {
        Long time = held.get(id(device, key));
        return time != null && time == downTime;
    }
    boolean release(int device, int key, long downTime) {
        if (!contains(device, key, downTime)) return false;
        held.remove(id(device, key));
        return true;
    }
    void clear() { held.clear(); }
}
