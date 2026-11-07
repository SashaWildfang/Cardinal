package com.bearnation.cardinal.command;

import com.bearnation.cardinal.Cardinal;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import net.luckperms.api.query.QueryOptions;
import net.luckperms.api.cacheddata.CachedMetaData;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class StaffCommand implements SimpleCommand {

    private final Cardinal plugin;
    private final LuckPerms luckPerms;

    public StaffCommand(Cardinal plugin, LuckPerms luckPerms) {
        this.plugin = plugin;
        this.luckPerms = luckPerms;
    }

    @Override
    public void execute(Invocation invocation) {
        List<Player> staffOnline = plugin.getProxy().getAllPlayers().stream()
                .filter(p -> p.hasPermission("cardinal.staff"))
                .sorted(Comparator.comparingInt(this::getRankWeight).reversed())
                .collect(Collectors.toList());

        if (staffOnline.isEmpty()) {
            invocation.source().sendMessage(Component.text("No staff are currently online.")
                    .color(NamedTextColor.RED));
            return;
        }

        int count = staffOnline.size();
        invocation.source().sendMessage(
                Component.text("There " + (count == 1 ? "is" : "are") + " " + count + " staff online:")
                        .color(NamedTextColor.GRAY)
        );

        for (Player p : staffOnline) {
            String name = p.getUsername();
            String server = p.getCurrentServer()
                    .map(s -> capitalize(s.getServerInfo().getName()))
                    .orElse("Unknown");

            // LuckPerms prefix (color codes translated)
            Optional<User> user = Optional.ofNullable(luckPerms.getUserManager().getUser(p.getUniqueId()));
            String prefix = user.map(u -> {
                CachedMetaData meta = u.getCachedData().getMetaData(QueryOptions.defaultContextualOptions());
                String raw = meta.getPrefix();
                return raw != null ? translateColorCodes(raw) : "";
            }).orElse("");

            // Clickable + hoverable name with prefix
            TextComponent clickableName = Component.text(prefix + name)
                    .color(NamedTextColor.YELLOW)
                    .clickEvent(ClickEvent.suggestCommand("/msg " + name + " "))
                    .hoverEvent(HoverEvent.showText(Component.text("Click to message " + name)));

            Component line = Component.text()
                    .append(Component.text(" • ").color(NamedTextColor.GRAY))
                    .append(clickableName)
                    .append(Component.text(" - ").color(NamedTextColor.DARK_GRAY))
                    .append(Component.text(server).color(NamedTextColor.AQUA))
                    .build();

            invocation.source().sendMessage(line);
        }
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission("cardinal.cmd.staff");
    }

    private int getRankWeight(Player player) {
        User user = luckPerms.getUserManager().getUser(player.getUniqueId());
        if (user == null) return 0;

        String groupName = user.getPrimaryGroup();
        Group group = luckPerms.getGroupManager().getGroup(groupName);
        if (group == null) return 0;

        return group.getWeight().orElse(0);
    }

    private String capitalize(String input) {
        if (input == null || input.isEmpty()) return input;
        return input.substring(0, 1).toUpperCase() + input.substring(1).toLowerCase();
    }

    private String translateColorCodes(String input) {
        return input.replace("&", "§");
    }
}
