package com.bearnation.cardinal.command;

import com.velocitypowered.api.command.SimpleCommand;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;

public class StoreCommand implements SimpleCommand {

    private static final String STORE_URL = "https://store.bearnation.com"; // Replace with your actual store URL

    @Override
    public void execute(Invocation invocation) {
        Component message = Component.text("» Click here to visit our Store! «", NamedTextColor.GOLD)
                .hoverEvent(HoverEvent.showText(Component.text("""
                        Click to open our Store!

                        Buy ranks & cosmetics
                        Support the server
                        Get cool perks!
                        """, NamedTextColor.GRAY)))
                .clickEvent(ClickEvent.openUrl(STORE_URL));

        invocation.source().sendMessage(message);
    }
}
