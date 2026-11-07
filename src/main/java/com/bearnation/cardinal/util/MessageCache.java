package com.bearnation.cardinal.util;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class MessageCache {

    private static final long EXPIRATION_MILLIS = 5 * 60 * 1000; // 5 minutes

    private static final Map<UUID, Entry> lastMessagedMap = new ConcurrentHashMap<>();

    private static class Entry {
        final UUID target;
        final long timestamp;

        Entry(UUID target) {
            this.target = target;
            this.timestamp = System.currentTimeMillis();
        }
    }

    public static void setLastMessaged(UUID sender, UUID recipient) {
        lastMessagedMap.put(sender, new Entry(recipient));
    }

    public static UUID getLastMessaged(UUID sender) {
        Entry entry = lastMessagedMap.get(sender);
        if (entry == null) return null;

        if (System.currentTimeMillis() - entry.timestamp > EXPIRATION_MILLIS) {
            lastMessagedMap.remove(sender);
            return null;
        }

        return entry.target;
    }

    public static void clear(UUID player) {
        lastMessagedMap.remove(player);
    }

    public static void removeReferencesTo(UUID player) {
        lastMessagedMap.entrySet().removeIf(e -> e.getValue().target.equals(player));
    }
}
