package com.bearnation.cardinal.command;

import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.bearnation.cardinal.Cardinal;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class HubCommand implements SimpleCommand {

    private final Cardinal plugin;

    public HubCommand(Cardinal plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(Invocation invocation) {
        if (!(invocation.source() instanceof Player)) {
            invocation.source().sendMessage(Component.text("Only players can use this command.", NamedTextColor.RED));
            return;
        }

        Player player = (Player) invocation.source();
        RegisteredServer hub = plugin.getServer().getServer("hub").orElse(null);

        if (hub == null) {
            player.sendMessage(Component.text("Hub server not found!", NamedTextColor.RED));
            return;
        }

        player.createConnectionRequest(hub).fireAndForget();
        player.sendMessage(Component.text("Sending you to the Hub...", NamedTextColor.GREEN));
    }
}
