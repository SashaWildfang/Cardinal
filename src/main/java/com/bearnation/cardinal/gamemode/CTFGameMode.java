package com.bearnation.cardinal.gamemode;

import com.bearnation.cardinal.map.MapInfo;
import com.bearnation.cardinal.util.Chat;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;

import java.util.List;

public class CTFGameMode implements GameMode {

    private final ProxyServer proxy;

    public CTFGameMode(ProxyServer proxy) {
        this.proxy = proxy;
    }

    @Override
    public String getId() {
        return "CTF";
    }

    @Override
    public String getDisplayName() {
        return "Capture the Flag";
    }

    @Override
    public int getTeamCount() {
        return 2;
    }

    @Override
    public void onMatchStart(MapInfo map) {
        Chat.broadcast("&a[CTF] Match started on map: &e" + map.getName());
        Chat.broadcast("&fObjective: &bCapture the enemy's flag and return it to your base!");

        // Send players currently in the hub to ctf1
        proxy.getAllPlayers().stream()
                .filter(p -> p.getCurrentServer().isPresent() &&
                        p.getCurrentServer().get().getServerInfo().getName().equalsIgnoreCase("hub"))
                .forEach(player ->
                        proxy.getServer("CTF").ifPresent(server ->
                                player.createConnectionRequest(server).connect()
                        )
                );
    }

    @Override
    public void onMatchTick(MapInfo map) {
        // Optional periodic logic: scoring, flag checks, etc.
    }

    @Override
    public void onMatchEnd(MapInfo map) {
        Chat.broadcast("&c[CTF] Match ended on map: &e" + map.getName());
    }

    @Override
    public List<String> getTeamNames() {
        return List.of("Red", "Blue");
    }

    @Override
    public void onPlayerJoin(Player player) {
        // Optional join logic (e.g., send tips, assign class)
    }
}
