package com.bearnation.cardinal.util;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.node.Node;
import net.luckperms.api.node.types.PermissionNode;
import net.luckperms.api.cacheddata.CachedMetaData;
import net.luckperms.api.cacheddata.CachedPermissionData;
import net.luckperms.api.query.QueryOptions;

import com.velocitypowered.api.proxy.Player;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class LuckPermsUtil {

    private final LuckPerms luckPerms;

    public LuckPermsUtil(LuckPerms luckPerms) {
        this.luckPerms = luckPerms;
    }

    /** Returns the LuckPerms User object for a Velocity Player. */
    public User getUser(Player player) {
        return getUser(player.getUniqueId());
    }

    /** Returns the LuckPerms User object for a UUID. */
    public User getUser(UUID uuid) {
        return luckPerms.getUserManager().getUser(uuid);
    }

    /** Returns the player's rank prefix, or primary group if none. */
    public String getDisplayRank(Player player) {
        User user = getUser(player);
        if (user == null) return "Default";

        CachedMetaData meta = user.getCachedData().getMetaData(QueryOptions.defaultContextualOptions());
        String prefix = meta.getPrefix();
        return prefix != null ? prefix : user.getPrimaryGroup();
    }

    /** Returns the player's prefix (may be null). */
    public String getPrefix(Player player) {
        User user = getUser(player);
        if (user == null) return null;

        return user.getCachedData().getMetaData(QueryOptions.defaultContextualOptions()).getPrefix();
    }

    /** Returns the player's suffix (may be null). */
    public String getSuffix(Player player) {
        User user = getUser(player);
        if (user == null) return null;

        return user.getCachedData().getMetaData(QueryOptions.defaultContextualOptions()).getSuffix();
    }

    /** Returns the player's primary group. */
    public String getPrimaryGroup(Player player) {
        User user = getUser(player);
        if (user == null) return "default";

        return user.getPrimaryGroup();
    }

    /** Returns true if the player has the specified permission. */
    public boolean hasPermission(Player player, String permission) {
        User user = getUser(player);
        if (user == null) return false;

        CachedPermissionData perms = user.getCachedData().getPermissionData(QueryOptions.defaultContextualOptions());
        return perms.checkPermission(permission).asBoolean();
    }

    /** Returns all nodes/permissions assigned to the user. */
    public Set<Node> getAllPermissionNodes(Player player) {
        User user = getUser(player);
        if (user == null) return Set.of();

        return new HashSet<>(user.getNodes());
    }


    /** Returns the LuckPerms Group object for a given group name. */
    public Optional<Group> getGroup(String groupName) {
        return Optional.ofNullable(luckPerms.getGroupManager().getGroup(groupName));
    }

    /** Returns all groups a user inherits from (transitive). */
    public Set<String> getAllGroups(Player player) {
        User user = getUser(player);
        if (user == null) return Set.of();

        return user.getInheritedGroups(QueryOptions.defaultContextualOptions()).stream()
                .map(Group::getName)
                .collect(java.util.stream.Collectors.toSet());
    }

    /** Returns LuckPerms instance (for advanced API access). */
    public LuckPerms getLuckPerms() {
        return luckPerms;
    }

    /** Returns a user's meta value by key (like "tag", "chat-color", etc). */
    public String getMeta(Player player, String key) {
        User user = getUser(player);
        if (user == null) return null;

        return user.getCachedData().getMetaData(QueryOptions.defaultContextualOptions()).getMetaValue(key);
    }

    /** Returns the weight of the user's primary group (if defined). */
    public int getGroupWeight(Player player) {
        String groupName = getPrimaryGroup(player);
        Group group = luckPerms.getGroupManager().getGroup(groupName);
        if (group == null) return 0;

        return group.getWeight().orElse(0);
    }

    public void refreshMetadata(Player player) {
        User user = getUser(player);
        if (user != null) {
            user.getCachedData().invalidate();
        }
    }
}
