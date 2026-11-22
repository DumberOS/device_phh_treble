package com.dumbermini.keynavigator;

public final class AppPolicyTest {
    private static int checks;
    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        check(AppPolicy.mode(Keypad.PACKAGE, null) == AppPolicy.Mode.BANKID, "BankID custom controls are on by default");
        check(AppPolicy.mode("se.bankgirot.swish", null) == AppPolicy.Mode.OFF, "Swish requires opt-in");
        check(AppPolicy.mode("com.example.app", null) == AppPolicy.Mode.OFF, "New apps require opt-in");
        check(AppPolicy.mode("com.example.app", true) == AppPolicy.Mode.SPATIAL, "Enabled apps get spatial controls");
        check(AppPolicy.mode("com.example.app", false) == AppPolicy.Mode.OFF, "Disabled apps pass through");
        check(AppPolicy.mode(Keypad.PACKAGE, true) == AppPolicy.Mode.BANKID_SPATIAL, "BankID toggle adds standard navigation");
        check(AppPolicy.mode(Keypad.PACKAGE, false) == AppPolicy.Mode.BANKID, "BankID custom controls remain on with toggle off");
        check(!AppPolicy.spatialEnabled(AppPolicy.mode(Keypad.PACKAGE, null)), "BankID standard navigation defaults off");
        check(!AppPolicy.spatialEnabled(AppPolicy.mode(Keypad.PACKAGE, false)), "BankID standard navigation can be disabled");
        check(AppPolicy.spatialEnabled(AppPolicy.mode(Keypad.PACKAGE, true)), "BankID standard navigation can be enabled");
        check(AppPolicy.spatialEnabled(AppPolicy.mode("se.bankgirot.swish", true)), "Other apps retain standard navigation");
        check(!AppPolicy.spatialEnabled(AppPolicy.Mode.OFF), "Disabled apps have no standard navigation");
        check(AppPolicy.mode(WiseKeypad.PACKAGE, null) == AppPolicy.Mode.WISE, "Wise PIN controls default on");
        check(AppPolicy.mode(WiseKeypad.PACKAGE, false) == AppPolicy.Mode.WISE, "Wise toggle off keeps PIN controls");
        check(AppPolicy.mode(WiseKeypad.PACKAGE, true) == AppPolicy.Mode.WISE_SPATIAL, "Wise toggle adds standard navigation");
        check(!AppPolicy.spatialEnabled(AppPolicy.mode(WiseKeypad.PACKAGE, null)), "Wise standard navigation defaults off");
        check(AppPolicy.spatialEnabled(AppPolicy.mode(WiseKeypad.PACKAGE, true)), "Wise can opt into standard navigation");
        check(AppPolicy.hasCustomControls(WiseKeypad.PACKAGE), "Wise receives PIN digits before 2/5 navigation");
        check(AppPolicy.hasCustomControls(Keypad.PACKAGE), "BankID retains custom-control priority");
        check(!AppPolicy.hasCustomControls("com.transferwise.android.other"), "Custom controls require exact package");
        check(AppPolicy.mode("se.bankgirot.swish", false) == AppPolicy.Mode.OFF, "Swish remains disabled when explicitly switched off");
        check(AppPolicy.mode("se.bankgirot.swish", true) == AppPolicy.Mode.SPATIAL, "Explicit Swish opt-in remains enabled");
        check(AppPolicy.mode(null, true) == AppPolicy.Mode.OFF, "No app cannot receive controls");
        check(AppPolicy.mode("", true) == AppPolicy.Mode.OFF, "Empty package rejected");
        check(AppPolicy.mode("com.bankid.bus.other", null) == AppPolicy.Mode.OFF, "Defaults match exact packages only");
        System.out.println("Passed " + checks + " app policy checks");
    }
}
