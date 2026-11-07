package com.bearnation.cardinal.gamemode;

import com.bearnation.cardinal.map.MapInfo;
import com.bearnation.cardinal.util.Chat;
import com.velocitypowered.api.proxy.Player;

import java.util.List;

public class FourTeamCTFGameMode implements GameMode {

    @Override
    public String getId() {
        return "4CTF";
    }

    @Override
    public String getDisplayName() {
        return "4-Team Capture the Flag";
    }

    @Override
    public int getTeamCount() {
        return 4;
    }

    @Override
    public void onMatchStart(MapInfo map) {
        Chat.broadcast("&a[4-Team CTF] Match started on: &e" + map.getName());
        Chat.broadcast("&fObjective: &bCapture flags from other teams and return to your base.");
        // Trigger Paper-side flag spawn, region setup, etc.
    }

    @Override
    public void onMatchTick(MapInfo map) {
        // Optional: update scoreboard, check win condition, etc.
    }

    @Override
    public void onMatchEnd(MapInfo map) {
        Chat.broadcast("&c[4-Team CTF] Match has ended on: &e" + map.getName());
    }

    @Override
    public List<String> getTeamNames() {
        return List.of("Red", "Blue", "Yellow", "Green");
    }

    @Override
    public void onPlayerJoin(Player player) {
        // Optional welcome or kit setup
    }
}
