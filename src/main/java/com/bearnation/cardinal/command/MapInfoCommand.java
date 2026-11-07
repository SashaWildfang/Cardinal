package com.bearnation.cardinal.command;

import com.bearnation.cardinal.map.MapInfo;
import com.bearnation.cardinal.map.MapManager;
import com.bearnation.cardinal.map.RegionBounds;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.*;
import java.util.stream.Collectors;

public class MapInfoCommand implements SimpleCommand {

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        String[] args = invocation.arguments();

        if (args.length == 0) {
            source.sendMessage(Component.text("Usage: /mapinfo <map name>", NamedTextColor.RED));
            return;
        }

        String query = String.join(" ", args);
        MapInfo map = MapManager.getInstance().searchByPartialName(query).orElse(null);

        if (map == null) {
            source.sendMessage(Component.text("Map not found: " + query, NamedTextColor.RED));
            return;
        }

        boolean isAdmin = source.hasPermission("cardinal.mapinfo.admin");

        // Basic Info
        source.sendMessage(Component.text("Map Info:", NamedTextColor.GOLD));
        source.sendMessage(Component.text("• Name: ", NamedTextColor.YELLOW)
                .append(Component.text(map.getName(), NamedTextColor.GREEN)));
        source.sendMessage(Component.text("• Author: ", NamedTextColor.YELLOW)
                .append(Component.text(map.getAuthor(), NamedTextColor.AQUA)));
        source.sendMessage(Component.text("• Gamemode: ", NamedTextColor.YELLOW)
                .append(Component.text(map.getGameMode(), NamedTextColor.LIGHT_PURPLE)));
        source.sendMessage(Component.text("• Required Players: ", NamedTextColor.YELLOW)
                .append(Component.text(String.valueOf(map.getRequiredPlayers()), NamedTextColor.GOLD)));
        source.sendMessage(Component.text("• Added Date: ", NamedTextColor.YELLOW)
                .append(Component.text(map.getAddedDate(), NamedTextColor.GRAY)));

        if (isAdmin) {
            source.sendMessage(Component.empty());
            source.sendMessage(Component.text("Admin Details:", NamedTextColor.RED));

            source.sendMessage(Component.text("• Schematic: ", NamedTextColor.YELLOW)
                    .append(Component.text(map.getSchematic(), NamedTextColor.WHITE)));

            if (!map.getRegionBounds().isEmpty()) {
                Set<String> regionNames = map.getRegionBounds().keySet();
                source.sendMessage(Component.text("• Regions: ", NamedTextColor.YELLOW)
                        .append(Component.text(String.join(", ", regionNames), NamedTextColor.GRAY)));
            }


            if (!map.getRegionBounds().isEmpty()) {
                source.sendMessage(Component.text("• Region Bounds:", NamedTextColor.YELLOW));
                for (Map.Entry<String, RegionBounds> entry : map.getRegionBounds().entrySet()) {
                    RegionBounds bounds = entry.getValue();
                    source.sendMessage(Component.text("   - " + entry.getKey() + ": ", NamedTextColor.GRAY)
                            .append(Component.text(bounds.toString(), NamedTextColor.DARK_GRAY)));
                }
            }

            if (!map.getRegionFlags().isEmpty()) {
                source.sendMessage(Component.text("• Region Flags:", NamedTextColor.YELLOW));
                for (Map.Entry<String, Map<String, Object>> entry : map.getRegionFlags().entrySet()) {
                    source.sendMessage(Component.text("   - " + entry.getKey() + ": " + entry.getValue(), NamedTextColor.GRAY));
                }
            }

            if (!map.getTeamSpawnRegions().isEmpty()) {
                source.sendMessage(Component.text("• Team Spawn Regions:", NamedTextColor.YELLOW));
                map.getTeamSpawnRegions().forEach((team, region) ->
                        source.sendMessage(Component.text("   - " + team + ": " + region, NamedTextColor.GRAY)));
            }

            if (!map.getTeamFlagRegions().isEmpty()) {
                source.sendMessage(Component.text("• Team Flag Regions:", NamedTextColor.YELLOW));
                map.getTeamFlagRegions().forEach((team, region) ->
                        source.sendMessage(Component.text("   - " + team + ": " + region, NamedTextColor.GRAY)));
            }

            if (!map.getFlagSpawns().isEmpty()) {
                source.sendMessage(Component.text("• Flag Spawn Locations:", NamedTextColor.YELLOW));
                map.getFlagSpawns().forEach((team, vector) ->
                        source.sendMessage(Component.text("   - " + team + ": ", NamedTextColor.GRAY)
                                .append(Component.text(vector.toString(), NamedTextColor.DARK_GRAY))));
            }

            if (map.getStartRegion() != null) {
                source.sendMessage(Component.text("• Start Region: ", NamedTextColor.YELLOW)
                        .append(Component.text(map.getStartRegion(), NamedTextColor.GRAY)));
            }

            if (map.getEndRegion() != null) {
                source.sendMessage(Component.text("• End Region: ", NamedTextColor.YELLOW)
                        .append(Component.text(map.getEndRegion(), NamedTextColor.GRAY)));
            }
        }
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission("cardinal.mapinfo");
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        String[] args = invocation.arguments();
        String input = String.join(" ", args).toLowerCase();

        return MapManager.getInstance().getMaps().stream()
                .map(MapInfo::getName)
                .filter(name -> name.toLowerCase().startsWith(input))
                .sorted()
                .collect(Collectors.toList());
    }
}
