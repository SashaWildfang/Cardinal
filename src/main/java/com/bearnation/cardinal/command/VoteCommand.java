package com.bearnation.cardinal.command;

import com.bearnation.cardinal.Cardinal;
import com.bearnation.cardinal.map.MapInfo;
import com.bearnation.cardinal.vote.MapVoteManager;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;

import java.util.*;

public class VoteCommand implements SimpleCommand {

    private final MapVoteManager voteManager;

    public VoteCommand(MapVoteManager voteManager) {
        this.voteManager = voteManager;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        String[] args = invocation.arguments();

        if (!(source instanceof Player player)) {
            source.sendMessage(Cardinal.color("&cOnly players can vote."));
            return;
        }

        UUID uuid = player.getUniqueId();
        List<MapInfo> options = voteManager.getCurrentOptions();

        if (!voteManager.isVotingActive() || options.isEmpty()) {
            player.sendMessage(Cardinal.color("&cThere is no active vote right now."));
            return;
        }

        if (voteManager.isVotePaused()) {
            player.sendMessage(Cardinal.color("&cVoting is currently paused. Please wait for it to resume."));
            return;
        }

        if (args.length != 1) {
            player.sendMessage(Cardinal.color("&cUsage: /vote <# or map name>"));
            return;
        }



        String input = args[0];
        Optional<MapInfo> selected = Optional.empty();
        int voteIndex = -1;

        // Try vote by number
        try {
            int inputIndex = Integer.parseInt(input);
            if (inputIndex >= 1 && inputIndex <= options.size()) {
                selected = Optional.of(options.get(inputIndex - 1));
                voteIndex = inputIndex - 1;
            }
        } catch (NumberFormatException ignored) {}

        // Try fuzzy match by map name
        if (selected.isEmpty()) {
            String loweredInput = input.toLowerCase();
            for (int i = 0; i < options.size(); i++) {
                MapInfo map = options.get(i);
                if (map.getName().toLowerCase().contains(loweredInput)) {
                    selected = Optional.of(map);
                    voteIndex = i;
                    break;
                }
            }
        }

        if (selected.isEmpty() || voteIndex == -1) {
            player.sendMessage(Cardinal.color("&cInvalid vote. Use a number or part of the map name."));
            return;
        }

        MapInfo selectedMap = selected.get();

        if (voteManager.hasPlayerVoted(uuid)) {
            String previousMapId = voteManager.getPlayerVote(uuid);
            if (previousMapId != null && previousMapId.equalsIgnoreCase(selectedMap.getId())) {
                player.sendMessage(Cardinal.color("&eYou already voted for &6" + selectedMap.getName() + "&e."));
                return;
            }

            String previousMapName = options.stream()
                    .filter(m -> m.getId().equalsIgnoreCase(previousMapId))
                    .map(MapInfo::getName)
                    .findFirst()
                    .orElse("Unknown");

            if (!player.hasPermission("cardinal.vote.revote")) {
                player.sendMessage(Cardinal.color("&cYou already voted for &e" + previousMapName + "&c."));
                player.sendMessage(Cardinal.color("&7Only &6donors &7can change their vote."));
                return;
            }

            player.sendMessage(Cardinal.color("&eChanging vote from &6" + previousMapName + " &eto &a" + selectedMap.getName() + "&e..."));
        }

        voteManager.castVote(uuid, selectedMap.getId(), voteIndex);
        player.sendMessage(Cardinal.color("&aYou voted for &e" + selectedMap.getName() + "&a."));
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return true;
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        List<MapInfo> options = voteManager.getCurrentOptions();
        if (options.isEmpty()) return List.of();

        Set<String> suggestions = new LinkedHashSet<>();
        for (int i = 0; i < options.size(); i++) {
            MapInfo info = options.get(i);
            suggestions.add(String.valueOf(i + 1));     // /vote 1
            suggestions.add(info.getName());            // /vote Tundra
        }

        return new ArrayList<>(suggestions);
    }
}
