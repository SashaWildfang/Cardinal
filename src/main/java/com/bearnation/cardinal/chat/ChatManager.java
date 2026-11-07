package com.bearnation.cardinal.chat;

import com.bearnation.cardinal.Cardinal;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.PlayerChatEvent;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ChatManager {

    private final Cardinal plugin;
    private final Map<UUID, ChatChannel> playerChannels = new ConcurrentHashMap<>();

    private static final MiniMessage mini = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer legacy = LegacyComponentSerializer.legacyAmpersand();

    public ChatManager(Cardinal plugin) {
        this.plugin = plugin;
        plugin.getServer().getEventManager().register(plugin, this);
    }

    public ChatChannel getChatChannel(UUID uuid) {
        return playerChannels.getOrDefault(uuid, ChatChannel.GLOBAL);
    }

    /** Public setter: only notifies if the value actually changed. */
    public void setChatChannel(UUID uuid, ChatChannel channel) {
        setChatChannel(uuid, channel, true);
    }

    /** Internal setter with notify toggle. */
    public void setChatChannel(UUID uuid, ChatChannel channel, boolean notify) {
        ChatChannel prev = playerChannels.put(uuid, channel);
        if (Objects.equals(prev, channel)) {
            // no change => no spam
            return;
        }
        if (notify) {
            plugin.getServer().getPlayer(uuid).ifPresent(p ->
                    p.sendMessage(Component.text("Channel set to " + channel.name(), NamedTextColor.YELLOW))
            );
        }
    }

    @Subscribe
    public void onChat(PlayerChatEvent event) {
        try {
            Player sender = event.getPlayer();
            ChatChannel channel = getChatChannel(sender.getUniqueId());
            String rawMessage = event.getMessage();

            // Prefix, Suffix, and Chat Color from LuckPerms
            String rawPrefix = plugin.getLuckPermsUtil().getPrefix(sender);
            String rawSuffix = plugin.getLuckPermsUtil().getSuffix(sender);
            String chatColor = plugin.getLuckPermsUtil().getMeta(sender, "chat-color");

            if (rawPrefix == null) rawPrefix = "";
            if (rawSuffix == null) rawSuffix = "";
            if (chatColor == null) chatColor = "&f"; // default to white

            String prefix = mini.serialize(legacy.deserialize(rawPrefix.trim()));
            String suffix = mini.serialize(legacy.deserialize(rawSuffix.trim()));
            String colorTag = "<" + chatColor.trim().toLowerCase() + ">";

            String nameTagOpen = "<name>";
            String nameTagClose = "</name>";
            String formattedMiniMessage;

            switch (channel) {
                case GLOBAL -> {
                    // Wrap the visible name (optional but nice for exact-span matching on the hub)
                    formattedMiniMessage = prefix + sender.getUsername() + suffix + ": " + colorTag + rawMessage;

                    // Send BOTH: sender + message
                    plugin.getMessaging().chatGlobal(sender.getUsername(), formattedMiniMessage);
                }
                case TEAM -> {
                    var teamOpt = plugin.getMatchManager().getTeamOf(sender.getUniqueId());
                    if (teamOpt.isEmpty()) {
                        sender.sendMessage(Component.text("You're not on a team.", NamedTextColor.RED));
                        if (getChatChannel(sender.getUniqueId()) != ChatChannel.GLOBAL) {
                            setChatChannel(sender.getUniqueId(), ChatChannel.GLOBAL);
                        }
                        return;
                    }
                    String teamName = teamOpt.get().getName();
                    formattedMiniMessage = "<blue>[Team] " + prefix + sender.getUsername() + suffix + ": " + colorTag + rawMessage;
                    plugin.getMessaging().chatTeam(teamName, sender.getUsername(), formattedMiniMessage);
                }
                case STAFF -> {
                    if (!sender.hasPermission("bearnation.channel.staff")) {
                        sender.sendMessage(Component.text("You do not have permission to use staff chat.", NamedTextColor.RED));
                        if (getChatChannel(sender.getUniqueId()) != ChatChannel.GLOBAL) {
                            setChatChannel(sender.getUniqueId(), ChatChannel.GLOBAL);
                        }
                        return;
                    }
                    formattedMiniMessage = "<light_purple>[Staff] " + prefix + sender.getUsername() + suffix + ": " + colorTag + rawMessage;
                    plugin.getMessaging().chatStaff(sender.getUsername(), formattedMiniMessage);
                }
            }

            // If you want to stop Velocity's default broadcast (to avoid double chat),
            // uncomment the next line on Velocity 3.2+:
            // event.setResult(PlayerChatEvent.ChatResult.denied());

        } catch (Exception e) {
            Cardinal.log("[ChatManager] Error in onChat: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /** Called by LP listener: don't notify; and don't rewrite if unchanged. */
    public void refreshChatFormat(UUID uuid) {
        setChatChannel(uuid, getChatChannel(uuid), false);
    }
}
