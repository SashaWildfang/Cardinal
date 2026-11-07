package com.bearnation.cardinal.command;

import com.bearnation.cardinal.Cardinal;
import com.bearnation.cardinal.match.MatchManager;
import com.bearnation.cardinal.vote.MapVoteManager;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import net.kyori.adventure.text.Component;

import java.util.*;

public class CardinalCommand implements SimpleCommand {

    private static final int COMMANDS_PER_PAGE = 10;

    private record CommandEntry(String name, String desc, String permission) {}

    private static CommandEntry cmd(String name, String desc, String perm) {
        return new CommandEntry(name, desc, perm);
    }

    private static final List<CommandEntry> COMMANDS = List.of(
            cmd("version", "Shows Cardinal version", "cardinal.cmd.version"),
            cmd("maps", "List all loaded maps", "cardinal.cmd.maps"),
            cmd("vote", "View current vote options", "cardinal.cmd.vote"),
            cmd("reload", "Reload plugin (not implemented)", "cardinal.cmd.admin"),
            cmd("autostart on|off", "Toggle autostart", "cardinal.cmd.autostart"),
            cmd("autoqueue on|off", "Toggle autoqueue", "cardinal.cmd.autoqueue"),
            cmd("forcestart", "Force start match", "cardinal.cmd.admin"),
            cmd("getvotes", "Show vote counts", "cardinal.cmd.getvotes"),
            cmd("join", "Join match", "cardinal.cmd.join"),
            cmd("joinqueue", "Queue for match", "cardinal.cmd.joinqueue"),
            cmd("leavequeue", "Leave the queue", "cardinal.cmd.leavequeue"),
            cmd("pausevote", "Pause map voting", "cardinal.cmd.pausevote"),
            cmd("resumevote", "Resume map voting", "cardinal.cmd.resumevote"),
            cmd("stopvote", "Cancel map voting", "cardinal.cmd.stopvote"),
            cmd("phase", "Get current phase", "cardinal.cmd.phase"),
            cmd("queue", "View queued players", "cardinal.cmd.queue"),
            cmd("restartgame", "Restart game flow", "cardinal.cmd.restart"),
            cmd("setphase <PHASE>", "Manually set game phase", "cardinal.cmd.setphase"),
            cmd("startmatch", "Manually start match", "cardinal.cmd.startmatch"),
            cmd("help [page]", "Show help menu", "cardinal.cmd.help")
    );

    private final MatchManager matchManager;
    private final MapVoteManager voteManager;

    public CardinalCommand(MatchManager matchManager, MapVoteManager voteManager) {
        this.matchManager = matchManager;
        this.voteManager = voteManager;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        String[] args = invocation.arguments();

        if (args.length == 0) {
            source.sendMessage(Component.text("§a[Cardinal] §7Game control system made by §fDetectivePlapper"));
            source.sendMessage(Component.text("§7Use §e/cardinal help §7to view commands."));
            return;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "help" -> {
                int page = 1;
                if (args.length >= 2) {
                    try {
                        page = Integer.parseInt(args[1]);
                    } catch (NumberFormatException ignored) {}
                }

                int totalPages = (int) Math.ceil((double) COMMANDS.size() / COMMANDS_PER_PAGE);
                if (page < 1 || page > totalPages) {
                    source.sendMessage(Component.text("§cInvalid page number. There are " + totalPages + " pages."));
                    return;
                }

                source.sendMessage(Component.text("§6§l❖ Cardinal Commands §7(Page " + page + "/" + totalPages + ") ❖"));

                int start = (page - 1) * COMMANDS_PER_PAGE;
                int end = Math.min(COMMANDS.size(), start + COMMANDS_PER_PAGE);
                for (CommandEntry entry : COMMANDS.subList(start, end)) {
                    source.sendMessage(Component.text("§b/cardinal " + entry.name() + " §7- " + entry.desc()));
                }

                if (page < totalPages) {
                    source.sendMessage(Component.text(""));
                    source.sendMessage(Component.text("§7Type §e/cardinal help " + (page + 1) + " §7to see more..."));
                }
            }

            case "version" -> {
                require(source, "cardinal.cmd.version");
                source.sendMessage(Component.text("Cardinal version: §a1.0.0"));
            }

            case "maps" -> new MapsCommand().execute(invocation);
            case "vote" -> new VoteCommand(Cardinal.getInstance().getMapVoteManager()).execute(invocation);
            case "reload" -> new ReloadCommand().execute(invocation);
            case "autostart" -> new AutoStartCommand(Cardinal.getInstance()).execute(invocation);
            case "autoqueue" -> new AutoQueueCommand().execute(invocation);
            case "forcestart" -> new ForceStartCommand(Cardinal.getInstance().getMatchManager()).execute(invocation);
            case "getvotes" -> new GetVotesCommand(voteManager).execute(invocation);
            case "join" -> new JoinCommand(matchManager).execute(invocation);
            case "joinqueue" -> new JoinQueueCommand(matchManager).execute(invocation);
            case "leavequeue" -> new LeaveQueueCommand(matchManager).execute(invocation);
            case "pausevote" -> new PauseVoteCommand().execute(invocation);
            case "resumevote" -> new ResumeVoteCommand().execute(invocation);
            case "stopvote" -> new StopVoteCommand(voteManager, Cardinal.getInstance().getAutoStarter()).execute(invocation);
            case "phase" -> new PhaseCommand(matchManager).execute(invocation);
            case "queue" -> new QueueCommand(matchManager).execute(invocation);
            case "restartgame" -> new RestartGameCommand().execute(invocation);
            case "setphase" -> new SetPhaseCommand(matchManager).execute(invocation);
            case "startmatch" -> new StartMatchCommand(Cardinal.getInstance().getServer(), matchManager).execute(invocation);

            default -> source.sendMessage(Component.text("§cUnknown subcommand. Use §e/cardinal help"));
        }
    }

    private void require(CommandSource source, String permission) {
        if (!source.hasPermission(permission)) {
            throw new IllegalArgumentException("Missing permission: " + permission);
        }
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission("cardinal.cmd");
    }
}
