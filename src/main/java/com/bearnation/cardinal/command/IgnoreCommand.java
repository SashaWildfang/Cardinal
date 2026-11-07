package com.bearnation.cardinal.command;

import com.bearnation.cardinal.util.IgnoreManager;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.Optional;
public class IgnoreCommand implements SimpleCommand {

    private final ProxyServer proxy;

    public IgnoreCommand(ProxyServer proxy) {
        this.proxy = proxy;
    }

    @Override
    public void execute(Invocation invocation) {
        if (!(invocation.source() instanceof Player player)) return;

        if (invocation.arguments().length != 1) {
            player.sendMessage(Component.text("Usage: /ignore <player>", NamedTextColor.RED));
            return;
        }

        Optional<Player> targetOpt = proxy.getPlayer(invocation.arguments()[0]);
        if (targetOpt.isEmpty()) {
            player.sendMessage(Component.text("Player not found.", NamedTextColor.RED));
            return;
        }

        Player target = targetOpt.get();

        if (target.getUniqueId().equals(player.getUniqueId())) {
            player.sendMessage(Component.text("You cannot ignore yourself.", NamedTextColor.RED));
            return;
        }

        IgnoreManager.ignore(player.getUniqueId(), target.getUniqueId());
        player.sendMessage(Component.text("You are now ignoring " + target.getUsername(), NamedTextColor.YELLOW));
    }
}
