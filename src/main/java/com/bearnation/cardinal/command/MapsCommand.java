package com.bearnation.cardinal.command;

import com.bearnation.cardinal.map.MapInfo;
import com.bearnation.cardinal.map.MapManager;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.*;

public class MapsCommand implements SimpleCommand {

    private static final int MAPS_PER_PAGE = 10;

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        List<MapInfo> allMaps = MapManager.getInstance().getMaps();

        if (allMaps.isEmpty()) {
            source.sendMessage(Component.text("No maps are currently loaded.", NamedTextColor.RED));
            return;
        }

        String[] args = invocation.arguments();
        String gamemodeFilter;
        int page = 1;

        if (args.length == 1) {
            if (isInteger(args[0])) {
                gamemodeFilter = null;
                page = Integer.parseInt(args[0]);
            } else {
                gamemodeFilter = args[0];
            }
        } else if (args.length >= 2) {
            gamemodeFilter = args[0];
            if (isInteger(args[1])) {
                page = Integer.parseInt(args[1]);
            }
        } else {
            gamemodeFilter = null;
        }

        List<MapInfo> filteredMaps = (gamemodeFilter != null)
                ? allMaps.stream().filter(map -> map.getGameMode().equalsIgnoreCase(gamemodeFilter)).toList()
                : allMaps;

        if (filteredMaps.isEmpty()) {
            source.sendMessage(Component.text("No maps found for " + (gamemodeFilter != null ? gamemodeFilter : "that filter") + ".", NamedTextColor.RED));
            return;
        }

        int totalPages = (int) Math.ceil((double) filteredMaps.size() / MAPS_PER_PAGE);
        if (page < 1) page = 1;
        if (page > totalPages) page = totalPages;

        int start = (page - 1) * MAPS_PER_PAGE;
        int end = Math.min(start + MAPS_PER_PAGE, filteredMaps.size());
        List<MapInfo> pagedMaps = filteredMaps.subList(start, end);

        Component header = Component.text(
                "Loaded Maps (" + filteredMaps.size() + ")"
                        + (gamemodeFilter != null ? " - " + gamemodeFilter.toUpperCase() : "")
                        + " - Page " + page + "/" + totalPages,
                NamedTextColor.GOLD
        );
        source.sendMessage(header);

        for (MapInfo map : pagedMaps) {
            source.sendMessage(Component.text(" • ", NamedTextColor.GRAY)
                    .append(Component.text(map.getName(), NamedTextColor.GREEN))
                    .append(Component.text(" (", NamedTextColor.DARK_GRAY))
                    .append(Component.text(map.getGameMode(), NamedTextColor.YELLOW))
                    .append(Component.text(")", NamedTextColor.DARK_GRAY)));
        }

        // Navigation hints
        if (page < totalPages) {
            source.sendMessage(Component.text("Type ", NamedTextColor.GRAY)
                    .append(Component.text("/maps " + (gamemodeFilter != null ? gamemodeFilter + " " : "") + (page + 1), NamedTextColor.AQUA))
                    .append(Component.text(" to see the next page.")));
        }

        source.sendMessage(Component.text("Tip: Use ", NamedTextColor.GRAY)
                .append(Component.text("/mapinfo <name>", NamedTextColor.AQUA))
                .append(Component.text(" to view map details.")));
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return true; // Replace with permission check if needed
    }

    private boolean isInteger(String s) {
        try {
            Integer.parseInt(s);
            return true;
        } catch (NumberFormatException ex) {
            return false;
        }
    }
}
