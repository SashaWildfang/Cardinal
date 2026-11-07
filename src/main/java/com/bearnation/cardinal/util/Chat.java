package com.bearnation.cardinal.util;

import com.bearnation.cardinal.Cardinal;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public class Chat {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    public static void broadcast(String msg) {
        Component component = LEGACY.deserialize(msg);
        Cardinal.getInstance().getServer().sendMessage(component);
    }
}

