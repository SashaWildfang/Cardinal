package com.bearnation.cardinal.map;

import com.bearnation.cardinal.Cardinal;
import com.bearnation.cardinal.teams.Team;
import com.bearnation.cardinal.teams.TeamManager;

import java.nio.file.Path;
import java.util.*;

public class MapInfo {

    private final String id;
    private final String name;
    private final String author;
    private final String addedDate;
    private final String gameMode;
    private final String schematic;
    private final int requiredPlayers;

    private final List<String> supportedModes;
    private final Map<String, Team> teams;
    private final Map<String, String> teamSpawnRegions;
    private final Map<String, String> teamFlagRegions;
    private final Map<String, Map<String, Object>> regionFlags;
    private final Path folder;
    private final Map<String, RegionBounds> regionBounds;
    private final Map<String, Vector3> teamFlagSpawns;
    private final int pasteX;
    private final int pasteY;
    private final int pasteZ;

    private final String startRegion;
    private final String endRegion;

    public MapInfo(String id, Path mapFolder, Map<String, Object> config) {
        this.id = id;
        this.folder = mapFolder;

        // Validate required string fields
        this.name = getRequiredString(config, "name");
        this.author = getRequiredString(config, "author");
        this.addedDate = getRequiredString(config, "date_added");
        this.gameMode = getRequiredString(config, "gamemode");
        this.schematic = getRequiredString(config, "schematic");
        this.pasteX = config.containsKey("paste_x") ? ((Number) config.get("paste_x")).intValue() : 0;
        this.pasteY = config.containsKey("paste_y") ? ((Number) config.get("paste_y")).intValue() : 64;
        this.pasteZ = config.containsKey("paste_z") ? ((Number) config.get("paste_z")).intValue() : 0;

        // Optional integer with default
        this.requiredPlayers = config.containsKey("required_players")
                ? ((Number) config.get("required_players")).intValue()
                : 4;

        // List fields
        this.supportedModes = config.getOrDefault("supported_modes", List.of()) instanceof List<?> list
                ? list.stream().map(Object::toString).toList()
                : List.of();

        this.startRegion = config.getOrDefault("start_region", null) instanceof String s ? s : null;
        this.endRegion = config.getOrDefault("end_region", null) instanceof String s ? s : null;

        // Region flags
        this.regionFlags = new HashMap<>();
        Object flagsSection = config.get("flags");
        if (flagsSection instanceof Map<?, ?> regionMap) {
            for (Map.Entry<?, ?> entry : regionMap.entrySet()) {
                String regionName = entry.getKey().toString();
                Object regionFlagsRaw = entry.getValue();

                if (regionFlagsRaw instanceof Map<?, ?> flagsRaw) {
                    Map<String, Object> flags = new HashMap<>();
                    for (Map.Entry<?, ?> flagEntry : flagsRaw.entrySet()) {
                        flags.put(flagEntry.getKey().toString(), flagEntry.getValue());
                    }
                    regionFlags.put(regionName, flags);
                }
            }
        }

        // Team section
        this.teams = new LinkedHashMap<>();
        this.teamSpawnRegions = new HashMap<>();
        this.teamFlagRegions = new HashMap<>();

        Object teamsSection = config.get("teams");
        if (teamsSection instanceof Map<?, ?> teamMap) {
            TeamManager defaultTeamManager = new TeamManager();

            for (Map.Entry<?, ?> entry : teamMap.entrySet()) {
                String teamName = entry.getKey().toString();
                Map<String, Object> data = (Map<String, Object>) entry.getValue();

                String displayName = teamName.toUpperCase();
                int colorCode = defaultTeamManager.getTeamByName(teamName)
                        .map(Team::getColorCode)
                        .orElse(0);
                String hexColor = defaultTeamManager.getTeamByName(teamName)
                        .map(Team::getHexColor)
                        .orElse("#AAAAAA");

                Team team = new Team(teamName, displayName, colorCode, hexColor);
                this.teams.put(teamName.toLowerCase(), team);

                if (data.containsKey("spawn_region")) {
                    teamSpawnRegions.put(teamName.toLowerCase(), data.get("spawn_region").toString());
                }
                if (data.containsKey("flag_region")) {
                    teamFlagRegions.put(teamName.toLowerCase(), data.get("flag_region").toString());
                }
            }
        } else {
            throw new IllegalArgumentException("Missing or invalid 'teams' section in config for map " + id);
        }

        // Flag spawn XYZs
        this.teamFlagSpawns = new HashMap<>();
        Object flagSpawnsSection = config.get("flag_spawns");
        if (flagSpawnsSection instanceof Map<?, ?> spawnMap) {
            for (Map.Entry<?, ?> entry : spawnMap.entrySet()) {
                String team = entry.getKey().toString();
                Object val = entry.getValue();
                if (val instanceof Map<?, ?> pos) {
                    try {
                        String world = pos.get("world").toString();
                        double x = Double.parseDouble(pos.get("x").toString());
                        double y = Double.parseDouble(pos.get("y").toString());
                        double z = Double.parseDouble(pos.get("z").toString());
                        teamFlagSpawns.put(team.toLowerCase(), new Vector3(world, x, y, z));
                    } catch (Exception ignored) {}
                }
            }
        }


        this.regionBounds = new HashMap<>();
        Object regionCoordsSection = config.get("region_bounds");
        if (regionCoordsSection instanceof Map<?, ?> regionMap) {
            for (Map.Entry<?, ?> entry : regionMap.entrySet()) {
                String regionName = entry.getKey().toString();
                Object coords = entry.getValue();
                if (coords instanceof Map<?, ?> data) {
                    try {
                        String world = data.get("world").toString();
                        int minX = (int) data.get("minX");
                        int minY = (int) data.get("minY");
                        int minZ = (int) data.get("minZ");
                        int maxX = (int) data.get("maxX");
                        int maxY = (int) data.get("maxY");
                        int maxZ = (int) data.get("maxZ");
                        regionBounds.put(regionName, new RegionBounds(world, minX, minY, minZ, maxX, maxY, maxZ));
                    } catch (Exception ignored) {}
                }
            }
        }

        // Legacy or YAML-style regions section with coordinate bounds
        if (config.get("regions") instanceof Map<?, ?> rawRegionMap) {
            for (Map.Entry<?, ?> entry : rawRegionMap.entrySet()) {
                String regionName = entry.getKey().toString();
                Object coords = entry.getValue();
                if (coords instanceof Map<?, ?> data) {
                    try {
                        int x1 = (int) data.get("x1");
                        int y1 = (int) data.get("y1");
                        int z1 = (int) data.get("z1");
                        int x2 = (int) data.get("x2");
                        int y2 = (int) data.get("y2");
                        int z2 = (int) data.get("z2");
                        // Assume world is "world" unless stated otherwise
                        String world = data.containsKey("world") ? data.get("world").toString() : "world";
                        regionBounds.put(regionName, new RegionBounds(world, x1, y1, z1, x2, y2, z2));
                    } catch (Exception ex) {
                        Cardinal.log("&c[MapInfo] Failed to parse region '" + regionName + "' in map " + id + ": " + ex.getMessage());
                    }
                }
            }
        }


    }


    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getAuthor() {
        return author;
    }

