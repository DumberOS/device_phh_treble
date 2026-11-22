package com.dumbermini.keynavigator;

import java.util.ArrayList;
import java.util.List;

/** Matches BankID's text acknowledgement screen before PIN entry. */
final class ReadingScreen {
    static final String FRAGMENT = Keypad.PACKAGE + ":id/sign_fragment";
    static final String TEXT = Keypad.PACKAGE + ":id/TextToSignContainer";
    static final String BUTTON_CONTAINER = Keypad.PACKAGE + ":id/AcknowledgementButtonContainer";
    static final String BUTTON = Keypad.PACKAGE + ":id/AcknowledgementButton";

    enum Action { PASS_THROUGH, CONSUME, SWIPE_UP, CONTINUE }

    final Keypad.Node button;
    final Keypad.Node scrollTarget;

    private ReadingScreen(Keypad.Node button, Keypad.Node scrollTarget) {
        this.button = button;
        this.scrollTarget = scrollTarget;
    }

    Action actionFor(boolean down, boolean centre) {
        if (centre) return button.enabled ? Action.CONTINUE : Action.CONSUME;
        if (down) return Action.SWIPE_UP;
        return Action.PASS_THROUGH;
    }

    static ReadingScreen match(Keypad.Node root) {
        if (root == null || !Keypad.PACKAGE.equals(root.packageName)) return null;
        // Never treat a PIN keypad or an ambiguous screen as an acknowledgement.
        if (!find(root, Keypad.FIELD).isEmpty() || !find(root, Keypad.GRID).isEmpty()) return null;
        Keypad.Node fragment = unique(root, FRAGMENT);
        if (fragment == null || find(root, TEXT).size() != 1 || find(root, BUTTON).size() != 1)
            return null;
        Keypad.Node text = unique(fragment, TEXT);
        Keypad.Node container = unique(fragment, BUTTON_CONTAINER);
        Keypad.Node button = container == null ? null : unique(container, BUTTON);
        if (text == null || !text.enabled || button == null || !button.clickable
                || !"android.widget.Button".equals(button.className) || !pinButton(button)) return null;
        List<Keypad.Node> scrollTargets = new ArrayList<>();
        collectScrollTargets(text, scrollTargets);
        if (scrollTargets.size() != 1 || !scrollTargets.get(0).enabled) return null;
        return new ReadingScreen(button, scrollTargets.get(0));
    }

    private static boolean pinButton(Keypad.Node button) {
        String[] labels = {"Identify with security code", "Sign with security code",
                "Approve with security code", "Approve a payment with security code",
                "Identifiera med säkerhetskod", "Skriv under med säkerhetskod",
                "Godkänn med säkerhetskod", "Godkänn betalning med säkerhetskod"};
        for (String label : labels) {
            if (label.equals(button.text.trim()) || label.equals(button.description.trim())) return true;
        }
        return false;
    }

    private static boolean usable(Keypad.Node node) {
        return node.visible && Keypad.PACKAGE.equals(node.packageName);
    }

    private static Keypad.Node unique(Keypad.Node root, String id) {
        List<Keypad.Node> matches = find(root, id);
        return matches.size() == 1 ? matches.get(0) : null;
    }

    private static List<Keypad.Node> find(Keypad.Node root, String id) {
        List<Keypad.Node> matches = new ArrayList<>();
        collect(root, id, matches);
        return matches;
    }

    private static void collect(Keypad.Node node, String id, List<Keypad.Node> matches) {
        if (!usable(node)) return;
        if (id.equals(node.id)) matches.add(node);
        for (Keypad.Node child : node.children) collect(child, id, matches);
    }

    private static boolean collectScrollTargets(Keypad.Node node, List<Keypad.Node> matches) {
        if (!usable(node)) return false;
        boolean childWebView = false;
        for (Keypad.Node child : node.children) {
            childWebView |= collectScrollTargets(child, matches);
        }
        boolean webView = "android.webkit.WebView".equals(node.className);
        // Chromium exposes its scroll actions on the inner virtual WebView, not its host.
        // Keep it at the bottom too, when the forward action disappears.
        if (webView && !childWebView) matches.add(node);
        return webView || childWebView;
    }
}
