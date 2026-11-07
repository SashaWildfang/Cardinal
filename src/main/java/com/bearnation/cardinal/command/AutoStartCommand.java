package com.bearnation.cardinal.command;

import com.bearnation.cardinal.Cardinal;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class AutoStartCommand implements SimpleCommand {

    private final Cardinal plugin;

    public AutoStartCommand(Cardinal plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        String[] args = invocation.arguments();

        if (!source.hasPermission("cardinal.cmd.autostart")) {
            source.sendMessage(Component.text("You don’t have permission to use this command.", NamedTextColor.RED));
            return;
        }

        if (args.length != 1 ||
                !(args[0].equalsIgnoreCase("on") ||
                        args[0].equalsIgnoreCase("off") ||
                        args[0].equalsIgnoreCase("status"))) {
            source.sendMessage(Component.text("Usage: /autostart <on|off|status>", NamedTextColor.YELLOW));
            return;
        }

        switch (args[0].toLowerCase()) {
            case "on" -> {
                if (plugin.getAutoStarter().isAutoStartEnabled()) {
                    source.sendMessage(Component.text("Auto-start is already enabled.", NamedTextColor.YELLOW));
                } else {
                    plugin.getAutoStarter().setAutoStartEnabled(true); // starts monitoring inside
                    plugin.getMessaging().sendAutoStartStatusToHub(true);
                    source.sendMessage(Component.text("Auto-start has been enabled.", NamedTextColor.GREEN));
                }
            }
            case "off" -> {
                plugin.getAutoStarter().setAutoStartEnabled(false); // stops monitoring inside
                plugin.getMessaging().sendAutoStartStatusToHub(false);
                source.sendMessage(Component.text("Auto-start has been disabled.", NamedTextColor.RED));
            }
            case "status" -> {
                boolean status = plugin.getAutoStarter().isAutoStartEnabled();
                NamedTextColor color = status ? NamedTextColor.GREEN : NamedTextColor.RED;
                source.sendMessage(Component.text(
                        "Auto-start is currently " + (status ? "ENABLED" : "DISABLED") + ".", color));
            }
        }
    }
}