    public String getAddedDate() {
        return addedDate;
    }

    public String getGameMode() {
        return gameMode;
    }

    public String getSchematic() {
        return schematic;
    }

    public int getRequiredPlayers() {
        return requiredPlayers;
    }

    public List<String> getSupportedModes() {
        return supportedModes;
    }


    public Map<String, Team> getTeams() {
        return teams;
    }

    public Optional<Team> getTeam(String name) {
        return Optional.ofNullable(teams.get(name.toLowerCase()));
    }

    public Map<String, String> getTeamSpawnRegions() {
        return teamSpawnRegions;
    }

    public Map<String, String> getTeamFlagRegions() {
        return teamFlagRegions;
    }

    public Optional<String> getSpawnRegion(String teamName) {
        return Optional.ofNullable(teamSpawnRegions.get(teamName.toLowerCase()));
    }

    public Optional<String> getFlagRegion(String teamName) {
        return Optional.ofNullable(teamFlagRegions.get(teamName.toLowerCase()));
    }

    public String getStartRegion() {
        return startRegion;
    }

    public String getEndRegion() {
        return endRegion;
    }

    public Map<String, Map<String, Object>> getRegionFlags() {
        return regionFlags;
    }

    public Optional<Map<String, Object>> getFlagsForRegion(String regionName) {
        return Optional.ofNullable(regionFlags.get(regionName));
    }

    private static String getRequiredString(Map<String, Object> config, String key) {
        Object value = config.get(key);
        if (!(value instanceof String s) || s.trim().isEmpty()) {
            throw new IllegalArgumentException("Missing required field: '" + key + "'");
        }
        return s;
    }


    public Path getFolder() {
        return folder;
    }

    public Map<String, RegionBounds> getRegionBounds() {
        return regionBounds;
    }

    public Optional<RegionBounds> getRegion(String regionName) {
        return Optional.ofNullable(regionBounds.get(regionName));
    }

    public Map<String, Vector3> getFlagSpawns() {
        return teamFlagSpawns;
    }

    public Optional<Vector3> getFlagSpawn(String teamName) {
        return Optional.ofNullable(teamFlagSpawns.get(teamName.toLowerCase()));
    }

    public int getPasteX() {
        return pasteX;
    }

    public int getPasteY() {
        return pasteY;
    }

    public int getPasteZ() {
        return pasteZ;
    }



    @Override
    public String toString() {
        return name + " by " + author;
    }
}
