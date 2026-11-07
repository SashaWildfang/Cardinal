package com.bearnation.cardinal.match;

import com.bearnation.cardinal.Cardinal;
import com.bearnation.cardinal.GamePhase;
import com.bearnation.cardinal.gamemode.GameMode;
import com.bearnation.cardinal.map.MapInfo;
import com.bearnation.cardinal.teams.Team;
import com.bearnation.cardinal.teams.TeamManager;
import com.velocitypowered.api.proxy.Player;

import java.util.*;

public class Match {

    private final MapInfo map;
    private final GameMode gameMode;
    private final TeamManager teamManager;
    private final Set<UUID> participants = new HashSet<>();
    private boolean started = false;

    public Match(MapInfo map, GameMode gameMode) {
        this.map = map;
        this.gameMode = gameMode;
        this.teamManager = new TeamManager();
    }

    public void start() {
        if (started) return;
        started = true;
        gameMode.onMatchStart(map);
        Cardinal.getInstance().setPhase(GamePhase.IN_GAME);

    }

    public void stop() {
        if (!started) return;
        gameMode.onMatchEnd(map);
    }

    public boolean hasStarted() {
        return started;
    }

    public void join(Player player) {
        participants.add(player.getUniqueId());

        // If the game mode supports teams
        if (gameMode.getTeamCount() > 1) {
            Optional<Team> teamOpt = teamManager.getTeamWithFewestPlayers();
            if (teamOpt.isPresent()) {
                Team team = teamOpt.get();
                team.addPlayer(player);
                teamManager.assignPlayer(player, team);
                player.sendMessage(Cardinal.mm.deserialize("&aYou joined team &f" + team.getName()));
            } else {
                player.sendMessage(Cardinal.mm.deserialize("&cNo available team to join."));
            }
        } else {
            player.sendMessage(Cardinal.mm.deserialize("&aYou joined the match!"));
        }

        broadcast("&e" + player.getUsername() + " joined the match.");
    }

    public void broadcast(String message) {
        for (UUID uuid : participants) {
            Cardinal.getInstance().getServer().getPlayer(uuid).ifPresent(p ->
                    p.sendMessage(Cardinal.mm.deserialize(message))
            );
        }
    }

    public MapInfo getMap() {
        return map;
    }

    public GameMode getGameMode() {
        return gameMode;
    }

    public TeamManager getTeamManager() {
        return teamManager;
    }

    public Set<UUID> getParticipants() {
        return participants;
    }

    public Optional<Team> getTeamForPlayer(UUID uuid) {
        return teamManager.getTeamOf(uuid);
    }

}
