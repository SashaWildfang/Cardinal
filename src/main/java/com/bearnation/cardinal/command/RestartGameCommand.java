package com.bearnation.cardinal.command;

import com.bearnation.cardinal.Cardinal;
import com.bearnation.cardinal.bus.Bus;
import com.google.gson.JsonObject;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class RestartGameCommand implements SimpleCommand {

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();

        if (!(source instanceof Player player)) {
            source.sendMessage(Component.text("Only players can use this command.", NamedTextColor.RED));
            return;
        }

        if (!player.hasPermission("cardinal.cmd.restartgame")) {
            player.sendMessage(Component.text("You do not have permission to use this command.", NamedTextColor.RED));
            return;
        }

        // Reset scoreboard for everyone (no kind forced) and send to hub
        Cardinal.getInstance().getServer().getAllPlayers().forEach(p -> {
            JsonObject payload = new JsonObject();
            payload.addProperty("uuid", p.getUniqueId().toString());
            Cardinal.getInstance().getMessaging().getBus().publish(
                    "bn.hub",
                    Bus.env("reset_scoreboard", "broadcast", payload).toString()
            );

            Cardinal.getInstance().getServer().getServer("hub").ifPresent(server ->
                    p.createConnectionRequest(server).fireAndForget()
            );
        });

        // Restart or clean up depending on auto-start
        if (Cardinal.getInstance().getAutoStarter().isAutoStartEnabled()) {
            Cardinal.getInstance().getAutoStarter().restartMatchFlow();
        } else {
            Cardinal.getInstance().getAutoStarter().cancelCountdownTask();
            Cardinal.getInstance().getAutoStarter().cancelVoteTransitionTask();
            Cardinal.getInstance().getMatchManager().forceEndMatch();
            Cardinal.getInstance().getMatchManager().setCurrentPhase(com.bearnation.cardinal.GamePhase.IDLE, null);
        }

        // Clear action bars
        Cardinal.getInstance().getServer().getAllPlayers()
                .forEach(p -> p.sendActionBar(Component.empty()));

        // Feedback
        player.sendMessage(Component.text("✅ You have reset the match and returned all players to the lobby.", NamedTextColor.GREEN));

        Component broadcast = Component.text()
                .append(Component.text("⚠ ", NamedTextColor.YELLOW))
                .append(Component.text(player.getUsername(), NamedTextColor.AQUA))
                .append(Component.text(" has reset the match. Returning to lobby...", NamedTextColor.YELLOW))
                .build();

        Cardinal.getInstance().getServer().getAllPlayers().forEach(p -> p.sendMessage(broadcast));
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return true;
    }
}
