package com.bearnation.cardinal.command;

import com.bearnation.cardinal.Cardinal;
import com.bearnation.cardinal.match.MatchManager;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class StartMatchCommand implements SimpleCommand {

    private final ProxyServer server;
    private final MatchManager matchManager;

    public StartMatchCommand(ProxyServer server, MatchManager matchManager) {
        this.server = server;
        this.matchManager = matchManager;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();

        boolean success = matchManager.startMatch();

        if (success) {
            source.sendMessage(Component.text("Match started successfully!", NamedTextColor.GREEN));
        } else {
            source.sendMessage(Component.text("Failed to start match: Not enough players or no maps loaded.", NamedTextColor.RED));
        }
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission("cardinal.start");
    }
}
