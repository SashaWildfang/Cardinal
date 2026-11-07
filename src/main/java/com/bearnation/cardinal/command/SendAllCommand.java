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

public class SendAllCommand implements SimpleCommand {

    private final Cardinal plugin;

    public SendAllCommand(Cardinal plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        String[] args = invocation.arguments();

        if (!source.hasPermission("cardinal.cmd.sendall")) {
            source.sendMessage(text("You do not have permission to use this command.", RED));
            return;
        }

        if (args.length != 1) {
            source.sendMessage(text("Usage: /sendall <server>", RED));
            return;
        }

        String serverName = args[0];
        Optional<RegisteredServer> optionalServer = plugin.getServer().getServer(serverName);
        if (optionalServer.isEmpty()) {
            source.sendMessage(text("Server not found: " + serverName, RED));
            return;
        }

        RegisteredServer server = optionalServer.get();
        int count = 0;

        for (Player player : plugin.getServer().getAllPlayers()) {
            player.createConnectionRequest(server).connect();
            count++;
        }

        source.sendMessage(text("Sent " + count + " player(s) to " + server.getServerInfo().getName(), GREEN));
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        String[] args = invocation.arguments();

        if (args.length == 1) {
            return plugin.getServer().getAllServers().stream()
                    .map(s -> s.getServerInfo().getName())
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .collect(Collectors.toList());
        }

        return List.of();
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission("cardinal.cmd.sendall");
    }
}
