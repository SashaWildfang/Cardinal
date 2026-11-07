package com.bearnation.cardinal.map;

import com.bearnation.cardinal.Cardinal;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.util.*;

public class MapManager {

    private static final MapManager instance = new MapManager();
    private final List<MapInfo> maps = new ArrayList<>();
    private static final Set<String> VALID_GAMEMODES = Set.of(
            "ctf", "dtm", "dtc", "dtw", "ctw", "kotf", "koth", "infection", "tdm", "blitz", "ffa"
    );

    private MapManager() {}

    public static MapManager getInstance() {
        return instance;
    }

    public void loadMaps(Path baseDirectory) {
        maps.clear();

        try {
            if (!Files.exists(baseDirectory)) {
                Files.createDirectories(baseDirectory);
                Cardinal.log("&eCreated missing maps directory.");
            }

            Files.walk(baseDirectory)
                    .filter(path -> path.getFileName().toString().equalsIgnoreCase("config.yml"))
                    .forEach(config -> {
                        Path mapFolder = config.getParent();
                        if (Files.isDirectory(mapFolder)) {
                            loadMap(mapFolder, baseDirectory);
                        }
                    });

        } catch (IOException e) {
            Cardinal.log("&cFailed to load maps.");
            e.printStackTrace();
        }
    }

    public boolean loadMap(Path mapFolder, Path baseDirectory) {
        Path configFile = mapFolder.resolve("config.yml");
        if (!Files.exists(configFile)) {
            Cardinal.log("&cMissing config.yml in: " + mapFolder.getFileName());
            return false;
        }

        try (InputStream input = Files.newInputStream(configFile)) {
            LoaderOptions options = new LoaderOptions();
            Yaml yaml = new Yaml(new SafeConstructor(options));
            Map<String, Object> config = yaml.load(input);

            String id = mapFolder.getFileName().toString();
            String gamemode = (String) config.get("gamemode");

            // Attempt to determine gamemode from directory structure
            Path relative = baseDirectory.relativize(mapFolder);
            if ((gamemode == null || gamemode.isBlank()) && relative.getNameCount() > 1) {
                String possibleGamemode = relative.getName(0).toString().toLowerCase();
                if (VALID_GAMEMODES.contains(possibleGamemode)) {
                    config.put("gamemode", possibleGamemode);
                    Cardinal.log("&7[Maps] Inferred gamemode '" + possibleGamemode + "' for map '" + id + "'");
                } else {
                    Cardinal.log("&c[Maps] Could not determine valid gamemode for map '" + id + "' in folder: " + possibleGamemode);
                }
            }

            MapInfo map = new MapInfo(id, mapFolder, config);
            maps.add(map);

            if (!map.getRegionBounds().isEmpty()) {
                Cardinal.log("&7[Maps] Loaded " + map.getRegionBounds().size() + " region bounds for map '" + map.getName() + "'");
            }
            if (!map.getFlagSpawns().isEmpty()) {
                Cardinal.log("&7[Maps] Loaded flag spawns for teams: " + map.getFlagSpawns().keySet());
            }

            Cardinal.log("&aLoaded map: " + map.getName() + " (ID: " + id + ")");
            return true;

        } catch (Exception e) {
            Cardinal.log("&cError reading map folder: " + mapFolder.getFileName());
            e.printStackTrace();
            return false;
        }
    }

    public List<MapInfo> getMaps() {
        return maps;
    }

    public MapInfo getRandomMap() {
        if (maps.isEmpty()) return null;
        return maps.get(new Random().nextInt(maps.size()));
    }

    public MapInfo getMapByName(String name) {
        return maps.stream()
                .filter(m -> m.getName().equalsIgnoreCase(name))
                .findFirst()
                .orElse(null);
    }

    public List<MapInfo> pickRandomMaps(int amount) {
        List<MapInfo> shuffled = new ArrayList<>(maps);
        Collections.shuffle(shuffled);
        return shuffled.stream().limit(amount).toList();
    }

    public List<MapInfo> getMapsByGameMode(String mode) {
        return maps.stream()
                .filter(m -> m.getGameMode().equalsIgnoreCase(mode))
                .toList();
    }

    public List<MapInfo> getMapsSupportingMode(String mode) {
        return maps.stream()
                .filter(m -> m.getSupportedModes().contains(mode))
                .toList();
    }

    public Optional<MapInfo> searchByPartialName(String query) {
        return maps.stream()
                .filter(m -> m.getName().toLowerCase().contains(query.toLowerCase()))
                .findFirst();
    }

    public boolean reloadMap(Path folder) {
        String id = folder.getFileName().toString();
        maps.removeIf(m -> m.getId().equalsIgnoreCase(id));
        return loadMap(folder, Cardinal.getInstance().getDataDirectory().resolve("maps"));
    }

    public boolean hasMap(String name) {
        return maps.stream().anyMatch(m -> m.getName().equalsIgnoreCase(name));
    }

    public boolean isEmpty() {
        return maps.isEmpty();
    }

    public int getMapCount() {
        return maps.size();
    }

    public void reloadAllMaps() {
        Path mapDir = Cardinal.getInstance().getDataDirectory().resolve("maps");
        loadMaps(mapDir);
    }
}
