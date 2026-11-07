package com.bearnation.cardinal.listener;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyPingEvent;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerPing;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.*;
import java.util.stream.Collectors;

public final class ServerListListener {

    private final ProxyServer proxy;
    private static final LegacyComponentSerializer AMP = LegacyComponentSerializer.legacyAmpersand();
    private static final LegacyComponentSerializer SEC = LegacyComponentSerializer.legacySection();
    private static final MiniMessage MM = MiniMessage.miniMessage();

    private static final int SAMPLE_LIMIT = 8;
    private static final boolean SHOW_ZERO_COUNT = true;

    private static final Map<String, String> PRETTY = Map.of(
            "hub", "Network Hub",
            "ctf", "CTF"
    );

    // MOTD parts
    private static final String MOTD_TOP =
            "<gradient:#ffffff:#00aaff><bold>Bear Nation</bold></gradient> <dark_gray><bold>|</bold> " +
                    "<gradient:#00d2ff:#3a7bd5><bold>#1 CTF Server</bold></gradient> <gray>[1.21.8]";

    private static final String[] MOTD_BOTTOM_ROTATING = {
            "<green><bold>BETA TESTING</bold></green> <dark_gray><bold>|</bold> <white>Join and play today!</white>",
            "<aqua><bold>TEAM UP</bold></aqua> <dark_gray><bold>|</bold> <white>Form squads & capture flags!</white>",
            "<gold><bold>NEW MAPS</bold></gold> <dark_gray><bold>|</bold> <white>Be the first to master them.</white>",
            "<light_purple><bold>DISCORD</bold></light_purple> <dark_gray><bold>|</bold> <white>/discord for giveaways</white>",
            "<red><bold>DOUBLE XP</bold></red> <dark_gray><bold>|</bold> <white>Level faster this weekend!</white>",
            "<gradient:#ff6a00:#ffcc00><bold>CAPTURE THE FLAG</bold></gradient> <dark_gray><bold>|</bold> <white>Classic mode, modern twist.</white>",
            "<blue><bold>CTF MADNESS</bold></blue> <dark_gray><bold>|</bold> <white>Flag runs every 5 minutes!</white>",
            "<gradient:#ff00ff:#00ffff><bold>COSMETICS</bold></gradient> <dark_gray><bold>|</bold> <white>Stand out with style.</white>",
            "<green><bold>LEADERBOARDS</bold></green> <dark_gray><bold>|</bold> <white>Can you reach the top?</white>",
            "<gradient:#00ff87:#60efff><bold>FAST QUEUES</bold></gradient> <dark_gray><bold>|</bold> <white>Jump right into the action!</white>",
            "<gold><bold>GOLD RUSH</bold></gold> <dark_gray><bold>|</bold> <white>Earn coins as you play.</white>",
            "<gradient:#ff4e50:#f9d423><bold>NEW KITS</bold></gradient> <dark_gray><bold>|</bold> <white>Unlock abilities & dominate.</white>",
            "<red><bold>TOURNAMENTS</bold></red> <dark_gray><bold>|</bold> <white>Prizes for the best teams.</white>",
            "<gradient:#8e2de2:#4a00e0><bold>STRATEGY WINS</bold></gradient> <dark_gray><bold>|</bold> <white>Plan, attack, defend.</white>",
            "<aqua><bold>FLAGS GALORE</bold></aqua> <dark_gray><bold>|</bold> <white>Multiple modes available.</white>",
            "<gradient:#ff512f:#dd2476><bold>PARTY UP</bold></gradient> <dark_gray><bold>|</bold> <white>Play with your friends!</white>",
            "<yellow><bold>DAILY REWARDS</bold></yellow> <dark_gray><bold>|</bold> <white>Log in every day & collect.</white>",
            "<gradient:#ff9a9e:#fad0c4><bold>SPRINT & STEAL</bold></gradient> <dark_gray><bold>|</bold> <white>Fast-paced flag capture.</white>",
            "<light_purple><bold>EVENT NIGHT</bold></light_purple> <dark_gray><bold>|</bold> <white>Special maps & challenges.</white>",
            "<gradient:#11998e:#38ef7d><bold>COMMUNITY POWER</bold></gradient> <dark_gray><bold>|</bold> <white>Vote for new features.</white>"
    };


    private static final long ROTATE_MS = 20_000L; // change rotation speed here

    @Inject
    public ServerListListener(ProxyServer proxy) {
        this.proxy = proxy;
    }

    @Subscribe
    public void onPing(ProxyPingEvent event) {
        ServerPing orig = event.getPing();
        ServerPing.Builder ping = orig.asBuilder();

        // ---- Rotating MOTD (keeps your gradient top line) ----
        int idx = (int) ((System.currentTimeMillis() / ROTATE_MS) % MOTD_BOTTOM_ROTATING.length);
        String motd = MOTD_TOP + "\n" + MOTD_BOTTOM_ROTATING[idx];
        Component desc = MM.deserialize(motd);
        ping.description(desc);

        // ---- Player counts ----
        int online = proxy.getAllPlayers().size();
        ping.onlinePlayers(online);
        orig.getPlayers().ifPresent(p -> ping.maximumPlayers(p.getMax()));

        // ---- Hover (player sample) ----
        List<ServerPing.SamplePlayer> sample = new ArrayList<>();
        sample.add(sampleLine("&bWelcome to Bear Nation"));
        sample.add(sampleLine("&7There are currently &a" + online + " &7players online"));
        sample.add(sampleLine(""));

        List<RegisteredServer> servers = new ArrayList<>(proxy.getAllServers());
        servers.sort(Comparator.comparingInt((RegisteredServer s) -> s.getPlayersConnected().size()).reversed());

        int shown = 0;
        for (RegisteredServer rs : servers) {
            int count = rs.getPlayersConnected().size();
            if (!SHOW_ZERO_COUNT && count == 0) continue;
            if (shown >= SAMPLE_LIMIT) break;

            String raw = rs.getServerInfo().getName();
            String pretty = PRETTY.getOrDefault(raw, titleize(raw));
            String line = String.format("&8[] &a%d &7on &b%s", count, pretty); // brackets with no icon
            sample.add(sampleLine(line));
            shown++;
        }

        int remaining = servers.size() - shown;
        if (remaining > 0) {
            sample.add(sampleLine(""));
            sample.add(sampleLine("&7… and &e" + remaining + " &7more …"));
        }
        sample.add(sampleLine(""));

        ping.samplePlayers(sample);
        event.setPing(ping.build());
    }

    // ----- helpers -----

    private ServerPing.SamplePlayer sampleLine(String ampersandColored) {
        String section = SEC.serialize(AMP.deserialize(ampersandColored)); // & -> §
        return new ServerPing.SamplePlayer(section, UUID.randomUUID());
    }

    private static String titleize(String name) {
        String cleaned = name.replace('-', ' ').replace('_', ' ');
        return Arrays.stream(cleaned.split("\\s+"))
                .filter(s -> !s.isBlank())
                .map(s -> s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1))
                .collect(Collectors.joining(" "));
    }
}
