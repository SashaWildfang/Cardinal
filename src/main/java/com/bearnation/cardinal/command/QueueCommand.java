package com.bearnation.cardinal.command;

import com.bearnation.cardinal.Cardinal;
import com.bearnation.cardinal.match.MatchManager;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class QueueCommand implements SimpleCommand {

    private final MatchManager matchManager;

    public QueueCommand(MatchManager matchManager) {
        this.matchManager = matchManager;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        String[] args = invocation.arguments();

        if (args.length == 0) {
            source.sendMessage(Component.text("Usage: /queue <list|add|remove|clear|gui>"));
            return;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "list" -> {
                if (!source.hasPermission("cardinal.queue.list")) {
                    source.sendMessage(Component.text("You don’t have permission."));
                    return;
                }
                if (matchManager.getPlayersInLobby().isEmpty()) {
                    source.sendMessage(Component.text("Queue is empty."));
                    return;
                }
                source.sendMessage(Component.text("Players in queue:"));
                matchManager.getPlayersInLobby().forEach(p ->
                        source.sendMessage(Component.text(" - " + p.getUsername())));
            }

            case "add" -> {
                if (!source.hasPermission("cardinal.queue.add")) {
                    source.sendMessage(Component.text("You don’t have permission."));
                    return;
                }
                if (args.length < 2) {
                    source.sendMessage(Component.text("Usage: /queue add <player>"));
                    return;
                }

                Optional<Player> target = Cardinal.getInstance().getServer().getPlayer(args[1]);
                if (target.isEmpty()) {
                    source.sendMessage(Component.text("Player not found."));
                    return;
                }

                if (matchManager.getPlayersInLobby().contains(target.get())) {
                    source.sendMessage(Component.text("§cThat player is already in the queue."));
                    return;
                }

                matchManager.playerJoinLobby(target.get());
                source.sendMessage(Component.text("§aAdded " + target.get().getUsername() + " to the queue."));
            }

            case "remove" -> {
                if (!source.hasPermission("cardinal.queue.remove")) {
                    source.sendMessage(Component.text("You don’t have permission."));
                    return;
                }
                if (args.length < 2) {
                    source.sendMessage(Component.text("Usage: /queue remove <player>"));
                    return;
                }

                Optional<Player> target = Cardinal.getInstance().getServer().getPlayer(args[1]);
                if (target.isEmpty()) {
                    source.sendMessage(Component.text("Player not found."));
                    return;
                }

                if (!matchManager.getPlayersInLobby().contains(target.get())) {
                    source.sendMessage(Component.text("§cThat player is not in the queue."));
                    return;
                }

                matchManager.playerLeaveLobby(target.get());
                source.sendMessage(Component.text("§aRemoved " + target.get().getUsername() + " from the queue."));
            }

            case "clear" -> {
                if (!source.hasPermission("cardinal.queue.clear")) {
                    source.sendMessage(Component.text("You don’t have permission."));
                    return;
                }

                new ArrayList<>(matchManager.getPlayersInLobby()).forEach(matchManager::playerLeaveLobby);
                source.sendMessage(Component.text("Queue cleared."));
            }

            case "gui" -> {
                if (!(source instanceof Player player)) {
                    source.sendMessage(Component.text("Only players can open the GUI."));
                    return;
                }

                Cardinal.getInstance().getServer().getCommandManager().executeAsync(player, "queuegui");
            }

            default -> source.sendMessage(Component.text("Unknown subcommand. Use /queue <list|add|remove|clear|gui>"));
        }
    }


    @Override
    public List<String> suggest(Invocation invocation) {
        String[] args = invocation.arguments();

        if (args.length == 0) {
            return List.of("list", "add", "remove", "clear", "gui");
        }

        if (args.length == 1) {
            return List.of("list", "add", "remove", "clear", "gui").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 2) {
            String sub = args[0].toLowerCase();

            return switch (sub) {
                case "add" -> Cardinal.getInstance().getServer().getAllPlayers().stream()
                        .map(Player::getUsername)
                        .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                        .filter(name -> matchManager.getPlayersInLobby().stream().noneMatch(p -> p.getUsername().equalsIgnoreCase(name)))
                        .collect(Collectors.toList());

                case "remove" -> matchManager.getPlayersInLobby().stream()
                        .map(Player::getUsername)
                        .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());

                default -> List.of();
            };
        }

        return List.of();
    }

}
