package com.bearnation.cardinal.command;

import com.bearnation.cardinal.Cardinal;
import com.bearnation.cardinal.chat.ChatChannel;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.UUID;

public class StaffChatCommand implements SimpleCommand {

    private final Cardinal plugin;
    private final LegacyComponentSerializer legacy = LegacyComponentSerializer.legacyAmpersand();

    public StaffChatCommand(Cardinal plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(Invocation invocation) {
        if (!(invocation.source() instanceof Player player)) {
            invocation.source().sendMessage(Component.text("Only players can use this command."));
            return;
        }

        if (!player.hasPermission("bearnation.cmd.staffchat")) {
            player.sendMessage(legacy.deserialize("&9&lBN:&7 You do not have permission (( Missing rank &e&lSTAFF &7))."));
            return;
        }

        String[] args = invocation.arguments();
        UUID uuid = player.getUniqueId();

        if (args.length == 0) {
            // Toggle chat channel
            ChatChannel current = plugin.getChatManager().getChatChannel(uuid);
            if (current == ChatChannel.STAFF) {
                plugin.getChatManager().setChatChannel(uuid, ChatChannel.GLOBAL);
                player.sendMessage(legacy.deserialize("&fChannel switched to: &aGlobal"));
            } else {
                plugin.getChatManager().setChatChannel(uuid, ChatChannel.STAFF);
                player.sendMessage(legacy.deserialize("&fChannel switched to: &aStaff"));
            }
            return;
        }

        // Send one-time message to staff
        String message = String.join(" ", args);
        String formatted = "&d[Staff] &f" + player.getUsername() + ": &7" + message;
        Component component = legacy.deserialize(formatted);

        plugin.getServer().getAllPlayers().stream()
                .filter(p -> p.hasPermission("bearnation.cmd.staffchat"))
                .forEach(p -> p.sendMessage(component));

        Cardinal.log("&d[STAFF] " + player.getUsername() + ": &7" + message);
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source() instanceof Player p && p.hasPermission("bearnation.cmd.staffchat");
    }
}
