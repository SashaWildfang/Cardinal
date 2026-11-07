package com.bearnation.cardinal.help;

import com.bearnation.cardinal.bus.MessageBus;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.ProxyServer;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

public final class HelpAggregator {
    private final ProxyServer proxy;
    private final MessageBus bus;

    // corr -> future to complete on reply
    private final ConcurrentMap<String, CompletableFuture<JsonArray>> pending = new ConcurrentHashMap<>();

    public HelpAggregator(ProxyServer proxy, MessageBus bus) {
        this.proxy = proxy;
        this.bus = bus;

        // SINGLE, fixed subscription for all help replies
        bus.subscribe("bn.help.reply", msg -> {
            try {
                JsonObject obj = JsonParser.parseString(msg).getAsJsonObject();
                if (!obj.has("corr")) return;
                String corr = obj.get("corr").getAsString();
                JsonArray cmds = obj.has("commands") && obj.get("commands").isJsonArray()
                        ? obj.getAsJsonArray("commands")
                        : new JsonArray();
                CompletableFuture<JsonArray> fut = pending.remove(corr);
                if (fut != null) fut.complete(cmds);
            } catch (Throwable ignored) {}
        });
    }

    /** Ask the backend (serverKey like "hub" or "ctf-1") for its commands for this player. */
    public List<HelpEntry> fetchBackendCommands(UUID playerId, String serverKey, Duration timeout) {
        final String corr = UUID.randomUUID().toString();
        final CompletableFuture<JsonArray> fut = new CompletableFuture<>();
        pending.put(corr, fut);

        JsonObject payload = new JsonObject();
        payload.addProperty("type", "help_request");
        payload.addProperty("corr", corr);
        payload.addProperty("reply", "bn.help.reply");
        payload.addProperty("player", playerId.toString());
        payload.addProperty("server", serverKey);

        bus.publish("bn." + serverKey, payload.toString());

        try {
            JsonArray arr = fut.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            // LOG: reply in

            List<HelpEntry> out = new ArrayList<>(arr.size());
            for (var el : arr) {
                JsonObject o = el.getAsJsonObject();
                String name = o.get("name").getAsString();
                String desc = o.has("desc") ? o.get("desc").getAsString() : "No description";
                out.add(new HelpEntry(name, desc));
            }
            return out;
        } catch (Exception e) {
            System.out.println("[HelpAggregator] timeout/no-reply corr=" + corr + " err=" + e.getClass().getSimpleName());
            pending.remove(corr);
            return List.of();
        }
    }

    public void helpPing(String serverKey) {
        JsonObject o = new JsonObject();
        o.addProperty("type", "ping");
        o.addProperty("reply", "bn.help.reply");
        o.addProperty("corr", "ping-" + System.currentTimeMillis());

        bus.publish("bn." + serverKey, o.toString());
    }

    /** Proxy-visible commands (permission-aware) with Brigadier tooltip descriptions when available. */
    /** Proxy-visible commands (permission-aware), with strong fallbacks and namespace cleanup. */
    public List<HelpEntry> collectProxyCommands(CommandSource source) {
        var cm = proxy.getCommandManager();

        // 1) Try Velocity suggestions (usually permission-aware)
        List<String> names;
        try {
            names = cm.offerSuggestions(source, "/").join();
        } catch (Throwable t) {
            names = List.of();
        }
        if (names == null) names = List.of();

        // 2) Fallback if suggestions are empty or suspiciously small
        if (names.isEmpty()) {
            try {
                names = cm.getAliases().stream()
                        .filter(alias -> cm.hasCommand(alias, source))          // permission/availability
                        .map(alias -> alias.contains(":")                       // strip namespace if present
                                ? alias.substring(alias.indexOf(':') + 1)
                                : alias)
                        .distinct()
                        .sorted(String.CASE_INSENSITIVE_ORDER)
                        .toList();
            } catch (Throwable ignored) {
                names = List.of();
            }
        } else {
            // Normalize suggestion results: strip leading "/" and any namespace
            names = names.stream()
                    .map(s -> s.startsWith("/") ? s.substring(1) : s)
                    .map(s -> s.contains(":") ? s.substring(s.indexOf(':') + 1) : s)
                    .distinct()
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .toList();
        }

        // 3) Try Brigadier tooltips for descriptions (optional)
        Map<String,String> descs = new HashMap<>();
        try {
            var sug = cm.offerBrigadierSuggestions(source, "/").join();
            if (sug != null) {
                sug.getList().forEach(s -> {
                    String t = s.getText();
                    if (t == null || t.isBlank()) return;
                    if (t.startsWith("/")) t = t.substring(1);
                    if (t.contains(":")) t = t.substring(t.indexOf(':') + 1);
                    var tip = s.getTooltip();
                    if (tip != null) {
                        String txt = tip.getString();
                        if (txt != null && !txt.isBlank()) descs.put(t, txt);
                    }
                });
            }
        } catch (Throwable ignored) {}

        // 4) Build entries
        List<HelpEntry> out = new ArrayList<>(names.size());
        for (String n : names) {
            out.add(new HelpEntry(n, descs.getOrDefault(n, "No description")));
        }

        // Debug (optional): uncomment to verify proxy count
        // System.out.println("[HelpAggregator] proxy-commands=" + out.size());

        return out;
    }


    public record HelpEntry(String name, String desc) {}
}
