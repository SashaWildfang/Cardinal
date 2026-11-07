package com.bearnation.cardinal.util;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SpyManager {

    private static final Set<UUID> spies = ConcurrentHashMap.newKeySet();

    public static void enableSpy(UUID player) {
        spies.add(player);
    }

    public static void disableSpy(UUID player) {
        spies.remove(player);
    }

    public static boolean isSpy(UUID player) {
        return spies.contains(player);
    }

    public static Set<UUID> getSpies() {
        return Set.copyOf(spies);
    }
}
