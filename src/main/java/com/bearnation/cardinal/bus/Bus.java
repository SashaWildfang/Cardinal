package com.bearnation.cardinal.bus;

import com.google.gson.JsonObject;
import java.util.UUID;

public final class Bus {
    private Bus() {}
    public static JsonObject env(String type, String target, JsonObject payload) {
        JsonObject o = new JsonObject();
        o.addProperty("v", 1);
        o.addProperty("type", type);
        o.addProperty("target", target == null ? "broadcast" : target);
        o.addProperty("traceId", UUID.randomUUID().toString());
        o.add("payload", payload == null ? new JsonObject() : payload);
        return o;
    }
}
