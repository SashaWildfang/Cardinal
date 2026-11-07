package com.bearnation.cardinal.util;

import java.util.*;

public class IgnoreManager {

    private static final Map<UUID, Set<UUID>> ignoredMap = new HashMap<>();

    public static void ignore(UUID player, UUID toIgnore) {
        ignoredMap.computeIfAbsent(player, k -> new HashSet<>()).add(toIgnore);
    }

    public static void unignore(UUID player, UUID toUnignore) {
        Set<UUID> ignored = ignoredMap.get(player);
        if (ignored != null) {
            ignored.remove(toUnignore);
            if (ignored.isEmpty()) ignoredMap.remove(player);
        }
    }

    public static boolean isIgnoring(UUID player, UUID target) {
        return ignoredMap.getOrDefault(player, Collections.emptySet()).contains(target);
    }

    public static Set<UUID> getIgnored(UUID player) {
        return ignoredMap.getOrDefault(player, Collections.emptySet());
    }

    public static void clear(UUID player) {
        ignoredMap.remove(player);
    }
}
