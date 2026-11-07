package com.bearnation.cardinal.command;

import com.bearnation.cardinal.Cardinal;
import com.bearnation.cardinal.match.MatchManager;
import com.bearnation.cardinal.map.MapInfo;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.List;

public class ForceStartCommand implements SimpleCommand {

    private final MatchManager matchManager;

    public ForceStartCommand(MatchManager matchManager) {
        this.matchManager = matchManager;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();

        if (!source.hasPermission("cardinal.cmd.forcestart")) {
            source.sendMessage(Component.text("You don't have permission.", NamedTextColor.RED));
            return;
        }

        List<Player> players = matchManager.getPlayersInLobby();
        List<MapInfo> maps = Cardinal.getInstance().getMapManager().getMaps();

        if (players.size() < 2 && maps.isEmpty()) {
            source.sendMessage(Component.text("Cannot start match: ", NamedTextColor.RED)
                    .append(Component.text("not enough players", NamedTextColor.YELLOW))
                    .append(Component.text(" and ", NamedTextColor.GRAY))
                    .append(Component.text("no maps found.", NamedTextColor.YELLOW)));
            return;
        }

        if (players.size() < 2) {
            source.sendMessage(Component.text("Cannot start match: ", NamedTextColor.RED)
                    .append(Component.text("need at least 2 players in lobby.", NamedTextColor.YELLOW)));
            return;
        }

        if (maps.isEmpty()) {
            source.sendMessage(Component.text("Cannot start match: ", NamedTextColor.RED)
                    .append(Component.text("no maps loaded.", NamedTextColor.YELLOW)));
            return;
        }

        boolean started = matchManager.startMatch();
        if (started) {
            source.sendMessage(Component.text("✔ Forced match start.", NamedTextColor.GREEN));
        } else {
            source.sendMessage(Component.text("Something went wrong. Could not force start.", NamedTextColor.RED));
        }
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission("cardinal.cmd.forcestart");
    }
}
