package com.bearnation.cardinal.listener;

import com.bearnation.cardinal.Cardinal;
import com.bearnation.cardinal.bus.Bus;
import com.bearnation.cardinal.messaging.Messaging;
import com.bearnation.cardinal.util.LuckPermsUtil;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.proxy.Player;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.context.ContextManager;
import net.luckperms.api.event.group.GroupDataRecalculateEvent;
import net.luckperms.api.event.node.NodeMutateEvent;
import net.luckperms.api.event.user.UserDataRecalculateEvent;
import net.luckperms.api.model.PermissionHolder;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import net.luckperms.api.query.QueryOptions;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class LuckPermsListener {

    private final Cardinal plugin;
    private final LuckPerms luckPerms;
    private final ContextManager contextManager;
    private final LuckPermsUtil luckPermsUtil;

    // Debounce per user
    private final Map<UUID, com.velocitypowered.api.scheduler.ScheduledTask> pending = new ConcurrentHashMap<>();
    // Last-sent snapshot to avoid duplicate publishes
    private final Map<UUID, Snapshot> lastSent = new ConcurrentHashMap<>();

    public LuckPermsListener(Cardinal plugin, LuckPerms luckPerms, LuckPermsUtil luckPermsUtil) {
        this.plugin = plugin;
        this.luckPerms = luckPerms;
        this.contextManager = luckPerms.getContextManager();
        this.luckPermsUtil = luckPermsUtil;
        Cardinal.log("[LuckPermsListener] Registered (" + System.identityHashCode(this) + ")");
    }

    /* ----------------------------- EVENT HANDLERS ----------------------------- */

    @Subscribe
    public void onUserMetaUpdate(UserDataRecalculateEvent event) {
        UUID uuid = event.getUser().getUniqueId();
        plugin.getServer().getPlayer(uuid).ifPresent(luckPermsUtil::refreshMetadata);
        enqueuePublish(uuid);
    }

    @Subscribe
    public void onGroupDataChange(GroupDataRecalculateEvent event) {
        handleGroupUpdate(event.getGroup().getName());
    }

    @Subscribe
    public void onNodeMutate(NodeMutateEvent event) {
        PermissionHolder holder = event.getTarget();
        if (holder instanceof Group g) {
            handleGroupUpdate(g.getName());
        }
        // Ignore user mutations — recalc will follow anyway
    }

    /* ----------------------------- CORE LOGIC ----------------------------- */

    private void handleGroupUpdate(String groupName) {
        Cardinal.log("[LuckPermsListener] Group data updated: " + groupName);

        for (Player p : plugin.getServer().getAllPlayers()) {
            UUID uuid = p.getUniqueId();
            luckPerms.getUserManager().loadUser(uuid).thenAcceptAsync(user -> {
                boolean inherits = user.getInheritedGroups(QueryOptions.defaultContextualOptions())
                        .stream().anyMatch(g -> g.getName().equalsIgnoreCase(groupName));
                if (inherits) {
                    enqueuePublish(uuid);
                }
            });
        }
    }

    private void enqueuePublish(UUID uuid) {
        var old = pending.remove(uuid);
        if (old != null) old.cancel();

        var task = plugin.getProxy().getScheduler()
                .buildTask(plugin, () -> {
                    luckPerms.getUserManager().loadUser(uuid).thenAcceptAsync(user -> {
                        user.getCachedData().invalidate();
                        plugin.getChatManager().refreshChatFormat(uuid);

                        if (shouldPublish(uuid, user)) {
                            publishLuckPermsUpdate(uuid);
                        }
                    });
                    pending.remove(uuid);
                })
                .delay(200, TimeUnit.MILLISECONDS)
                .schedule();

        pending.put(uuid, task);
    }

    private boolean shouldPublish(UUID uuid, User user) {
        var q = QueryOptions.defaultContextualOptions();
        var meta = user.getCachedData().getMetaData(q);

        Snapshot now = new Snapshot(
                user.getPrimaryGroup(),
                nullSafe(meta.getPrefix()),
                nullSafe(meta.getSuffix()),
                nullSafe(meta.getMetaValue("chat-color"))
        );

        Snapshot prev = lastSent.put(uuid, now);
        return !now.equals(prev);
    }

    private static String nullSafe(String s) {
        return s == null ? "" : s;
    }

    private void publishLuckPermsUpdate(UUID uuid) {
        try {
            Messaging m = plugin.getMessaging();
            var payload = new com.google.gson.JsonObject();
            payload.addProperty("uuid", uuid.toString());
            m.getBus().publish("bn.hub", Bus.env("luckperms_update", "broadcast", payload).toString());
            Cardinal.log("[LuckPermsListener] Published luckperms_update for " + uuid);
        } catch (Exception e) {
            Cardinal.log("[LuckPermsListener] Failed to publish luckperms_update: " + e.getMessage());
        }
    }

    /* ----------------------------- SNAPSHOT ----------------------------- */

    private static final class Snapshot {
        final String primary, prefix, suffix, color;
        Snapshot(String primary, String prefix, String suffix, String color) {
            this.primary = primary; this.prefix = prefix; this.suffix = suffix; this.color = color;
        }
        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Snapshot s)) return false;
            return Objects.equals(primary, s.primary)
                    && Objects.equals(prefix, s.prefix)
                    && Objects.equals(suffix, s.suffix)
                    && Objects.equals(color, s.color);
        }
        @Override public int hashCode() {
            return Objects.hash(primary, prefix, suffix, color);
        }
    }
}
