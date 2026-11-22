package com.dumbermini.keynavigator;

/** Standard navigation is opt-in; supported custom controls are always available. */
final class AppPolicy {
    enum Mode { OFF, BANKID, WISE, SPATIAL, BANKID_SPATIAL, WISE_SPATIAL }

    static Mode mode(String packageName, Boolean preference) {
        if (packageName == null || packageName.isEmpty()) return Mode.OFF;
        boolean spatial = Boolean.TRUE.equals(preference);
        if (Keypad.PACKAGE.equals(packageName)) return spatial ? Mode.BANKID_SPATIAL : Mode.BANKID;
        if (WiseKeypad.PACKAGE.equals(packageName)) return spatial ? Mode.WISE_SPATIAL : Mode.WISE;
        return spatial ? Mode.SPATIAL : Mode.OFF;
    }

    static boolean spatialEnabled(Mode mode) {
        return mode == Mode.SPATIAL || mode == Mode.BANKID_SPATIAL || mode == Mode.WISE_SPATIAL;
    }

    static boolean hasCustomControls(String packageName) {
        return Keypad.PACKAGE.equals(packageName) || WiseKeypad.PACKAGE.equals(packageName);
    }
}
