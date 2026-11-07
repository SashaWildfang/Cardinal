package com.bearnation.cardinal.command;

import com.bearnation.cardinal.help.HelpAggregator;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

public final class AutoHelpCommand implements SimpleCommand {
    private static final int PAGE_SIZE = 10;

    private final ProxyServer proxy;
    private final HelpAggregator agg;

    public AutoHelpCommand(ProxyServer proxy, HelpAggregator aggregator) {
        this.proxy = proxy;
        this.agg = aggregator;
    }

    @Override
    public void execute(Invocation inv) {
        var src = inv.source();
        Audience aud = (Audience) src;

        String[] args = inv.arguments();
        String filter = null; Integer pageArg = null;
        if (args.length == 1) { pageArg = tryInt(args[0]); if (pageArg == null) filter = args[0]; }
        else if (args.length >= 2) { filter = args[0]; pageArg = tryInt(args[1]); }
        int page = pageArg == null ? 1 : Math.max(1, pageArg);

        // Proxy-visible commands
        var proxyCmds = agg.collectProxyCommands(src);

        // Backend-visible commands via bus
        var backendCmds = List.<HelpAggregator.HelpEntry>of();
        String where = "Network";
        if (src instanceof Player p) {
            var cur = p.getCurrentServer().orElse(null);
            if (cur != null) {
                String serverKey = cur.getServerInfo().getName().toLowerCase(Locale.ROOT);
                where = serverKey;
                backendCmds = agg.fetchBackendCommands(p.getUniqueId(), serverKey, Duration.ofMillis(600));
            }
        }

        // Merge; backend desc overwrites "No description"
        Map<String,String> merged = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        proxyCmds.forEach(e -> merged.put(e.name(), e.desc()));
        backendCmds.forEach(e -> merged.merge(e.name(), e.desc(),
                (a,b) -> "No description".equalsIgnoreCase(a) ? b : a));

        var items = merged.entrySet().stream()
                .filter(e -> !e.getKey().contains(":"))
                .sorted(Map.Entry.comparingByKey(String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toList());

        if (filter != null && !filter.isBlank()) {
            String f = filter.toLowerCase(Locale.ROOT);
            items = items.stream()
                    .filter(e -> e.getKey().toLowerCase(Locale.ROOT).contains(f)
                            || e.getValue().toLowerCase(Locale.ROOT).contains(f))
                    .collect(Collectors.toList());
        }

        int total = items.size();
        int maxPage = Math.max(1, (int)Math.ceil(total / (double) PAGE_SIZE));
        page = Math.min(page, maxPage);
        int from = Math.max(0, (page - 1) * PAGE_SIZE);
        int to = Math.min(total, from + PAGE_SIZE);
        var slice = items.subList(from, to);

        Component header = Component.text("——— ", NamedTextColor.DARK_GRAY)
                .append(Component.text("Commands ", NamedTextColor.AQUA))
                .append(Component.text("• " + where, NamedTextColor.GOLD))
                .append(Component.text(" • Page " + page + "/" + maxPage, NamedTextColor.GRAY))
                .append(Component.text(" ———", NamedTextColor.DARK_GRAY));

        var lines = slice.stream().map(e -> line(e.getKey(), e.getValue())).toList();
        Component body = lines.isEmpty()
                ? Component.text("No commands found.", NamedTextColor.GRAY)
                : Component.join(JoinConfiguration.separator(Component.newline()), lines);
        Component footer = pager(filter, page, maxPage);

        aud.sendMessage(Component.empty());
        aud.sendMessage(header);
        aud.sendMessage(body);
        aud.sendMessage(footer);
        aud.sendMessage(Component.empty());
    }

    @Override
    public List<String> suggest(Invocation inv) {
        return List.of("1","2","3","4","5");
    }

    private Component line(String name, String desc) {
        String shown = "/" + name;
        return Component.text("• ", NamedTextColor.DARK_GRAY)
                .append(Component.text(shown, NamedTextColor.AQUA)
                        .clickEvent(ClickEvent.suggestCommand(shown + " "))
                        .hoverEvent(HoverEvent.showText(Component.text("Click to type " + shown, NamedTextColor.GRAY))))
                .append(Component.text(" — ", NamedTextColor.DARK_GRAY))
                .append(Component.text((desc == null || desc.isBlank()) ? "No description" : desc, NamedTextColor.WHITE));
    }

    private Component pager(String filter, int page, int max) {
        String base = "/help";
        String prev = (filter == null || filter.isBlank()) ? base + " " + (page - 1) : base + " " + filter + " " + (page - 1);
        String next = (filter == null || filter.isBlank()) ? base + " " + (page + 1) : base + " " + filter + " " + (page + 1);

        Component prevC = (page > 1)
                ? Component.text("« Prev", NamedTextColor.AQUA).clickEvent(ClickEvent.runCommand(prev))
                : Component.text("« Prev", NamedTextColor.DARK_GRAY);
        Component nextC = (page < max)
                ? Component.text("Next »", NamedTextColor.AQUA).clickEvent(ClickEvent.runCommand(next))
                : Component.text("Next »", NamedTextColor.DARK_GRAY);

        return Component.text("——— ", NamedTextColor.DARK_GRAY)
                .append(prevC)
                .append(Component.text("  |  ", NamedTextColor.DARK_GRAY))
                .append(Component.text("Page " + page + "/" + max, NamedTextColor.GRAY))
                .append(Component.text("  |  ", NamedTextColor.DARK_GRAY))
                .append(nextC)
                .append(Component.text(" ———", NamedTextColor.DARK_GRAY));
    }

    private Integer tryInt(String s) { try { return Integer.valueOf(s); } catch (Exception e) { return null; } }
}
