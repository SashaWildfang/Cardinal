package com.bearnation.cardinal.teams;

import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.HashSet;
import java.util.Set;

public class Team {

    private final String name;
    private final String displayName;
    private final int colorCode;
    private final Set<Player> players = new HashSet<>();
    private final String hexColor;

    public Team(String name, String displayName, int colorCode, String hexColor) {
        this.name = name;
        this.displayName = displayName;
        this.colorCode = colorCode;
        this.hexColor = hexColor;
    }

    public String getName() {
        return name;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getColorCode() {
        return colorCode;
    }

    public void addPlayer(Player player) {
        players.add(player);
    }

    public void removePlayer(Player player) {
        players.remove(player);
    }

    public Set<Player> getPlayers() {
        return players;
    }

    public int getPlayerCount() {
        return players.size();
    }

    public TextColor getColor() {
        return switch (name.toLowerCase()) {
            case "red" -> NamedTextColor.RED;
            case "blue" -> NamedTextColor.BLUE;
            case "yellow" -> NamedTextColor.YELLOW;
            case "green" -> NamedTextColor.GREEN;
            default -> NamedTextColor.GRAY;
        };
    }

    public String getHexColor() {
        return hexColor;
    }

    public TextColor getHexTextColor() {
        try {
            return TextColor.fromHexString(hexColor);
        } catch (IllegalArgumentException e) {
            return NamedTextColor.GRAY;
        }
    }
}
