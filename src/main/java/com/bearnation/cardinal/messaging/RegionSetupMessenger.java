package com.bearnation.cardinal.messaging;

import com.bearnation.cardinal.map.MapInfo;
import com.bearnation.cardinal.map.RegionBounds;
import com.google.gson.JsonObject;

import java.util.Map;

/**
 * Publishes region-setup jobs to CTF via NATS.
 */
public final class RegionSetupMessenger {

    private final Messaging messaging;   // NATS publisher

    /**
     * @param messaging Your Messaging facade (already wired with bus + ctfTarget)
     */
    public RegionSetupMessenger(Messaging messaging) {
        this.messaging = messaging;
    }

    /**
     * Sends a region setup job to CTF.
     * If your Messaging was constructed with a fixed ctfTarget, that will be used.
     * If you want to dynamically choose a target per call, use the overload below.
     */
    public void sendRegionSetup(MapInfo mapInfo) {
        messaging.sendRegionSetup(buildPayload(mapInfo));
    }

    /**
     * Sends a region setup job to a specific CTF shard.
     * Use this if you want to override the target from code.
     */
    public void sendRegionSetupTo(String ctfTarget, MapInfo mapInfo) {
        JsonObject payload = buildPayload(mapInfo);
        messaging.sendRegionSetupTo(ctfTarget, payload);
    }

    /* ---------------- private helpers ---------------- */

    private JsonObject buildPayload(MapInfo mapInfo) {
        JsonObject payload = new JsonObject();

        // Core identifiers
        String mapId = mapInfo.getId();
        String mapName = mapInfo.getName() != null ? mapInfo.getName() : mapId;
        String author  = mapInfo.getAuthor() != null ? mapInfo.getAuthor() : "Unknown";

        payload.addProperty("mapId", mapId);
        payload.addProperty("mapName", mapName); // <-- added
        payload.addProperty("author", author);   // <-- added

        // Region bounds
        JsonObject boundsObj = new JsonObject();
        for (Map.Entry<String, RegionBounds> entry : mapInfo.getRegionBounds().entrySet()) {
            RegionBounds b = entry.getValue();
            JsonObject bounds = new JsonObject();
            bounds.addProperty("world", b.getWorld());
            bounds.addProperty("minX", b.getMinX());
            bounds.addProperty("minY", b.getMinY());
            bounds.addProperty("minZ", b.getMinZ());
            bounds.addProperty("maxX", b.getMaxX());
            bounds.addProperty("maxY", b.getMaxY());
            bounds.addProperty("maxZ", b.getMaxZ());
            boundsObj.add(entry.getKey(), bounds);
        }
        payload.add("regionBounds", boundsObj);

        // Region flags
        JsonObject flagsObj = new JsonObject();
        for (Map.Entry<String, Map<String, Object>> entry : mapInfo.getRegionFlags().entrySet()) {
            JsonObject flagData = new JsonObject();
            for (Map.Entry<String, Object> f : entry.getValue().entrySet()) {
                flagData.addProperty(f.getKey(), String.valueOf(f.getValue()));
            }
            flagsObj.add(entry.getKey(), flagData);
        }
        payload.add("regionFlags", flagsObj);

        return payload;
    }
}
