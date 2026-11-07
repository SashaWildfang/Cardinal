package com.bearnation.cardinal.teams;

import com.velocitypowered.api.proxy.Player;

import java.util.*;

public class TeamManager {

    private final Map<String, Team> teams = new LinkedHashMap<>();
    private final Map<UUID, Team> playerTeams = new HashMap<>();

    public TeamManager() {
        // Register default teams
        registerTeam(new Team("Red", "RED", 14, "#B02E26"));
        registerTeam(new Team("Blue", "BLUE", 11, "#364BBA"));
        registerTeam(new Team("Yellow", "YELLOW", 4, "#FED83D"));
        registerTeam(new Team("Green", "GREEN", 5, "#5E7C16"));
    }

    public void registerTeam(Team team) {
        teams.put(team.getName().toLowerCase(), team);
    }

    public Optional<Team> getTeamByName(String name) {
        return Optional.ofNullable(teams.get(name.toLowerCase()));
    }

    public List<Team> getAllTeams() {
        return new ArrayList<>(teams.values());
    }

    public List<Team> getTeams() {
        return getAllTeams();
    }

    public List<String> getTeamNames() {
        return teams.values().stream()
                .map(Team::getName)
                .toList();
    }

    public void assignPlayer(Player player, Team team) {
        playerTeams.put(player.getUniqueId(), team);
    }

    public Optional<Team> getTeamForPlayer(Player player) {
        return Optional.ofNullable(playerTeams.get(player.getUniqueId()));
    }

    public void leaveTeam(Player player) {
        playerTeams.remove(player.getUniqueId());
    }

    public Map<UUID, Team> getAllTeamAssignments() {
        return Collections.unmodifiableMap(playerTeams);
    }

    public void clearTeams() {
        playerTeams.clear();
    }

    public Optional<Team> getTeamWithFewestPlayers() {
        return teams.values().stream()
                .min(Comparator.comparingInt(team ->
                        (int) playerTeams.values().stream().filter(t -> t.equals(team)).count()
                ));
    }

    public Optional<Team> getTeamOf(UUID uuid) {
        return Optional.ofNullable(playerTeams.get(uuid));
    }

}
