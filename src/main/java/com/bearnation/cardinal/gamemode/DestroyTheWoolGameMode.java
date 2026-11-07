package com.bearnation.cardinal.gamemode;

import com.bearnation.cardinal.map.MapInfo;
import com.bearnation.cardinal.util.Chat;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;

import java.util.List;

public class DestroyTheWoolGameMode implements GameMode {

    private final ProxyServer proxy;

    public DestroyTheWoolGameMode(ProxyServer proxy) {
        this.proxy = proxy;
    }

    @Override
    public String getId() {
        return "dtw";
    }

    @Override
    public String getDisplayName() {
        return "Destroy the Wool";
    }

    @Override
    public int getTeamCount() {
        return 2;
    }

    @Override
    public void onMatchStart(MapInfo map) {
        Chat.broadcast("&a[Cardinal]&7 Match started on map: &e" + map.getName());
        Chat.broadcast("&fObjective: &7Destroy the opposing Team's Wool");

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
        Chat.broadcast("&a[Cardinal]&7 Match ended on map: &e" + map.getName());
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
