package com.bearnation.cardinal.command;

import com.bearnation.cardinal.Cardinal;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

public class FindCommand implements SimpleCommand {

    private final Cardinal plugin;

    public FindCommand(Cardinal plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        String[] args = invocation.arguments();

        if (!source.hasPermission("cardinal.cmd.find")) {
            source.sendMessage(Component.text("You do not have permission to use this command.", NamedTextColor.RED));
            return;
        }

        // No argument: show the sender's location
        if (args.length == 0) {
            if (source instanceof Player player) {
                player.getCurrentServer().ifPresentOrElse(
                        server -> source.sendMessage(Component.text("You are currently on ", NamedTextColor.GREEN)
                                .append(Component.text(server.getServerInfo().getName(), NamedTextColor.YELLOW))),
                        () -> source.sendMessage(Component.text("You are not connected to any server.", NamedTextColor.RED))
                );
            } else {
                source.sendMessage(Component.text("Usage: /find <player>", NamedTextColor.RED));
            }
            return;
        }

        // /find <player>
        String targetName = args[0];
        Optional<Player> optionalTarget = plugin.getServer().getPlayer(targetName);

        if (optionalTarget.isEmpty()) {
            source.sendMessage(Component.text("Player not found or not online: ", NamedTextColor.RED)
                    .append(Component.text(targetName, NamedTextColor.YELLOW)));
            return;
        }

        Player target = optionalTarget.get();
        String serverName = target.getCurrentServer()
                .map(s -> s.getServerInfo().getName())
                .orElse("unknown");

        source.sendMessage(Component.text("Player ", NamedTextColor.GREEN)
                .append(Component.text(target.getUsername(), NamedTextColor.YELLOW))
                .append(Component.text(" is on ", NamedTextColor.GREEN))
                .append(Component.text(serverName, NamedTextColor.AQUA)));
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission("cardinal.cmd.find");
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        if (invocation.arguments().length == 1) {
            String input = invocation.arguments()[0].toLowerCase(Locale.ROOT);
            return plugin.getServer().getAllPlayers().stream()
                    .map(Player::getUsername)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(input))
                    .collect(Collectors.toList());
        }
        return List.of();
    }
}
