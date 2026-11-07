package com.bearnation.cardinal.command;

import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class WhereAmICommand implements SimpleCommand {

    @Override
    public void execute(Invocation invocation) {
        if (invocation.source() instanceof Player player) {
            String currentServer = player.getCurrentServer().map(s -> s.getServerInfo().getName()).orElse("Unknown");
            player.sendMessage(Component.text("You are currently on: " + currentServer, NamedTextColor.AQUA));
        } else {
            invocation.source().sendMessage(Component.text("This command can only be used by players.", NamedTextColor.RED));
        }
    }
}
