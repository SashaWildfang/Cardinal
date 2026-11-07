package com.bearnation.cardinal.command;

import com.bearnation.cardinal.teams.Team;
import com.bearnation.cardinal.teams.TeamManager;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.Optional;

public class TeamJoinCommand implements SimpleCommand {

    private final TeamManager teamManager;

    public TeamJoinCommand(TeamManager teamManager) {
        this.teamManager = teamManager;
    }

    @Override
    public void execute(Invocation invocation) {
        if (!(invocation.source() instanceof Player player)) {
            invocation.source().sendMessage(Component.text("Only players can use this command.", NamedTextColor.RED));
            return;
        }

        String[] args = invocation.arguments();
        if (args.length != 1) {
            player.sendMessage(Component.text("Usage: /join <team>", NamedTextColor.YELLOW));
            return;
        }

        String teamName = args[0];
        Optional<Team> optionalTeam = teamManager.getTeamByName(teamName);

        if (optionalTeam.isEmpty()) {
            player.sendMessage(Component.text("Team not found: " + teamName, NamedTextColor.RED));
            return;
        }

        Team team = optionalTeam.get();
        teamManager.assignPlayer(player, team);
        player.sendMessage(Component.text("You joined team " + team.getName() + "!", team.getColor()));
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return true;
    }
}
