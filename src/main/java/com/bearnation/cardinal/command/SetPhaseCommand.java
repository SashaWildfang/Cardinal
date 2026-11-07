package com.bearnation.cardinal.command;

import com.bearnation.cardinal.GamePhase;
import com.bearnation.cardinal.match.MatchManager;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class SetPhaseCommand implements SimpleCommand {

    private final MatchManager matchManager;

    public SetPhaseCommand(MatchManager matchManager) {
        this.matchManager = matchManager;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();

        if (!source.hasPermission("cardinal.cmd.setphase")) {
            source.sendMessage(Component.text("You don’t have permission to use this command.", NamedTextColor.RED));
            return;
        }

        if (invocation.arguments().length != 1) {
            source.sendMessage(Component.text("Usage: /setphase <IDLE|VOTING|IN_GAME|POST_GAME>", NamedTextColor.RED));
            return;
        }

        try {
            GamePhase requestedPhase = GamePhase.valueOf(invocation.arguments()[0].toUpperCase(Locale.ROOT));
            GamePhase currentPhase = matchManager.getCurrentPhase();

            // Prevent switching to same phase
            if (currentPhase == requestedPhase) {
                source.sendMessage(Component.text("Already in phase: ", NamedTextColor.RED)
                        .append(Component.text(currentPhase.name(), NamedTextColor.YELLOW)));
                return;
            }

            // Block switching away from POST_VOTING unless to IN_GAME
            if (currentPhase == GamePhase.POST_VOTING && requestedPhase != GamePhase.IN_GAME) {
                source.sendMessage(Component.text("Cannot interrupt post-voting countdown. Let it finish or use /force_start.", NamedTextColor.RED));
                return;
            }

            // Block going to VOTING mid-game
            if (currentPhase == GamePhase.IN_GAME && requestedPhase == GamePhase.VOTING) {
                source.sendMessage(Component.text("Cannot enter voting while a match is in progress.", NamedTextColor.RED));
                return;
            }

            if (requestedPhase == GamePhase.VOTING &&
                    matchManager.getPlayersInLobby().size() < 2) {
                source.sendMessage(Component.text("Cannot start voting — not enough players in lobby (min 2).", NamedTextColor.RED));
                return;
            }

            // Call actual transition
            boolean success = matchManager.setCurrentPhase(requestedPhase, source);

            if (!success) {
                source.sendMessage(Component.text("Phase change rejected. Current phase: ", NamedTextColor.RED)
                        .append(Component.text(currentPhase.name(), NamedTextColor.YELLOW)));
            } else {
                source.sendMessage(Component.text("Phase set to ", NamedTextColor.GREEN)
                        .append(Component.text(requestedPhase.name(), NamedTextColor.YELLOW)));
            }

        } catch (IllegalArgumentException e) {
            source.sendMessage(Component.text("Invalid phase. Use: IDLE, VOTING, IN_GAME, POST_GAME", NamedTextColor.RED));
        }
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        if (invocation.arguments().length == 1) {
            String input = invocation.arguments()[0].toLowerCase(Locale.ROOT);
            return List.of(GamePhase.values()).stream()
                    .map(Enum::name)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(input))
                    .collect(Collectors.toList());
        }
        return List.of();
    }
}
