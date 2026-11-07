package com.bearnation.cardinal.match;

import com.bearnation.cardinal.Cardinal;
import com.bearnation.cardinal.GamePhase;
import com.bearnation.cardinal.gamemode.GameMode;
import com.bearnation.cardinal.gamemode.GameModeRegistry;
import com.bearnation.cardinal.map.MapInfo;
import com.bearnation.cardinal.messaging.Messaging;
import com.bearnation.cardinal.teams.Team;
import com.bearnation.cardinal.teams.TeamManager;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

public class MatchManager {

    private final List<Player> playersInLobby = new ArrayList<>();
    private final TeamManager teamManager;

    private Match activeMatch;
    private MapInfo currentMap;
    private GameMode currentGameMode;
    private boolean matchInProgress = false;
    private GamePhase currentPhase = GamePhase.IDLE;

    private Messaging messaging;

    public MatchManager(TeamManager teamManager) {
        this.teamManager = teamManager;
    }

    public void setMessaging(Messaging messaging) {
        this.messaging = messaging;
    }

    public void playerJoinLobby(Player player) {
        if (!playersInLobby.contains(player)) {
            playersInLobby.add(player);
        }
        player.sendMessage(Cardinal.color("&a[Cardinal]&7 You’ve joined the lobby. Waiting for players..."));
    }

    public void playerLeaveLobby(Player player) {
        playersInLobby.remove(player);
    }

    public void startMatchAuto() {
        List<MapInfo> availableMaps = Cardinal.getInstance().getMapManager().getMaps();
        if (availableMaps.isEmpty()) {
            Cardinal.log("&a[Cardinal]&7 No maps found... exiting");
            return;
        }
        MapInfo selected = availableMaps.get(ThreadLocalRandom.current().nextInt(availableMaps.size()));
        startMatch(selected);
    }

    public boolean startMatch() {
        List<MapInfo> availableMaps = Cardinal.getInstance().getMapManager().getMaps();
        if (availableMaps.isEmpty() || playersInLobby.size() < 2) {
            return false;
        }
        MapInfo selected = availableMaps.get(ThreadLocalRandom.current().nextInt(availableMaps.size()));
        startMatch(selected);
        return true;
    }

    public void startPreGamePhase(MapInfo selectedMap) {
        if (matchInProgress) {
            Cardinal.log("&c[MatchManager] Cannot enter PRE_GAME — match already in progress.");
            return;
        }

        this.currentMap = selectedMap;
        this.currentGameMode = GameModeRegistry.getById(selectedMap.getGameMode());

        if (currentGameMode == null) {
            Cardinal.log("&cUnknown game mode: " + selectedMap.getGameMode());
            return;
        }

        setCurrentPhase(GamePhase.PRE_GAME, null); // notifies via NATS
        Cardinal.log("&b[MatchManager] Entering PRE_GAME fly phase for map: " + selectedMap.getName());

        // After 20 seconds, start the real match
        Cardinal.getInstance().getServer().getScheduler().buildTask(Cardinal.getInstance(), () -> {
            startMatch(selectedMap); // starts IN_GAME
        }).delay(20, TimeUnit.SECONDS).schedule();
    }

    public void startMatch(MapInfo map) {
        if (matchInProgress) {
            Cardinal.log("&cMatch already in progress.");
            return;
        }

        this.currentMap = map;
        this.currentGameMode = GameModeRegistry.getById(map.getGameMode());

        if (currentGameMode == null) {
            Cardinal.log("&cUnknown game mode: " + map.getGameMode());
            return;
        }

        this.activeMatch = new Match(map, currentGameMode);
        this.matchInProgress = true;
        this.currentPhase = GamePhase.IN_GAME;

        teamManager.clearTeams();

        Cardinal.log("&6[MatchManager] Starting match: &e" + map.getName());

        currentGameMode.onMatchStart(map);

        for (Player player : playersInLobby) {
            autoAssignTeam(player);
            currentGameMode.onPlayerJoin(player);
        }
        playersInLobby.clear();

        // Notify Hub + CTF about IN_GAME
        if (messaging != null) {
            messaging.updatePhaseForHub(GamePhase.IN_GAME.name());
            messaging.updatePhaseForCtf(GamePhase.IN_GAME.name());
        }
    }

    private void autoAssignTeam(Player player) {
        currentGameMode.getTeamNames().stream()
                .map(teamManager::getTeamByName)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .min(Comparator.comparingInt(t ->
                        (int) teamManager.getAllTeamAssignments().values().stream().filter(t::equals).count()))
                .ifPresent(team -> {
                    teamManager.assignPlayer(player, team);
                    player.sendMessage(Cardinal.color("&7You’ve been assigned to team &" + team.getColorCode() + team.getName()));
                });
    }

