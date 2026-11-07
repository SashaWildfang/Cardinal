package com.bearnation.cardinal.command;

import com.bearnation.cardinal.match.MatchManager;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;

public class LeaveQueueCommand implements SimpleCommand {

    private final MatchManager matchManager;

    public LeaveQueueCommand(MatchManager matchManager) {
        this.matchManager = matchManager;
    }

    @Override
    public void execute(Invocation invocation) {
        if (!(invocation.source() instanceof Player player)) {
            invocation.source().sendMessage(Component.text("Only players can use this command."));
            return;
        }

        if (!player.hasPermission("cardinal.queue.leave")) {
            player.sendMessage(Component.text("§cYou don’t have permission to leave the queue."));
            return;
        }

        if (!matchManager.getPlayersInLobby().contains(player)) {
            player.sendMessage(Component.text("§eYou are not currently in the queue."));
            return;
        }

        matchManager.playerLeaveLobby(player);
        player.sendMessage(Component.text("§cYou have left the queue."));
    }
}
