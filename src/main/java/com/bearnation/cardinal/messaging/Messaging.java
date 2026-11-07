package com.bearnation.cardinal.messaging;

import com.bearnation.cardinal.bus.Bus;
import com.bearnation.cardinal.bus.MessageBus;
import com.google.gson.JsonObject;

import java.util.UUID;

public final class Messaging {
    private final MessageBus bus;
    private final String hubSubject = "bn.hub";
    private final String ctfSubject = "bn.ctf";
    private final String ctfTarget; // e.g., "ctf-1" (can be null -> broadcast)

    public Messaging(MessageBus bus, String ctfTarget) {
        this.bus = bus;
        this.ctfTarget = ctfTarget;
    }

    /* -------- Hub (lobby UI) -------- */
    public void broadcastVoteCounts(JsonObject countsPayload) {
        bus.publish(hubSubject, Bus.env("vote_counts", "broadcast", countsPayload).toString());
    }

    public void broadcastVoteResults(JsonObject resultsPayload) {
        bus.publish(hubSubject, Bus.env("vote_results", "broadcast", resultsPayload).toString());
    }

    public void broadcastPostVotingCountdown(int seconds) {
        JsonObject p = new JsonObject();
        p.addProperty("seconds", seconds);
        bus.publish(hubSubject, Bus.env("post_voting_countdown", "broadcast", p).toString());
    }

    /* ==== Chat: NEW methods that include sender ==== */
    public void chatGlobal(String sender, String miniMessage) {
        JsonObject p = new JsonObject();
        p.addProperty("message", miniMessage);
        p.addProperty("sender", sender); // Hub uses this for hover/click
        bus.publish(hubSubject, Bus.env("chat-global", "broadcast", p).toString());
    }

    public void chatTeam(String teamName, String sender, String miniMessage) {
        JsonObject p = new JsonObject();
        p.addProperty("team", teamName);
        p.addProperty("message", miniMessage);
        p.addProperty("sender", sender);
        bus.publish(hubSubject, Bus.env("chat-team", "broadcast", p).toString());
    }

    public void chatStaff(String sender, String miniMessage) {
        JsonObject p = new JsonObject();
        p.addProperty("message", miniMessage);
        p.addProperty("sender", sender);
        bus.publish(hubSubject, Bus.env("chat-staff", "broadcast", p).toString());
    }

    /* ==== Chat: legacy overloads (backward compatible) ==== */
    /** @deprecated Use chatGlobal(String sender, String miniMessage) */
    @Deprecated
    public void chatGlobal(String miniMessage) {
        JsonObject p = new JsonObject();
        p.addProperty("message", miniMessage);
        // no sender available here; Hub code already falls back gracefully
        bus.publish(hubSubject, Bus.env("chat-global", "broadcast", p).toString());
    }

    /** @deprecated Use chatTeam(String teamName, String sender, String miniMessage) */
    @Deprecated
    public void chatTeam(String teamName, String miniMessage) {
        JsonObject p = new JsonObject();
        p.addProperty("team", teamName);
        p.addProperty("message", miniMessage);
        bus.publish(hubSubject, Bus.env("chat-team", "broadcast", p).toString());
    }

    /** @deprecated Use chatStaff(String sender, String miniMessage) */
    @Deprecated
    public void chatStaff(String miniMessage) {
        JsonObject p = new JsonObject();
        p.addProperty("message", miniMessage);
        bus.publish(hubSubject, Bus.env("chat-staff", "broadcast", p).toString());
    }

    public void updatePhaseForHub(String phase) {
        JsonObject p = new JsonObject();
        p.addProperty("phase", phase);
        bus.publish(hubSubject, Bus.env("phase", "broadcast", p).toString());
    }

    public void sendPlayerCountToHub(int count) {
        JsonObject p = new JsonObject();
        p.addProperty("count", count);
        bus.publish(hubSubject, Bus.env("playercount_response", "broadcast", p).toString());
    }

    public void sendAutoStartStatusToHub(boolean enabled) {
        JsonObject p = new JsonObject();
        p.addProperty("enabled", enabled);
        bus.publish(hubSubject, Bus.env("autostart_status", "broadcast", p).toString());
    }

    /* -------- CTF (job/control) -------- */
    /** Uses the default ctfTarget provided in the constructor. */
    public void sendRegionSetup(JsonObject regionSetup) {
        bus.publish(ctfSubject, Bus.env("region_setup", ctfTarget, regionSetup).toString());
    }

    /** Override the target CTF shard for this call. */
    public void sendRegionSetupTo(String targetCtf, JsonObject regionSetup) {
        bus.publish(ctfSubject, Bus.env("region_setup", targetCtf, regionSetup).toString());
    }

    public void sendSchematicPaste(String mapId, String schematic, int x, int y, int z) {
        JsonObject p = new JsonObject();
        p.addProperty("mapId", mapId);
        p.addProperty("schematic", schematic);
        p.addProperty("pasteX", x);
        p.addProperty("pasteY", y);
        p.addProperty("pasteZ", z);
        bus.publish(ctfSubject, Bus.env("schematic_paste", ctfTarget, p).toString());
    }

    public void announceWorldResetStart() {
        bus.publish(ctfSubject, Bus.env("world_reset_start", ctfTarget, new JsonObject()).toString());
    }

    public void announceWorldResetFinish() {
        bus.publish(ctfSubject, Bus.env("world_reset_finish", ctfTarget, new JsonObject()).toString());
    }

    public void updatePhaseForCtf(String phase) {
        JsonObject p = new JsonObject();
        p.addProperty("phase", phase);
        bus.publish(ctfSubject, Bus.env("phase", ctfTarget, p).toString());
    }

    public MessageBus getBus() {
        return bus;
    }

    // Hub: tell clients voting resumed (so Hub clears “paused” and reapplies boards)
    public void notifyVoteResumed() {
        bus.publish(hubSubject, Bus.env("vote_resumed", "broadcast", new JsonObject()).toString());
    }

    // Hub: cancel reason (structured)
    public void notifyVoteCancelled(String reason) {
        JsonObject p = new JsonObject();
        p.addProperty("reason", reason); // fix: "reason" so Hub reads it directly
        bus.publish(hubSubject, Bus.env("vote_cancelled_reason", "broadcast", p).toString());
    }

    // Hub: ask it to refresh a single player's stats by UUID
    public void requestStatsRefresh(UUID uuid) {
        JsonObject p = new JsonObject();
        p.addProperty("uuid", uuid.toString());
        bus.publish(hubSubject, Bus.env("refresh_stats", "broadcast", p).toString());
    }

    // Proxy: let Velocity-side subscriber handle things like playercount, connect, etc.
    public void proxyConnect(UUID uuid, String server) {
        JsonObject p = new JsonObject();
        p.addProperty("uuid", uuid.toString());
        p.addProperty("server", server);
        bus.publish("bn.proxy", Bus.env("proxy.connect", "proxy", p).toString());
    }


    public void broadcastAutoStartStatus(boolean enabled) {
        JsonObject payload = new JsonObject();
        payload.addProperty("enabled", enabled);
        // fan out to all hubs
        bus.publish("bn.hub", Bus.env("autostart_status", "broadcast", payload).toString());
    }
}
