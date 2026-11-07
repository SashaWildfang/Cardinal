package com.bearnation.cardinal.command;

import com.bearnation.cardinal.Cardinal;
import com.bearnation.cardinal.GamePhase;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class ResumeVoteCommand implements SimpleCommand {

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();

        if (!(source instanceof Player player)) {
            source.sendMessage(Component.text("Only players can use this command.", NamedTextColor.RED));
            return;
        }

        if (!player.hasPermission("cardinal.cmd.resumevote")) {
            player.sendMessage(Component.text("You don’t have permission to resume voting.", NamedTextColor.RED));
            return;
        }

        if (Cardinal.getInstance().getMatchManager().getCurrentPhase() != GamePhase.VOTING) {
            player.sendMessage(Component.text("Voting can only be resumed during the VOTING phase.", NamedTextColor.RED));
            return;
        }

        boolean success = Cardinal.getInstance().getMapVoteManager().resumeVoting();

        if (success) {
            Component msg = Component.text()
                    .append(Component.text("▶ ", NamedTextColor.GREEN))
                    .append(Component.text(player.getUsername(), NamedTextColor.AQUA))
                    .append(Component.text(" has resumed the map vote.", NamedTextColor.GREEN))
                    .build();

            Cardinal.getInstance().getServer().getAllPlayers().forEach(p -> p.sendMessage(msg));
        } else {
            player.sendMessage(Component.text("Voting is not paused or already completed.", NamedTextColor.GRAY));
        }
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission("cardinal.cmd.resumevote");
    }
}
