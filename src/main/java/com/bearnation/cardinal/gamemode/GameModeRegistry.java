package com.bearnation.cardinal.gamemode;

import com.velocitypowered.api.proxy.ProxyServer;

import java.util.HashMap;
import java.util.Map;

public class GameModeRegistry {

    private static final Map<String, GameMode> gameModes = new HashMap<>();

    public static void registerDefaults(ProxyServer proxy) {
        register(new CTFGameMode(proxy));
        register(new FourTeamCTFGameMode());
        register(new KingOfTheHillGameMode());
        register(new DestroyTheWoolGameMode(proxy));
        // Add other modes as needed
    }

    public static void register(GameMode gameMode) {
        gameModes.put(gameMode.getId().toLowerCase(), gameMode);
    }

    public static GameMode getById(String id) {
        return gameModes.get(id.toLowerCase());
    }

    public static Map<String, GameMode> getAllGameModes() {
        return gameModes;
    }
}
