package com.bearnation.cardinal.proxy;

import com.bearnation.cardinal.bus.MessageBus;
import com.bearnation.cardinal.bus.Bus;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.velocitypowered.api.proxy.ProxyServer;
import org.slf4j.Logger;

import java.util.UUID;

public final class ProxySubscriber {
    private final ProxyServer proxy;
    private final MessageBus bus;
    private final Logger log;

    // Source of truth flags
    private volatile boolean autostartEnabled = true; // set this from your config/command

    public ProxySubscriber(ProxyServer proxy, MessageBus bus, Logger log) {
        this.proxy = proxy;
        this.bus = bus;
        this.log = log;
    }

    public void subscribe() {
        bus.subscribe("bn.proxy", msg -> {
            JsonObject o = JsonParser.parseString(msg).getAsJsonObject();
            if (!o.has("type")) return;
            String type = o.get("type").getAsString();
            JsonObject payload = o.has("payload") && o.get("payload").isJsonObject()
                    ? o.getAsJsonObject("payload") : new JsonObject();

            switch (type) {
                case "playercount.request" -> handlePlayerCountRequest();
                case "autostart_status_request" -> handleAutostartStatusRequest();
                case "proxy.connect" -> handleProxyConnect(payload);
                case "schematic_paste_done" -> handleSchematicPasteDone(payload);
                default -> { /* ignore */ }
            }
        });
    }

    private void handlePlayerCountRequest() {
        int total = proxy.getAllPlayers().size();
        JsonObject p = new JsonObject();
        p.addProperty("count", total);
        bus.publish("bn.hub", Bus.env("playercount_response", "broadcast", p).toString());
    }

    private void handleAutostartStatusRequest() {
        JsonObject p = new JsonObject();
        p.addProperty("enabled", autostartEnabled);
        bus.publish("bn.hub", Bus.env("autostart_status", "broadcast", p).toString());
    }

    private void handleProxyConnect(JsonObject payload) {
        try {
            UUID id = UUID.fromString(payload.get("uuid").getAsString());
            String server = payload.get("server").getAsString();

            proxy.getPlayer(id).ifPresent(player ->
                    proxy.getServer(server).ifPresentOrElse(
                            reg -> player.createConnectionRequest(reg).connect(),
                            () -> log.warn("proxy.connect: server not found {}", server)
                    )
            );
        } catch (Exception e) {
            log.warn("proxy.connect failed: {}", e.getMessage());
        }
    }

    private void handleSchematicPasteDone(JsonObject payload) {
        String mapId = payload.has("mapId") ? payload.get("mapId").getAsString() : "(unknown)";
        log.info("CTF reports schematic paste done for: {}", mapId);
        // Optional: kick off next step in your flow, e.g. phase -> PRE_GAME or similar
    }

    /* Expose a way to toggle autostart from a command */
    public void setAutostartEnabled(boolean enabled) { this.autostartEnabled = enabled; }
}
