package com.bearnation.cardinal.command;

import com.bearnation.cardinal.match.MatchManager;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;

public class QueueGuiBrigadier {

    public static BrigadierCommand create(MatchManager matchManager) {
        LiteralArgumentBuilder<CommandSource> root =
                com.mojang.brigadier.builder.LiteralArgumentBuilder.<CommandSource>literal("queuegui")
                        .executes(context -> {
                            CommandSource source = context.getSource();
                            if (source instanceof Player player) {
                                // Redirect to QueueGuiCommand with no arguments (page 1)
                                new QueueGuiCommand(matchManager).execute(new QueueGuiInvocation(player, new String[0]));
                            } else {
                                source.sendMessage(Component.text("Only players can use this."));
                            }
                            return 1;
                        })
                        .then(com.mojang.brigadier.builder.RequiredArgumentBuilder
                                .<CommandSource, Integer>argument("page", IntegerArgumentType.integer(1))
                                .executes(context -> {
                                    CommandSource source = context.getSource();
                                    int page = IntegerArgumentType.getInteger(context, "page");

                                    if (source instanceof Player player) {
                                        new QueueGuiCommand(matchManager).execute(new QueueGuiInvocation(player, new String[]{String.valueOf(page)}));
                                    } else {
                                        source.sendMessage(Component.text("Only players can use this."));
                                    }
                                    return 1;
                                }));

        return new BrigadierCommand(root);
    }

    // Custom Invocation class so we can call your existing SimpleCommand logic
    private record QueueGuiInvocation(Player player, String[] args) implements com.velocitypowered.api.command.SimpleCommand.Invocation {
        @Override
        public CommandSource source() {
            return player;
        }

        @Override
        public String alias() {
            return "queuegui";
        }

        @Override
        public String[] arguments() {
            return args;
        }
    }
}
