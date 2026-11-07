package com.bearnation.cardinal.command;

import com.bearnation.cardinal.Cardinal;
import com.bearnation.cardinal.util.IgnoreManager;
import com.bearnation.cardinal.util.LuckPermsUtil;
import com.bearnation.cardinal.util.MessageCache;
import com.bearnation.cardinal.util.SpyManager;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

public class ReplyCommand implements SimpleCommand {

    private final Cardinal plugin;
    private final LuckPermsUtil lp;

    public ReplyCommand(Cardinal plugin, LuckPermsUtil lp) {
        this.plugin = plugin;
        this.lp = lp;
    }

    @Override
    public void execute(Invocation invocation) {
        if (!(invocation.source() instanceof Player sender)) return;

        if (invocation.arguments().length < 1) {
            invocation.source().sendMessage(Component.text("Usage: /r <message>", NamedTextColor.RED));
            return;
        }

        UUID targetId = MessageCache.getLastMessaged(sender.getUniqueId());
        if (targetId == null) {
            sender.sendMessage(Component.text("You haven't messaged anyone recently.", NamedTextColor.RED));
            return;
        }

        Optional<Player> targetOpt = plugin.getServer().getPlayer(targetId);
        if (targetOpt.isEmpty()) {
            sender.sendMessage(Component.text("That player is no longer online.", NamedTextColor.RED));
            return;
        }

        Player target = targetOpt.get();
        String message = String.join(" ", List.of(invocation.arguments()));

        if (IgnoreManager.isIgnoring(target.getUniqueId(), sender.getUniqueId())) {
            sender.sendMessage(Component.text("That player is ignoring you.", NamedTextColor.RED));
            return;
        }

        // Metadata
        String senderPrefix = lp.getPrefix(sender) != null ? lp.getPrefix(sender) : "";
        String senderName = sender.getUsername();

        String targetPrefix = lp.getPrefix(target) != null ? lp.getPrefix(target) : "";
        String targetName = target.getUsername();

        String toMsg = "&dTo " + targetPrefix + targetName + "&d: &e" + message;
        String fromMsg = "&dFrom " + senderPrefix + senderName + "&d: &e" + message;


        sender.sendMessage(Cardinal.legacy().deserialize(toMsg));
        target.sendMessage(Cardinal.legacy().deserialize(fromMsg));

        Component spyMessage = Cardinal.legacy().deserialize(
                "&7[&cSpy&7] " + sender.getUsername() + " &7-> " + target.getUsername() + ": &f" + message);

        for (UUID spyUUID : SpyManager.getSpies()) {
            if (!spyUUID.equals(sender.getUniqueId()) && !spyUUID.equals(target.getUniqueId())) {
                plugin.getServer().getPlayer(spyUUID).ifPresent(spy -> spy.sendMessage(spyMessage));
            }
        }

        // Update cache
        MessageCache.setLastMessaged(sender.getUniqueId(), target.getUniqueId());
        MessageCache.setLastMessaged(target.getUniqueId(), sender.getUniqueId());
    }
}
