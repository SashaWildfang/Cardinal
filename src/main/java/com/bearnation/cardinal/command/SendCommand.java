package com.bearnation.cardinal.command;

import com.bearnation.cardinal.Cardinal;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.format.NamedTextColor.*;

public class SendCommand implements SimpleCommand {

    private final Cardinal plugin;

    public SendCommand(Cardinal plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        String[] args = invocation.arguments();

        if (!source.hasPermission("cardinal.cmd.send")) {
            source.sendMessage(text("You do not have permission to use this command.", RED));
            return;
        }

        if (args.length != 2) {
            source.sendMessage(text("Usage: /send <player> <server>", RED));
            return;
        }

        String playerName = args[0];
        String serverName = args[1];

        Optional<Player> optionalPlayer = plugin.getServer().getPlayer(playerName);
        if (optionalPlayer.isEmpty()) {
            source.sendMessage(text("Player not found: " + playerName, RED));
            return;
        }

        Optional<RegisteredServer> optionalServer = plugin.getServer().getServer(serverName);
        if (optionalServer.isEmpty()) {
            source.sendMessage(text("Server not found: " + serverName, RED));
            return;
        }

        Player player = optionalPlayer.get();
        RegisteredServer server = optionalServer.get();

        player.createConnectionRequest(server).connect().whenComplete((result, error) -> {
            if (result.isSuccessful()) {
                source.sendMessage(text("Sent " + player.getUsername() + " to " + server.getServerInfo().getName(), GREEN));
            } else {
                source.sendMessage(text("Failed to send player to server.", RED));
            }
        });
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        String[] args = invocation.arguments();

        if (args.length == 1) {
            return plugin.getServer().getAllPlayers().stream()
                    .map(Player::getUsername)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .collect(Collectors.toList());
        }

        if (args.length == 2) {
            return plugin.getServer().getAllServers().stream()
                    .map(s -> s.getServerInfo().getName())
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT)))
                    .collect(Collectors.toList());
        }

        return List.of();
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission("cardinal.cmd.send");
    }
}
