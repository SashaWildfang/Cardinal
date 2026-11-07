package com.bearnation.cardinal.command;

import com.bearnation.cardinal.match.MatchManager;
import com.bearnation.cardinal.GamePhase;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.command.CommandSource;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class PhaseCommand implements SimpleCommand {

    private final MatchManager matchManager;

    public PhaseCommand(MatchManager matchManager) {
        this.matchManager = matchManager;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();

        if (!source.hasPermission("cardinal.cmd.phase")) {
            source.sendMessage(Component.text("You don’t have permission to use this command.", NamedTextColor.RED));
            return;
        }

        GamePhase phase = matchManager.getCurrentPhase();

        source.sendMessage(Component.text("Current Phase: ", NamedTextColor.GRAY)
                .append(Component.text(phase.name(), NamedTextColor.AQUA)));
    }
}
