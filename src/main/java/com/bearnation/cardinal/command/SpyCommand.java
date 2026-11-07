package com.bearnation.cardinal.command;

import com.bearnation.cardinal.util.SpyManager;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class SpyCommand implements SimpleCommand {

    @Override
    public void execute(Invocation invocation) {
        if (!(invocation.source() instanceof Player player)) return;

        if (!player.hasPermission("cardinal.cmd.spy")) {
            player.sendMessage(Component.text("You don't have permission to use /spy.", NamedTextColor.RED));
            return;
        }

        boolean nowSpying = !SpyManager.isSpy(player.getUniqueId());

        if (nowSpying) {
            SpyManager.enableSpy(player.getUniqueId());
            player.sendMessage(Component.text("Spy mode enabled.", NamedTextColor.GREEN));
        } else {
            SpyManager.disableSpy(player.getUniqueId());
            player.sendMessage(Component.text("Spy mode disabled.", NamedTextColor.RED));
        }
    }
}
