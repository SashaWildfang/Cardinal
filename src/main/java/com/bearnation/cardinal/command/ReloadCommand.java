package com.bearnation.cardinal.command;

import com.bearnation.cardinal.Cardinal;
import com.bearnation.cardinal.map.MapManager;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class ReloadCommand implements SimpleCommand {

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();

        if (!source.hasPermission("cardinal.cmd.reload")) {
            source.sendMessage(Component.text("You do not have permission to reload Cardinal.", NamedTextColor.RED));
            return;
        }

        try {
            MapManager mapManager = Cardinal.getInstance().getMapManager();

            source.sendMessage(Component.text("Reloading maps...", NamedTextColor.YELLOW));
            mapManager.reloadAllMaps();
            source.sendMessage(Component.text("✔ Maps reloaded successfully!", NamedTextColor.GREEN));

        } catch (Exception e) {
            source.sendMessage(Component.text("❌ Failed to reload maps: " + e.getMessage(), NamedTextColor.RED));
            e.printStackTrace();
        }
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission("cardinal.cmd.reload");
    }
}
