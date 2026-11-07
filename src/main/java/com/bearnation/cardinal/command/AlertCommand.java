package com.bearnation.cardinal.command;

import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import com.bearnation.cardinal.Cardinal;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class AlertCommand implements SimpleCommand {

    private final Cardinal plugin;

    public AlertCommand(Cardinal plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(Invocation invocation) {
        if (!invocation.source().hasPermission("cardinal.cmd.alert")) {
            invocation.source().sendMessage(Component.text("You do not have permission to use this command.", NamedTextColor.RED));
            return;
        }

        String[] args = invocation.arguments();
        if (args.length == 0) {
            invocation.source().sendMessage(Component.text("Usage: /alert <message>", NamedTextColor.YELLOW));
            return;
        }

        String message = String.join(" ", args);
        Component alertMessage = Component.text("[ALERT] ", NamedTextColor.RED)
                .append(Component.text(message, NamedTextColor.WHITE));

        for (Player player : plugin.getServer().getAllPlayers()) {
            player.sendMessage(alertMessage);
        }

        // Also show in console
        Cardinal.log("&c[ALERT] &f" + message);
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return true; // We do permission check manually above
    }
}
