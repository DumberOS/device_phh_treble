package com.dumbermini.keynavigator;

public final class ServiceListTest {
    private static int checks;
    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        String other = "org.example.reader/.Reader";
        String own = ServiceList.COMPONENT;
        String shortOwn = "com.dumbermini.keynavigator/.BankIdKeyService";
        String old = "eu.dumbdroid.bankidkeys/.BankIdKeyService";
        check(!ServiceList.enabled(null), "Missing service list is disabled");
        check(!ServiceList.enabled(other), "Other services do not enable our switch");
        check(ServiceList.enabled(other + ":" + shortOwn), "Accept shortened component names");
        check(ServiceList.enabled(own), "Accept full component name");
        check(!ServiceList.enabled(old), "Old package does not enable the new service");
        check(ServiceList.update(null, true, true).equals(own), "First setup enables our service");
        String migrated = ServiceList.update(other + ":" + old, true, true);
        check(migrated.equals(other + ":" + own), "Migration preserves other services and retires old component");
        check(ServiceList.update(migrated, true, true).equals(migrated), "Initialization is idempotent");
        check(ServiceList.update(ServiceList.LEGACY, true, true).equals(own), "Retire full legacy component too");
        check(ServiceList.update(old, true, false).equals(old + ":" + own), "Normal toggle leaves legacy entry alone");
        check(ServiceList.update(other + ":" + shortOwn + ":" + own, false, false).equals(other),
                "Disable removes all own aliases without disabling other services");
        check(ServiceList.update(own, false, false).isEmpty(), "Disable last service clears the list");
        check(ServiceList.update(":" + other + "::", true, true).equals(other + ":" + own), "Ignore empty entries");
        System.out.println("Passed " + checks + " accessibility configuration checks");
    }
}
