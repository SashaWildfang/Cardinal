package com.bearnation.cardinal.command;

import com.bearnation.cardinal.database.DatabaseManager;
import com.bearnation.cardinal.messaging.Messaging;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import java.util.*;
import java.util.stream.Collectors;

public class CoinsCommand implements SimpleCommand {

    private final ProxyServer proxy;
    private final Messaging messaging; // NATS facade

    public CoinsCommand(ProxyServer proxy, Messaging messaging) {
        this.proxy = proxy;
        this.messaging = messaging;
    }

    @Override
    public void execute(Invocation invocation) {
        String[] args = invocation.arguments();
        CommandSource source = invocation.source();
        String alias = invocation.alias().toLowerCase();

        // Admin commands
        if (alias.equals("coinsadmin")) {
            if (!source.hasPermission("cardinal.cmd.coins.admin")) {
                send(source, "&cYou do not have permission to use admin coin commands.");
                return;
            }

            if (args.length == 1 && args[0].equalsIgnoreCase("help")) {
                send(source, "&7CoinsAdmin Commands:");
                send(source, "&7/coinsadmin add <player> <amount>");
                send(source, "&7/coinsadmin remove <player> <amount>");
                send(source, "&7/coinsadmin set <player> <amount>");
                send(source, "&7/coinsadmin reset <player>");
                return;
            }

            if (args.length < 2 || args.length > 3) {
                send(source, "&7Usage: /coinsadmin [add|remove|set|reset] <player> [amount]");
                return;
            }

            String action = args[0].toLowerCase();
            String targetName = args[1];
            Optional<Player> target = proxy.getPlayer(targetName);

            if (target.isEmpty()) {
                send(source, "&cPlayer not found.", NamedTextColor.RED);
                return;
            }

            UUID uuid = target.get().getUniqueId();
            boolean success;

            switch (action) {
                case "add" -> {
                    int amt = validateAmount(args, source); if (amt == -1) return;
                    success = DatabaseManager.addCoins(uuid, amt);
                }
                case "remove" -> {
                    int amt = validateAmount(args, source); if (amt == -1) return;
                    success = DatabaseManager.removeCoins(uuid, amt);
                }
                case "set" -> {
                    int amt = validateAmount(args, source); if (amt == -1) return;
                    success = DatabaseManager.setCoins(uuid, amt);
                }
                case "reset" -> success = DatabaseManager.resetCoins(uuid);
                default -> {
                    send(source, "&7Unknown action: &e" + action, NamedTextColor.RED);
                    return;
                }
            }

            if (success) {
                int newBalance = DatabaseManager.getCoins(uuid);
                send(source, "&a" + targetName + "&7 now has &7" + formatCoins(newBalance) + "&7 coins");
                target.get().sendMessage(parse("&9&lBN: &7Your coin balance is now &6" + formatCoins(newBalance)));

                // Ask Hub to refresh this player's scoreboard via NATS
                messaging.requestStatsRefresh(uuid);
            } else {
                send(source, "&cFailed to update coins.");
            }
            return;
        }

        // /coins or /coins <player>
        if (args.length == 0) {
            if (!(source instanceof Player player)) {
                send(source, "&7Usage: /coins <player>");
                return;
            }
            int coins = DatabaseManager.getCoins(player.getUniqueId());
            send(source, "&7You currently have &6" + formatCoins(coins) + "&7 coins.");
            return;
        }

        if (!source.hasPermission("cardinal.cmd.coins.player")) {
            send(source, "&cYou do not have permission to view others' coins");
            return;
        }

        String targetName = args[0];
        Optional<Player> target = proxy.getPlayer(targetName);
        if (target.isEmpty()) {
            send(source, "&cPlayer not found.");
            return;
        }

        int coins = DatabaseManager.getCoins(target.get().getUniqueId());
        send(source, "&a" + targetName + "&7 has &6" + formatCoins(coins) + "&7 coins");
    }

    private int validateAmount(String[] args, CommandSource source) {
        if (args.length != 3) {
            send(source, "&cYou must specify an amount.");
            return -1;
        }
        try {
            return Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            send(source, "&cInvalid number");
            return -1;
        }
    }

    private Component prefix() {
        return Component.text("BN: ", NamedTextColor.BLUE).decorate(TextDecoration.BOLD)
                .append(Component.text("", NamedTextColor.GRAY));
    }

    private Component parse(String msg) {
        return prefix().append(Component.text(msg.replace("&", "§")).color(NamedTextColor.GRAY));
    }

    private void send(CommandSource source, String message) {
        source.sendMessage(parse(message));
    }

    private void send(CommandSource source, String msg, NamedTextColor color) {
        source.sendMessage(prefix().append(Component.text(msg, color)));
    }

    private String formatCoins(int coins) {
        if (coins < 1_000) return String.valueOf(coins);
        double value = coins;
        String suffix;

        if (coins >= 1_000_000_000_000_000L) { value /= 1_000_000_000_000_000L; suffix = "Q"; }
        else if (coins >= 1_000_000_000_000L) { value /= 1_000_000_000_000L; suffix = "T"; }
        else if (coins >= 1_000_000_000) { value /= 1_000_000_000; suffix = "B"; }
        else if (coins >= 1_000_000) { value /= 1_000_000; suffix = "M"; }
        else { value /= 1_000; suffix = "k"; }

        return String.format(value < 10 ? "%.1f%s" : "%.0f%s", value, suffix);
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission("cardinal.cmd.coins");
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        String alias = invocation.alias().toLowerCase();
        String[] args = invocation.arguments();
        List<String> suggestions = new ArrayList<>();

        if ((alias.equalsIgnoreCase("coins") || alias.equalsIgnoreCase("balance")) && args.length == 1) {
            suggestions.addAll(proxy.getAllPlayers().stream()
                    .map(Player::getUsername)
                    .collect(Collectors.toList()));
        }

        if (alias.equals("coinsadmin")) {
            if (args.length == 1) {
                suggestions.addAll(List.of("add", "remove", "set", "reset", "help"));
            } else if (args.length == 2 && List.of("add", "remove", "set", "reset").contains(args[0].toLowerCase())) {
                suggestions.addAll(proxy.getAllPlayers().stream()
                        .map(Player::getUsername)
                        .collect(Collectors.toList()));
            }
        }

        return suggestions;
    }
}
