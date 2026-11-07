package com.bearnation.cardinal.command;

import com.velocitypowered.api.command.SimpleCommand;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;

public class DiscordCommand implements SimpleCommand {

    private static final String DISCORD_URL = "https://discord.gg/fPpza7mjCM";

    @Override
    public void execute(Invocation invocation) {
        Component message = Component.text("» Click here to join our Discord server! «", NamedTextColor.AQUA)
                .hoverEvent(HoverEvent.showText(Component.text("""
                        Click to open our Discord server!

                        Meet the community
                        Chat & get support
                        CTF updates + events
                        """, NamedTextColor.GRAY)))
                .clickEvent(ClickEvent.openUrl(DISCORD_URL));

        invocation.source().sendMessage(message);
    }
}
