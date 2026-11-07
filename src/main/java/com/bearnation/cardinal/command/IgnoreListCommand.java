package com.bearnation.cardinal.command;

import com.bearnation.cardinal.util.IgnoreManager;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.UUID;

public class IgnoreListCommand implements SimpleCommand {

    private final ProxyServer server;

    public IgnoreListCommand(ProxyServer server) {
        this.server = server;
    }

    @Override
    public void execute(Invocation invocation) {
        if (!(invocation.source() instanceof Player player)) return;

        var ignored = IgnoreManager.getIgnored(player.getUniqueId());
        if (ignored.isEmpty()) {
            player.sendMessage(Component.text("You are not ignoring anyone.", NamedTextColor.GRAY));
            return;
        }

        player.sendMessage(Component.text("Ignored players:", NamedTextColor.YELLOW));
        for (UUID uuid : ignored) {
            server.getPlayer(uuid).ifPresentOrElse(
                    target -> player.sendMessage(Component.text("- " + target.getUsername(), NamedTextColor.GRAY)),
                    () -> player.sendMessage(Component.text("- " + uuid.toString(), NamedTextColor.DARK_GRAY))
            );
        }
    }
}
