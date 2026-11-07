package com.bearnation.cardinal.command;

import com.bearnation.cardinal.Cardinal;
import com.bearnation.cardinal.chat.ChatChannel;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ChatCommand implements SimpleCommand {

    private final Cardinal plugin;
    private final LegacyComponentSerializer legacy = LegacyComponentSerializer.legacyAmpersand();

    public ChatCommand(Cardinal plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(Invocation invocation) {
        if (!(invocation.source() instanceof Player player)) {
            invocation.source().sendMessage(Component.text("Only players can use this command."));
            return;
        }

        String[] args = invocation.arguments();
        UUID uuid = player.getUniqueId();

        if (args.length == 0) {
            ChatChannel current = plugin.getChatManager().getChatChannel(uuid);
            player.sendMessage(legacy.deserialize("&fYou are currently in channel: &a" + current.name().toLowerCase()));
            return;
        }

        String arg = args[0].toLowerCase();

        switch (arg) {
            case "global" -> {
                plugin.getChatManager().setChatChannel(uuid, ChatChannel.GLOBAL);
                player.sendMessage(legacy.deserialize("&7Channel switched to: &aGlobal"));
            }
            case "team" -> {
                if (!plugin.getMatchManager().isMatchInProgress()) {
                    player.sendMessage(legacy.deserialize("&c[Error] &7Team chat is only available during a match."));
                    return;
                }

                plugin.getChatManager().setChatChannel(uuid, ChatChannel.TEAM);
                player.sendMessage(legacy.deserialize("&7Channel switched to: &aTeam"));
            }
            case "staff" -> {
                if (!player.hasPermission("bearnation.channel.staff")) {
                    player.sendMessage(legacy.deserialize("&9&lBN:&7 You do not have permission (( Missing rank &e&lSTAFF &7))."));
                    return;
                }

                plugin.getChatManager().setChatChannel(uuid, ChatChannel.STAFF);
                player.sendMessage(legacy.deserialize("&7Channel switched to: &aStaff"));
            }
            case "reset" -> {
                plugin.getChatManager().setChatChannel(uuid, ChatChannel.GLOBAL);
                player.sendMessage(legacy.deserialize("&fChannel switched to: &aGlobal"));
            }
            default -> {
                StringBuilder valid = new StringBuilder("&7Valid Channel(s): &fglobal, team");
                if (player.hasPermission("bearnation.channel.staff")) {
                    valid.append(", staff");
                }

                player.sendMessage(legacy.deserialize("&c[Error] &7Invalid channel.\n" + valid));
            }
        }
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        List<String> suggestions = new ArrayList<>();
        if (!(invocation.source() instanceof Player player)) return suggestions;

        suggestions.add("global");
        suggestions.add("team");
        suggestions.add("reset");

        if (player.hasPermission("bearnation.channel.staff")) {
            suggestions.add("staff");
        }

        return suggestions;
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return true;
    }
}
