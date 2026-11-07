package com.bearnation.cardinal.command;

import com.bearnation.cardinal.Cardinal;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class AutoQueueCommand implements SimpleCommand {

    private final Path filePath = Path.of("plugins/cardinal/autoqueue.txt");

    public AutoQueueCommand() {
        try {
            File file = filePath.toFile();
            if (!file.exists()) {
                file.getParentFile().mkdirs();
                file.createNewFile();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void execute(Invocation invocation) {
        if (!(invocation.source() instanceof Player player)) {
            invocation.source().sendMessage(Component.text("Only players can use this command."));
            return;
        }

        String[] args = invocation.arguments();
        UUID uuid = player.getUniqueId();

        if (args.length == 0) {
            boolean enabled = isAutoQueueEnabled(uuid);
            player.sendMessage(Component.text("§a[Cardinal]§f AutoQueue is currently " + (enabled ? "§aENABLED" : "§cDISABLED") + "."));
            player.sendMessage(Component.text("§fUse §a/autoqueue on §for §c/autoqueue off §fto change"));
            return;
        }

        if (!player.hasPermission("cardinal.autoqueue.toggle")) {
            player.sendMessage(Component.text("§cYou don't have permission to toggle AutoQueue."));
            return;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "on" -> {
                enableAutoQueue(uuid);
                player.sendMessage(Component.text("§aAutoQueue has been ENABLED."));
            }
            case "off" -> {
                disableAutoQueue(uuid);
                player.sendMessage(Component.text("§cAutoQueue has been DISABLED."));
            }
            default -> player.sendMessage(Component.text("§cUsage: /autoqueue <on|off>"));
        }
    }

    public boolean isAutoQueueEnabled(UUID uuid) {
        try {
            List<String> lines = Files.readAllLines(filePath);
            // Default to enabled if not explicitly disabled
            return !lines.contains(uuid.toString());
        } catch (IOException e) {
            e.printStackTrace();
            return true; // Assume enabled if there's an error
        }
    }


    private void enableAutoQueue(UUID uuid) {
        try {
            List<String> lines = Files.readAllLines(filePath);
            List<String> updated = lines.stream()
                    .filter(line -> !line.equals(uuid.toString()))
                    .collect(Collectors.toList());
            Files.write(filePath, updated);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    private void disableAutoQueue(UUID uuid) {
        try {
            List<String> lines = Files.readAllLines(filePath);
            if (!lines.contains(uuid.toString())) {
                lines.add(uuid.toString());
                Files.write(filePath, lines);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    @Override
    public List<String> suggest(Invocation invocation) {
        String[] args = invocation.arguments();
        if (args.length == 1) {
            return List.of("on", "off").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return List.of();
    }
}
