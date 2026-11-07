package com.bearnation.cardinal.command;

import com.bearnation.cardinal.Cardinal;
import com.bearnation.cardinal.GamePhase;
import com.bearnation.cardinal.match.MatchAutoStarter;
import com.bearnation.cardinal.vote.MapVoteManager;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class StopVoteCommand implements SimpleCommand {

    private final MapVoteManager voteManager;
    private final MatchAutoStarter autoStarter;

    public StopVoteCommand(MapVoteManager voteManager, MatchAutoStarter autoStarter) {
        this.voteManager = voteManager;
        this.autoStarter = autoStarter;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();

        if (!source.hasPermission("cardinal.cmd.stopvote")) {
            source.sendMessage(Component.text("You don’t have permission to use this command.", NamedTextColor.RED));
            return;
        }

        if (!voteManager.isVotingActive()) {
            source.sendMessage(Component.text("There is no active vote to stop.", NamedTextColor.GRAY));
            return;
        }

        voteManager.cancelVoting();
        autoStarter.restartMatchFlow(); // Resets countdowns and phase
        source.sendMessage(Component.text("Map voting stopped and match reset.", NamedTextColor.GREEN));
    }
}
