package com.bearnation.cardinal.command;

import com.bearnation.cardinal.match.MatchManager;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.List;

public class QueueGuiCommand implements SimpleCommand {

    private final MatchManager matchManager;

    public QueueGuiCommand(MatchManager matchManager) {
        this.matchManager = matchManager;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        String[] args = invocation.arguments();

        int page = 1;
        if (args.length >= 1) {
            try {
                page = Integer.parseInt(args[0]);
            } catch (NumberFormatException ignored) {}
        }

        if (!(source instanceof Player player)) {
            source.sendMessage(Component.text("Only players can use the queue GUI.", NamedTextColor.RED));
            return;
        }

        List<Player> queue = matchManager.getPlayersInLobby();
        int perPage = 8;
        int totalPages = (int) Math.ceil(queue.size() / (double) perPage);
        page = Math.max(1, Math.min(page, totalPages));
        int start = (page - 1) * perPage;
        int end = Math.min(start + perPage, queue.size());

        player.sendMessage(Component.text("§7==== §aQueue Manager Page " + page + "§7 ===="));

        if (queue.isEmpty()) {
            player.sendMessage(Component.text("§7Queue is empty."));
        } else {
            for (int i = start; i < end; i++) {
                Player target = queue.get(i);
                TextComponent line = Component.text("§a" + target.getUsername() + " ")
                        .append(Component.text("[REMOVE]", NamedTextColor.RED)
                                .clickEvent(ClickEvent.runCommand("/queue remove " + target.getUsername()))
                                .hoverEvent(Component.text("Click to remove " + target.getUsername())));
                player.sendMessage(line);
            }
        }

        Component controls = Component.empty();

        if (page > 1) {
            controls = controls.append(Component.text("[Back]", NamedTextColor.YELLOW)
                            .clickEvent(ClickEvent.runCommand("/queuegui " + (page - 1)))
                            .hoverEvent(Component.text("Go to previous page")))
                    .append(Component.space());
        }

        if (page < totalPages) {
            controls = controls.append(Component.text("[Next]", NamedTextColor.YELLOW)
                            .clickEvent(ClickEvent.runCommand("/queuegui " + (page + 1)))
                            .hoverEvent(Component.text("Go to next page")))
                    .append(Component.space());
        }

        controls = controls.append(Component.text("[Clear Queue]", NamedTextColor.RED)
                        .clickEvent(ClickEvent.runCommand("/queue clear"))
                        .hoverEvent(Component.text("Clear the entire queue")))
                .append(Component.space());

        controls = controls.append(Component.text("[Refresh]", NamedTextColor.GRAY)
                .clickEvent(ClickEvent.runCommand("/queuegui " + page)) // refreshes same page
                .hoverEvent(Component.text("Close this menu")));

        player.sendMessage(Component.text(""));
        player.sendMessage(controls);
    }
}
