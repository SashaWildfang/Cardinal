package com.bearnation.cardinal.playtime;

import com.bearnation.cardinal.Cardinal;
import com.bearnation.cardinal.database.DatabaseManager;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.proxy.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public final class PlaytimeService {
    private final Cardinal plugin;
    private final Map<UUID, Long> lastFlush = new ConcurrentHashMap<>(); // millis since last DB write

    public PlaytimeService(Cardinal plugin) {
        this.plugin = plugin;

        // periodic flush so crashes don’t lose time
        plugin.getServer().getScheduler()
                .buildTask(plugin, this::flushAll)
                .repeat(60, TimeUnit.SECONDS)
                .schedule();
    }

    @Subscribe
    public void onPostLogin(PostLoginEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        lastFlush.put(id, System.currentTimeMillis());
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent e) {
        Player p = e.getPlayer();
        UUID id = p.getUniqueId();
        flushOne(id);
        lastFlush.remove(id);
    }

    private void flushAll() {
        long now = System.currentTimeMillis();
        for (UUID id : plugin.getServer().getAllPlayers().stream().map(Player::getUniqueId).toList()) {
            Long last = lastFlush.get(id);
            if (last == null) { lastFlush.put(id, now); continue; }
            long delta = (now - last) / 1000;
            if (delta > 0) {
                DatabaseManager.addPlaySeconds(id, delta);
                lastFlush.put(id, now);
            }
        }
    }

    private void flushOne(UUID id) {
        Long last = lastFlush.get(id);
        if (last == null) return;
        long delta = (System.currentTimeMillis() - last) / 1000;
        if (delta > 0) DatabaseManager.addPlaySeconds(id, delta);
        lastFlush.put(id, System.currentTimeMillis());
    }

    /** For live display: DB value + unflushed seconds in memory */
    public long getLivePlaySeconds(UUID id) {
        long base = DatabaseManager.getPlaySeconds(id);
        Long last = lastFlush.get(id);
        if (last != null) base += (System.currentTimeMillis() - last) / 1000;
        return base;
    }
}
