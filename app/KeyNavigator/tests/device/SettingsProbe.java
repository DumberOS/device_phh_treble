package com.dumbermini.keynavigator.probe;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.util.List;

/** Opt-in UI test: change an app switch, verify activation, then restore it. */
public final class SettingsProbe extends Instrumentation {
    private static final String APP = "com.dumbermini.keynavigator";
    private UiAutomation automation;
    private String targetPackage = "se.bankgirot.swish", targetLabel = "Swish";
    private boolean bankId;
    @Override public void onCreate(Bundle args) {
        super.onCreate(args);
        bankId = args != null && "bankid".equals(args.getString("app"));
        if (bankId) { targetPackage = "com.bankid.bus"; targetLabel = "BankID"; }
        start();
    }
    @Override public void onStart() {
        Bundle result = new Bundle();
        Boolean original = null;
        Exception failure = null;
        try {
            require(!getContext().getSystemService(android.app.KeyguardManager.class).isKeyguardLocked(),
                    "Unlock the phone before running the settings test");
            automation = getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
            AccessibilityServiceInfo info = automation.getServiceInfo();
            info.flags |= AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
                    | AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
                    | AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS;
            automation.setServiceInfo(info);
            openSettings(); original = checked();
            setChecked(false); verifyNavigation(false);
            openSettings(); require(!checked(), "Disabled choice did not survive reopening");
            setChecked(true); verifyNavigation(true);
            openSettings(); require(checked(), "Enabled choice did not survive reopening");
        } catch (Exception e) { failure = e; }
        finally {
            if (original != null) {
                try { openSettings(); setChecked(original); }
                catch (Exception e) { failure = e; }
            }
        }
        result.putString("result", failure == null
                ? "PASS: switch off/on changes " + targetLabel + " standard navigation; choices persist on reopen; original setting restored"
                        + (bankId ? "; BankID custom controls remain enabled in both cases" : "")
                : "FAIL: " + failure.getClass().getSimpleName() + ": " + failure.getMessage());
        finish(failure == null ? -1 : 1, result);
    }

    private AccessibilityNodeInfo root() {
        AccessibilityNodeInfo root = null;
        for (AccessibilityWindowInfo window : automation.getWindows()) {
            try {
                if (root == null && window.isFocused() && window.getType() == AccessibilityWindowInfo.TYPE_APPLICATION)
                    root = window.getRoot();
            } finally { window.recycle(); }
        }
        return root;
    }

    private AccessibilityNodeInfo find(String id) {
        AccessibilityNodeInfo root = root();
        if (root == null) return null;
        try {
            if (!APP.contentEquals(root.getPackageName())) return null;
            List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByViewId(APP + ":id/" + id);
            if (nodes.size() == 1) return nodes.get(0);
            for (AccessibilityNodeInfo node : nodes) node.recycle();
            return null;
        } finally { root.recycle(); }
    }

    private AccessibilityNodeInfo waitFor(String id) {
        long deadline = SystemClock.uptimeMillis() + 10000;
        while (SystemClock.uptimeMillis() < deadline) {
            AccessibilityNodeInfo node = find(id);
            if (node != null) return node;
            SystemClock.sleep(100);
        }
        throw new IllegalStateException("Missing settings control: " + id);
    }

    private void openSettings() {
        getContext().startActivity(new Intent().setComponent(new ComponentName(APP, APP + ".SetupActivity"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP));
        long searchDeadline = SystemClock.uptimeMillis() + 5000;
        boolean searched = false;
        while (!searched && SystemClock.uptimeMillis() < searchDeadline) {
            AccessibilityNodeInfo field = waitFor("app_search");
            try {
                field.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
                Bundle args = new Bundle();
                args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, targetLabel);
                searched = field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);
            } finally { field.recycle(); }
            if (!searched) SystemClock.sleep(100);
        }
        require(searched, "Search rejected");
        long deadline = SystemClock.uptimeMillis() + 10000;
        while (SystemClock.uptimeMillis() < deadline) {
            AccessibilityNodeInfo row = find("app_label");
            if (row != null) {
                try { if (targetLabel.contentEquals(row.getText())) return; }
                finally { row.recycle(); }
            }
            SystemClock.sleep(100);
        }
        throw new IllegalStateException(targetLabel + " launcher row missing");
    }

    private boolean checked() {
        AccessibilityNodeInfo toggle = waitFor("app_toggle");
        try { return toggle.isChecked(); } finally { toggle.recycle(); }
    }

    private void setChecked(boolean value) {
        if (checked() != value) {
            AccessibilityNodeInfo toggle = waitFor("app_toggle");
            try { require(toggle.performAction(AccessibilityNodeInfo.ACTION_CLICK), "Toggle rejected"); }
            finally { toggle.recycle(); }
        }
        long deadline = SystemClock.uptimeMillis() + 3000;
        while (SystemClock.uptimeMillis() < deadline) {
            if (checked() == value) return;
            SystemClock.sleep(100);
        }
        throw new IllegalStateException("Toggle did not change");
    }

    private void verifyNavigation(boolean enabled) throws Exception {
        Intent launch = getContext().getPackageManager().getLaunchIntentForPackage(targetPackage);
        require(launch != null, "Missing launcher activity for " + targetLabel);
        getContext().startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        long deadline = SystemClock.uptimeMillis() + 10000;
        while (SystemClock.uptimeMillis() < deadline) {
            SystemClock.sleep(200);
            String dump = shell("dumpsys activity service " + APP + "/.BankIdKeyService");
            if (bankId) require(dump.contains("BankID custom controls enabled: true"), "BankID custom controls disabled");
            if (dump.contains("Current window: " + targetLabel)
                    && dump.contains("; package=" + targetPackage + ";") == enabled
                    && (!bankId || dump.contains("BankID standard navigation enabled: " + enabled))) return;
        }
        throw new IllegalStateException("Navigation did not match switch=" + enabled);
    }

    private String shell(String command) throws Exception {
        try (FileInputStream in = new ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command));
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096]; int read;
            while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
            return out.toString("UTF-8");
        }
    }
    private static void require(boolean value, String message) {
        if (!value) throw new IllegalStateException(message);
    }
}