    public void endMatch(MapInfo map) {
        if (!matchInProgress) return;
        matchInProgress = false;

        currentGameMode.onMatchEnd(map);
        currentGameMode = null;
        currentMap = null;
        activeMatch.stop();
        teamManager.clearTeams();
    }

    public void forceEndMatch() {
        if (!matchInProgress) return;

        Cardinal.log("&c[MatchManager] Force-ending current match.");
        matchInProgress = false;

        if (activeMatch != null) activeMatch.stop();
        if (teamManager != null) teamManager.clearTeams();

        currentGameMode = null;
        currentMap = null;
    }

    public void onMatchEnd() {
        matchInProgress = false;
        currentPhase = GamePhase.IDLE;
        Cardinal.getInstance().getAutoStarter().startMonitoring();
        Cardinal.getInstance().setPhase(GamePhase.POST_GAME);
    }

    public boolean isMatchInProgress() { return matchInProgress; }
    public MapInfo getCurrentMap() { return currentMap; }
    public GameMode getCurrentGameMode() { return currentGameMode; }
    public Match getActiveMatch() { return activeMatch; }
    public List<Player> getPlayersInLobby() { return playersInLobby; }

    public boolean isPlayerOnTeam(UUID uuid) {
        return activeMatch != null && activeMatch.getTeamForPlayer(uuid).isPresent();
    }

    public Optional<Team> getTeamOf(UUID uuid) {
        if (activeMatch == null) return Optional.empty();
        return activeMatch.getTeamForPlayer(uuid);
    }

    public GamePhase getCurrentPhase() { return currentPhase; }

    public boolean setCurrentPhase(GamePhase phase, @Nullable CommandSource source) {
        if (phase == GamePhase.VOTING) {
            List<MapInfo> allMaps = Cardinal.getInstance().getMapManager().getMaps();
            if (allMaps.isEmpty()) {
                String failMsg = "&a[Cardinal] &cCannot start vote: No maps loaded.";
                Cardinal.log(failMsg);
                if (source != null) source.sendMessage(Cardinal.color(failMsg));

                // Fallback to IDLE and notify Hub/CTF
                this.currentPhase = GamePhase.IDLE;
                Cardinal.getInstance().setPhase(GamePhase.IDLE);
                Cardinal.log("&a[Cardinal]&7 Phase set to: &eIDLE");
                if (source != null) source.sendMessage(Cardinal.color("&a[Cardinal]&7 Phase set to: &eIDLE"));
                if (messaging != null) {
                    messaging.updatePhaseForHub("IDLE");
                    messaging.updatePhaseForCtf("IDLE");
                }
                return false;
            }

            List<MapInfo> maps = Cardinal.getInstance().getMapManager().pickRandomMaps(3);
            Cardinal.getInstance().getMapVoteManager().startVoting(maps);
        }

        this.currentPhase = phase;
        Cardinal.getInstance().setPhase(phase);

        Cardinal.log("&a[Cardinal]&7 Phase set to:" + phase.name());
        if (source != null) source.sendMessage(Cardinal.color("&a[Cardinal]&7 Phase set to:" + phase.name()));
        if (messaging != null) {
            messaging.updatePhaseForHub(phase.name());
            messaging.updatePhaseForCtf(phase.name());
        }

        return true;
    }

    public int getOnlinePlayersInMatchServer() {
        return (int) Cardinal.getInstance().getServer().getAllPlayers().stream()
                .filter(player -> player.getCurrentServer()
                        .map(srv -> srv.getServerInfo().getName().equalsIgnoreCase("match")) // adjust if needed
                        .orElse(false))
                .count();
    }

    public boolean haveAllQueuedPlayersVoted() {
        Set<UUID> queued = Cardinal.getInstance().getAutoStarter().getQueuedPlayers();

        List<Player> inLobby = Cardinal.getInstance().getServer().getAllPlayers().stream()
                .filter(p -> p.getCurrentServer().isPresent() &&
                        p.getCurrentServer().get().getServerInfo().getName().equalsIgnoreCase("hub"))
                .toList();

        List<UUID> queuedInLobby = inLobby.stream()
                .map(Player::getUniqueId)
                .filter(queued::contains)
                .toList();

        boolean allVoted = !queuedInLobby.isEmpty() &&
                queuedInLobby.stream().allMatch(uuid -> Cardinal.getInstance().getMapVoteManager().hasPlayerVoted(uuid));

        Cardinal.log("&7[Debug] queuedInLobby=" + queuedInLobby.size() +
                " | allVoted=" + allVoted +
                " | totalQueued=" + queued.size() +
                " | votedCount=" +
                queuedInLobby.stream().filter(uuid -> Cardinal.getInstance().getMapVoteManager().hasPlayerVoted(uuid)).count());

        return allVoted;
    }
}
