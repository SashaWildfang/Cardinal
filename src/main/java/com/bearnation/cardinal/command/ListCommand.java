package com.bearnation.cardinal.command;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

import java.util.*;
import java.util.stream.Collectors;

public class ListCommand implements SimpleCommand {

    private final ProxyServer proxy;

    public ListCommand(ProxyServer proxy) {
        this.proxy = proxy;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        Collection<Player> allPlayers = proxy.getAllPlayers();
        int totalPlayers = allPlayers.size();

        List<Component> lines = new ArrayList<>();

        lines.add(Component.text("╭──────────────", NamedTextColor.DARK_GRAY));
        lines.add(Component.text("┃ ", NamedTextColor.DARK_GRAY)
                .append(Component.text("🧍 Players Online ", NamedTextColor.RED))
                .append(Component.text("(" + totalPlayers + ")", NamedTextColor.WHITE)));
        lines.add(Component.text("┃ ", NamedTextColor.DARK_GRAY)
                .append(Component.text(" ", NamedTextColor.GRAY))); // Spacer line

        for (RegisteredServer server : proxy.getAllServers()) {
            List<String> playerNames = server.getPlayersConnected().stream()
                    .map(Player::getUsername)
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .collect(Collectors.toList());

            if (playerNames.isEmpty()) continue;

            String playerList = String.join(", ", playerNames);

            Component line = Component.text("┃ ", NamedTextColor.DARK_GRAY)
                    .append(Component.text("⮞ ", NamedTextColor.GRAY))
                    .append(Component.text(server.getServerInfo().getName(), NamedTextColor.WHITE))
                    .append(Component.text(" (" + playerNames.size() + "): ", NamedTextColor.GRAY))
                    .append(Component.text(playerList, NamedTextColor.GREEN)); // &a

            lines.add(line);
        }

        if (lines.size() <= 3) {
            lines.add(Component.text("┃ ", NamedTextColor.DARK_GRAY)
                    .append(Component.text("No players are online.", NamedTextColor.DARK_GRAY)));
        }

        lines.add(Component.text("╰──────────────", NamedTextColor.DARK_GRAY));

        lines.forEach(source::sendMessage);
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission("cardinal.cmd.list");
    }
}
