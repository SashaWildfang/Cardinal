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

public class MsgCommand implements SimpleCommand {

    private final Cardinal plugin;
    private final LuckPermsUtil lp;

    public MsgCommand(Cardinal plugin, LuckPermsUtil lp) {
        this.plugin = plugin;
        this.lp = lp;
    }

    @Override
    public void execute(Invocation invocation) {
        if (invocation.arguments().length < 2) {
            invocation.source().sendMessage(Component.text("Usage: /" + invocation.alias() + " <player> <message>", NamedTextColor.RED));
            return;
        }

        if (!(invocation.source() instanceof Player sender)) return;

        String targetArg = invocation.arguments()[0];
        String message = String.join(" ", List.of(invocation.arguments()).subList(1, invocation.arguments().length));

        Optional<Player> targetOpt = plugin.getServer().getPlayer(targetArg);
        if (targetOpt.isEmpty()) {
            sender.sendMessage(Component.text("Player not found!", NamedTextColor.RED));
            return;
        }

        Player target = targetOpt.get();

        if (IgnoreManager.isIgnoring(target.getUniqueId(), sender.getUniqueId())) {
            sender.sendMessage(Component.text("That player is ignoring you", NamedTextColor.RED));
            return;
        }

        if (target.getUniqueId().equals(sender.getUniqueId())) {
            sender.sendMessage(Component.text("You cannot message yourself.", NamedTextColor.RED));
            return;
        }

        // Metadata
        String senderPrefix = lp.getPrefix(sender) != null ? lp.getPrefix(sender) : "";
        String senderName = sender.getUsername();

        String targetPrefix = lp.getPrefix(target) != null ? lp.getPrefix(target) : "";
        String targetDisplayName = target.getUsername();

        // Messages
        String toMsg = "&dTo " + targetPrefix + targetDisplayName + "&d: &e" + message;
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

        MessageCache.setLastMessaged(sender.getUniqueId(), target.getUniqueId());
        MessageCache.setLastMessaged(target.getUniqueId(), sender.getUniqueId()); // So they can reply too

    }

    @Override
    public List<String> suggest(Invocation invocation) {
        if (!(invocation.source() instanceof Player sender)) return List.of();

        if (invocation.arguments().length == 1) {
            return plugin.getServer().getAllPlayers().stream()
                    .filter(p -> !p.getUniqueId().equals(sender.getUniqueId())) // exclude self
                    .filter(p -> p.getCurrentServer().isPresent() && sender.getCurrentServer().isPresent() &&
                            p.getCurrentServer().get().getServerInfo().getName().equals(
                                    sender.getCurrentServer().get().getServerInfo().getName()))
                    .map(Player::getUsername)
                    .toList();
        }

        return List.of();
    }

}
