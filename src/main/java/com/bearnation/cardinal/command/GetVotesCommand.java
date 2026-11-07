package com.bearnation.cardinal.command;

import com.bearnation.cardinal.map.MapInfo;
import com.bearnation.cardinal.vote.MapVoteManager;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.List;
import java.util.Map;

public class GetVotesCommand implements SimpleCommand {

    private final MapVoteManager voteManager;

    public GetVotesCommand(MapVoteManager voteManager) {
        this.voteManager = voteManager;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource src = invocation.source();

        if (!(src instanceof Player) || !src.hasPermission("cardinal.cmd.getvotes")) {
            src.sendMessage(Component.text("You don't have permission to run this command.", NamedTextColor.RED));
            return;
        }

        if (!voteManager.isVotingActive()) {
            src.sendMessage(Component.text("There is no active vote right now.", NamedTextColor.YELLOW));
            return;
        }

        List<MapInfo> options = voteManager.getCurrentOptions();
        Map<String, Integer> counts = voteManager.getVoteCounts();

        src.sendMessage(Component.text("Current votes:", NamedTextColor.GREEN));

        for (int i = 0; i < options.size(); i++) {
            MapInfo opt = options.get(i);
            String name = opt.getName();
            int voteCount = counts.getOrDefault(opt.getId(), 0);
            src.sendMessage(
                    Component.text(String.format(" [%d] %s — %d vote%s",
                            i + 1,
                            name,
                            voteCount,
                            voteCount == 1 ? "" : "s"
                    ), NamedTextColor.AQUA)
            );
        }
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return true;
    }
}
