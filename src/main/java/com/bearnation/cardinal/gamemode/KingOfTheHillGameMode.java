package com.bearnation.cardinal.gamemode;

import com.bearnation.cardinal.map.MapInfo;
import com.bearnation.cardinal.util.Chat;
import com.velocitypowered.api.proxy.Player;

import java.util.List;

public class KingOfTheHillGameMode implements GameMode {

    @Override
    public String getId() {
        return "KOTH";
    }

    @Override
    public String getDisplayName() {
        return "King of the Hill";
    }

    @Override
    public int getTeamCount() {
        return 2;
    }

    @Override
    public void onMatchStart(MapInfo map) {
        Chat.broadcast("&a[King of the Hill] Match started on: &e" + map.getName());
        Chat.broadcast("&fObjective: &bControl the hill region to gain points!");
    }

    @Override
    public void onMatchTick(MapInfo map) {
        // Hill control logic
    }

    @Override
    public void onMatchEnd(MapInfo map) {
        Chat.broadcast("&c[King of the Hill] Match ended on: &e" + map.getName());
    }

    @Override
    public List<String> getTeamNames() {
        return List.of("Red", "Blue");
    }

    @Override
    public void onPlayerJoin(Player player) {
        // Optional logic
    }
}
