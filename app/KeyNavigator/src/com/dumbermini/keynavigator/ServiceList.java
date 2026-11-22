package com.dumbermini.keynavigator;

import java.util.LinkedHashSet;
import java.util.Set;

/** Changes only our service entries, preserving other accessibility services. */
final class ServiceList {
    static final String COMPONENT = "com.dumbermini.keynavigator/com.dumbermini.keynavigator.BankIdKeyService";
    static final String LEGACY = "eu.dumbdroid.bankidkeys/eu.dumbdroid.bankidkeys.BankIdKeyService";

    private static String canonical(String component) {
        int slash = component.indexOf('/');
        if (slash < 1 || slash + 1 >= component.length()) return component;
        String name = component.substring(slash + 1);
        return component.substring(0, slash + 1) + (name.startsWith(".") ? component.substring(0, slash) + name : name);
    }

    static boolean enabled(String services) {
        if (services != null) for (String service : services.split(":"))
            if (COMPONENT.equals(canonical(service))) return true;
        return false;
    }

    static String update(String services, boolean enable, boolean retireLegacy) {
        Set<String> result = new LinkedHashSet<>();
        if (services != null) for (String service : services.split(":")) {
            if (service.isEmpty()) continue;
            String normalized = canonical(service);
            if (COMPONENT.equals(normalized) || (retireLegacy && LEGACY.equals(normalized))) continue;
            result.add(service);
        }
        if (enable) result.add(COMPONENT);
        return String.join(":", result);
    }
}
