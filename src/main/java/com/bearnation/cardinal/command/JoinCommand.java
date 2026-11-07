package com.bearnation.cardinal.command;

import com.bearnation.cardinal.Cardinal;
import com.bearnation.cardinal.GamePhase;
import com.bearnation.cardinal.match.MatchManager;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class JoinCommand implements SimpleCommand {

    private final MatchManager matchManager;

    public JoinCommand(MatchManager matchManager) {
        this.matchManager = matchManager;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();

        if (!(source instanceof Player player)) {
            source.sendMessage(Component.text("Only players can use this command.", NamedTextColor.RED));
            return;
        }

        // Only allow joining if game is currently in progress
        if (matchManager.getCurrentPhase() != GamePhase.IN_GAME) {
            player.sendMessage(Component.text("You can only join when a match is in progress.", NamedTextColor.RED));
            return;
        }

        // Attempt to connect the player to the CTF server
        Cardinal.getInstance().getProxy().getServer("ctf").ifPresentOrElse(
                server -> {
                    player.createConnectionRequest(server).connect().thenAccept(success -> {
                        if (success.isSuccessful()) {
                            player.sendMessage(Component.text("Sending you to the CTF server...", NamedTextColor.GREEN));
                        } else {
                            player.sendMessage(Component.text("Failed to connect to the CTF server.", NamedTextColor.RED));
                        }
                    });
                },
                () -> player.sendMessage(Component.text("CTF server not found.", NamedTextColor.RED))
        );
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission("cardinal.join");
    }
}
