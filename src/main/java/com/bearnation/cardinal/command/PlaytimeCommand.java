package com.bearnation.cardinal.command;

import com.bearnation.cardinal.Cardinal;
import com.bearnation.cardinal.playtime.PlaytimeService;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.util.UuidUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.Optional;
import java.util.UUID;

public class PlaytimeCommand implements SimpleCommand {
    private static final String PERM_SELF = "cardinal.playtime";
    private static final String PERM_OTHERS = "cardinal.playtime.others";

    private final Cardinal plugin;
    private final PlaytimeService service;

    public PlaytimeCommand(Cardinal plugin, PlaytimeService service) {
        this.plugin = plugin;
        this.service = service;
    }

    @Override
    public void execute(Invocation inv) {
        var src = inv.source();
        String[] args = inv.arguments();

        // /playtime -> self
        if (args.length == 0) {
            if (!(src instanceof Player player)) {
                src.sendMessage(Component.text("Usage: /playtime <player>", NamedTextColor.RED));
                return;
            }
            if (!src.hasPermission(PERM_SELF)) {
                src.sendMessage(Component.text("You don't have permission.", NamedTextColor.RED));
                return;
            }
            long seconds = service.getLivePlaySeconds(player.getUniqueId());
            src.sendMessage(formatLine(player.getUsername(), seconds));
            return;
        }

        // /playtime <player|uuid>
        if (!src.hasPermission(PERM_OTHERS)) {
            src.sendMessage(Component.text("You don't have permission to view others.", NamedTextColor.RED));
            return;
        }

        String who = args[0];
        UUID targetUuid = resolveUuid(who);
        if (targetUuid == null) {
            src.sendMessage(Component.text("Player not online and not a valid UUID/name.", NamedTextColor.RED));
            return;
        }

        long seconds = service.getLivePlaySeconds(targetUuid);

        // Prefer live username if they’re online; otherwise echo what was typed
        String displayName = plugin.getServer().getPlayer(targetUuid)
                .map(Player::getUsername)
                .orElse(who);

        src.sendMessage(formatLine(displayName, seconds));
    }

    private UUID resolveUuid(String input) {
        // 1) online player by name
        Optional<Player> online = plugin.getServer().getPlayer(input);
        if (online.isPresent()) return online.get().getUniqueId();

        // 2) dashed UUID
        try {
            return UUID.fromString(input);
        } catch (IllegalArgumentException ignored) { }

        // 3) undashed UUID (Velocity helper)
        try {
            return UuidUtils.fromUndashed(input);
        } catch (IllegalArgumentException ignored) { }

        // 4) (optional) DB lookup by last known name if you store it
        // return DatabaseManager.getUuidByName(input).orElse(null);

        return null;
    }

    private Component formatLine(String name, long seconds) {
        long days = seconds / 86_400;
        long hours = (seconds % 86_400) / 3_600;
        long mins = (seconds % 3_600) / 60;
        long secs = seconds % 60;

        String nice = (days > 0 ? days + "d " : "")
                + (hours > 0 ? hours + "h " : "")
                + (mins > 0 ? mins + "m " : "")
                + secs + "s";

        return Component.text("Playtime for ", NamedTextColor.GRAY)
                .append(Component.text(name, NamedTextColor.YELLOW))
                .append(Component.text(": ", NamedTextColor.GRAY))
                .append(Component.text(nice.trim(), NamedTextColor.GREEN));
    }
}
