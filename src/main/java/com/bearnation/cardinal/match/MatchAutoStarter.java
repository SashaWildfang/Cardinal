package com.bearnation.cardinal.match;

import com.bearnation.cardinal.Cardinal;
import com.bearnation.cardinal.GamePhase;
import com.bearnation.cardinal.bus.Bus;
import com.bearnation.cardinal.map.MapInfo;
import com.bearnation.cardinal.map.MapManager;
import com.bearnation.cardinal.vote.MapVoteManager;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.scheduler.ScheduledTask;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;


import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class MatchAutoStarter {

    private final MatchManager matchManager;
    private final MapVoteManager voteManager;
    private final Cardinal plugin;
    private ScheduledTask autoStartTask;
    private ScheduledTask countdownTask;
    private ScheduledTask voteTransitionTask;
    private boolean voteResumedAfterPause = false;
    private boolean autoStartEnabled = true;
    private static final Path AUTO_START_FILE = Path.of("data", "auto_start.txt");
    private final Set<UUID> queuedPlayers = new HashSet<>();

    private static final int MIN_PLAYERS_TO_START = 2;
    private static final int COUNTDOWN_SECONDS = 10;
    private static final int VOTING_DURATION_SECONDS = 30; // kept for reference
    private static final Path LAST_MAP_FILE = Path.of("data", "last_map.txt");

    private boolean countdownStarted = false;
    private int countdownTimeLeft = COUNTDOWN_SECONDS;
    private String lastPlayedMapId = null;

    public MatchAutoStarter(Cardinal plugin, MatchManager matchManager, MapVoteManager voteManager) {
        this.plugin = plugin;
        this.matchManager = matchManager;
        this.voteManager = voteManager;
        loadLastPlayedMap();
        loadAutoStartState();
    }

    public void startMonitoring() {
        if (!autoStartEnabled) return;
        if (autoStartTask != null) autoStartTask.cancel();

        Cardinal.log("&7[MatchAutoStarter] Monitoring for minimum players to start match...");
        autoStartTask = plugin.getServer().getScheduler()
                .buildTask(plugin, this::checkStartConditions)
                .repeat(2, TimeUnit.SECONDS)
                .schedule();
    }

    private void checkStartConditions() {
        Collection<Player> players = plugin.getServer().getAllPlayers().stream()
                .filter(p -> p.getCurrentServer().isPresent()
                        && p.getCurrentServer().get().getServerInfo().getName().equalsIgnoreCase("hub"))
                .toList();

        if (matchManager.getCurrentPhase() == GamePhase.POST_VOTING || matchManager.isMatchInProgress()) {
            return;
        }

        if (players.size() >= MIN_PLAYERS_TO_START
                && !countdownStarted
                && matchManager.getCurrentPhase() == GamePhase.IDLE) {

            if (voteManager.isVotePaused() && !voteResumedAfterPause) {
                Cardinal.log("&a[MatchAutoStarter] Enough players — resuming paused vote.");
                voteResumedAfterPause = true;
                voteManager.resumeVoting();

                for (Player p : plugin.getServer().getAllPlayers()) {
                    p.getCurrentServer().ifPresent(s -> {
                        if (s.getServerInfo().getName().equalsIgnoreCase("hub")) sendVoteUiTo(p);
                    });
                }

                // Push current vote state to Hub via NATS
                String json = voteManager.getCurrentVoteStateJson();
                try {
                    JsonObject payload = JsonParser.parseString(json).getAsJsonObject();
                    plugin.getMessaging().broadcastVoteCounts(payload);
                } catch (Exception e) {
                    Cardinal.log("&c[MatchAutoStarter] Failed to parse vote state JSON: " + e.getMessage());
                }

                // Also send a simple "vote_resumed" signal
                plugin.getMessaging().notifyVoteResumed();
                return;
            }

            if (!voteManager.isVotePaused()) {
                Cardinal.log("&e[MatchAutoStarter] Enough players detected (" + players.size() + "). Starting countdown.");
                startCountdown(players);
            }

        } else if (players.size() < MIN_PLAYERS_TO_START && countdownStarted) {
            Cardinal.log("&c[MatchAutoStarter] Not enough players anymore. Canceling voting...");
            cancelVotingAndReset();
        } else if (players.size() < MIN_PLAYERS_TO_START) {
            int needed = MIN_PLAYERS_TO_START - players.size();
            Component message = Component.text("Waiting for " + needed + " more player" + (needed > 1 ? "s" : "") + " to start", NamedTextColor.YELLOW);
            for (Player player : players) {
                player.sendActionBar(message);
            }
        }
    }

    private void startCountdown(Collection<Player> players) {
        countdownStarted = true;

        List<MapInfo> eligibleMaps = new ArrayList<>();
        for (MapInfo map : MapManager.getInstance().getMaps()) {
            if (map.getRequiredPlayers() > players.size()) continue;
            if (lastPlayedMapId != null && map.getId().equalsIgnoreCase(lastPlayedMapId)) continue;
            eligibleMaps.add(map);
        }

        if (eligibleMaps.isEmpty()) {
            Cardinal.log("&c[MatchAutoStarter] No maps meet the required player count or all are repeats.");
            for (Player player : players) {
                player.sendMessage(Cardinal.color("&cNo eligible maps available for current player count."));
            }
            countdownStarted = false;
            return;
        }

        List<MapInfo> redVsBlue = eligibleMaps.stream()
                .filter(map -> map.getRegionBounds().keySet().stream().filter(r -> r.startsWith("spawn_")).count() == 2)
                .toList();

        List<MapInfo> votingPool = new ArrayList<>(!redVsBlue.isEmpty() ? redVsBlue : eligibleMaps);
        Collections.shuffle(votingPool);
        List<MapInfo> voteOptions = votingPool.subList(0, Math.min(3, votingPool.size()));

        matchManager.setCurrentPhase(GamePhase.VOTING, null);
        voteManager.startVoting(voteOptions);

        for (Player p : players) {
            sendVoteUiTo(p);
        }

        voteTransitionTask = plugin.getServer().getScheduler()
                .buildTask(plugin, () -> {
                    if (!voteManager.isVotingActive()) {
                        if (voteTransitionTask != null) {
                            voteTransitionTask.cancel();
                            voteTransitionTask = null;
                        }
                        return;
                    }

                    if (!voteManager.isVotePaused() && voteManager.getTimeLeftSeconds() <= 0) {
                        startCountdownTask(players, voteOptions);
                        if (voteTransitionTask != null) {
                            voteTransitionTask.cancel();
                            voteTransitionTask = null;
                        }
                    }
                })
                .repeat(1, TimeUnit.SECONDS)
                .schedule();
    }

    private void startCountdownTask(Collection<Player> players, List<MapInfo> voteOptions) {
        if (countdownTask != null) countdownTask.cancel();
        countdownTimeLeft = COUNTDOWN_SECONDS;

        MapInfo selectedMap = voteManager.hasVotes()
                ? voteManager.getWinner().orElse(voteOptions.get(0))
                : voteOptions.get(0);

        List<MapInfo> tiedMaps = voteManager.getTiedMaps(selectedMap);
        List<String> tied = tiedMaps.size() > 1
                ? tiedMaps.stream().map(MapInfo::getId).toList()
                : List.of();

        matchManager.setCurrentPhase(GamePhase.POST_VOTING, null);

        // Hub: vote results packet (includes names)
        JsonObject results = new JsonObject();
        results.addProperty("selectedMap", selectedMap.getId());
        results.addProperty("countdown", COUNTDOWN_SECONDS);
        var tiedArr = new com.google.gson.JsonArray();
        tied.forEach(tiedArr::add);
        results.add("tiedMaps", tiedArr);
        JsonObject namesObj = new JsonObject();
        for (MapInfo m : voteOptions) { // or voteManager.getCurrentOptions()
            namesObj.addProperty(m.getId(), m.getName());
        }
        results.add("mapNames", namesObj);
        plugin.getMessaging().broadcastVoteResults(results);

        // CTF: schematic + regions over NATS
        plugin.getMessaging().sendSchematicPaste(
                selectedMap.getId(),
                selectedMap.getSchematic(),
                selectedMap.getPasteX(),
                selectedMap.getPasteY(),
                selectedMap.getPasteZ()
        );
        plugin.getRegionSetupMessenger().sendRegionSetup(selectedMap);

        // Countdown tick → Hub
        countdownTask = plugin.getServer().getScheduler()
                .buildTask(plugin, task -> {
                    if (countdownTimeLeft <= 0) {
                        task.cancel();
                        countdownStarted = false;
                        lastPlayedMapId = selectedMap.getId();
                        saveLastPlayedMap();

                        // Transfer players to CTF
                        plugin.getServer().getAllPlayers().forEach(player ->
                                plugin.getServer().getServer("CTF").ifPresent(server ->
                                        player.createConnectionRequest(server).fireAndForget()
                                )
                        );

                        // Start pre-game
                        plugin.getServer().getScheduler()
                                .buildTask(plugin, () -> matchManager.startPreGamePhase(selectedMap))
                                .delay(1, TimeUnit.SECONDS)
                                .schedule();
                        return;
                    }

                    plugin.getMessaging().broadcastPostVotingCountdown(countdownTimeLeft);
                    countdownTimeLeft--;
                })
                .repeat(1, TimeUnit.SECONDS)
                .schedule();
    }

    private void cancelVotingAndReset() {
        String reason = "Not enough players. Canceling voting phase.";
        Cardinal.log("&c[MatchAutoStarter] " + reason);
        notifyLobbyVoteCanceled(reason);

        if (countdownTask != null) {
            countdownTask.cancel();
            countdownTask = null;
        }

        if (voteTransitionTask != null) {
            voteTransitionTask.cancel();
            voteTransitionTask = null;
        }

        voteManager.cancelVoting();
        voteManager.resetPauseState();

        matchManager.setCurrentPhase(GamePhase.IDLE, null);
        countdownStarted = false;
        countdownTimeLeft = COUNTDOWN_SECONDS;
    }

    public void stopMonitoring() {
        if (autoStartTask != null) autoStartTask.cancel();
        if (countdownTask != null) countdownTask.cancel();

        countdownStarted = false;
        countdownTimeLeft = COUNTDOWN_SECONDS;
        Cardinal.log("&c[MatchAutoStarter] Monitoring stopped.");
    }

    public void handlePlayerLeaveCheck() {
        if (plugin.getServer().getAllPlayers().size() < MIN_PLAYERS_TO_START && countdownStarted) {
            cancelVotingAndReset();
        }
    }

    private void saveLastPlayedMap() {
        try {
            Files.createDirectories(LAST_MAP_FILE.getParent());
            Files.writeString(LAST_MAP_FILE, lastPlayedMapId);
        } catch (IOException e) {
            Cardinal.log("&c[MatchAutoStarter] Failed to save last played map.");
            e.printStackTrace();
        }
    }

    private void loadLastPlayedMap() {
        try {
            if (Files.exists(LAST_MAP_FILE)) {
                lastPlayedMapId = Files.readString(LAST_MAP_FILE).trim();
                Cardinal.log("&7[MatchAutoStarter] Last played map: " + lastPlayedMapId);
            }
        } catch (IOException e) {
            Cardinal.log("&c[MatchAutoStarter] Failed to load last played map.");
            e.printStackTrace();
        }
    }

    public void restartMatchFlow() {
        Cardinal.log("&e[MatchAutoStarter] Manual restart triggered.");

        if (countdownTask != null) countdownTask.cancel();
        if (voteTransitionTask != null) voteTransitionTask.cancel();
        if (autoStartTask != null) autoStartTask.cancel();

        countdownStarted = false;
        voteResumedAfterPause = false;
        countdownTimeLeft = COUNTDOWN_SECONDS;

        if (matchManager.isMatchInProgress()) {
            Cardinal.log("&c[MatchAutoStarter] Match in progress — forcing to IDLE.");
            matchManager.forceEndMatch();
        }

        matchManager.setCurrentPhase(GamePhase.IDLE, null);
        if (autoStartEnabled) {
            startMonitoring();
            plugin.getServer().getScheduler()
                    .buildTask(plugin, this::checkStartConditions)
                    .delay(1, TimeUnit.SECONDS)
                    .schedule();
        }
    }

    public void handleLobbyEmptyCheck() {
        boolean isVoting = matchManager.getCurrentPhase() == GamePhase.VOTING;
        boolean isPostVoting = matchManager.getCurrentPhase() == GamePhase.POST_VOTING;

        int matchPlayers = matchManager.getOnlinePlayersInMatchServer();

        if ((isVoting || isPostVoting) && matchPlayers == 0) {
            String reason = "Players left during " + (isVoting ? "voting" : "post-voting") + ". Canceling vote.";
            Cardinal.log("&c[MatchAutoStarter] " + reason);
            notifyLobbyVoteCanceled(reason);
            cancelVotingAndReset();
        }
    }

    public boolean isAutoStartEnabled() {
        return autoStartEnabled;
    }

    public void setAutoStartEnabled(boolean enabled) {
        this.autoStartEnabled = enabled;
        try {
            Files.createDirectories(AUTO_START_FILE.getParent());
            Files.writeString(AUTO_START_FILE, Boolean.toString(enabled));
            Cardinal.log("&e[MatchAutoStarter] Auto-start set to: " + enabled);
        } catch (IOException e) {
            Cardinal.log("&c[MatchAutoStarter] Failed to save auto-start state.");
            e.printStackTrace();
        }

        if (!enabled) {
            stopMonitoring();
        } else {
            startMonitoring();
        }
    }

    private void loadAutoStartState() {
        try {
            if (Files.exists(AUTO_START_FILE)) {
                String content = Files.readString(AUTO_START_FILE).trim();
                autoStartEnabled = Boolean.parseBoolean(content);
                Cardinal.log("&7[MatchAutoStarter] Loaded auto-start state: " + autoStartEnabled);
            }
        } catch (IOException e) {
            Cardinal.log("&c[MatchAutoStarter] Failed to load auto-start state.");
            e.printStackTrace();
        }
    }

    private void notifyLobbyVoteCanceled(String reason) {
        plugin.getMessaging().notifyVoteCancelled(reason);
        // Optional: global chat echo
        plugin.getMessaging().chatGlobal("<green>[Cardinal]</green> " + reason);
    }

    /* ---- Queue helpers ---- */
    public Set<UUID> getQueuedPlayers() { return Collections.unmodifiableSet(queuedPlayers); }
    public boolean addToQueue(UUID uuid) { return queuedPlayers.add(uuid); }
    public boolean removeFromQueue(UUID uuid) { return queuedPlayers.remove(uuid); }
    public boolean isQueued(UUID uuid) { return queuedPlayers.contains(uuid); }

    public void forceStartPostVotingCountdown(MapInfo selectedMap, List<MapInfo> tiedMaps) {
        Cardinal.log("&b[MatchAutoStarter] forceStartPostVotingCountdown called!");
        Cardinal.log("&b[MatchAutoStarter] Selected map: " + (selectedMap != null ? selectedMap.getId() : "null"));
        Cardinal.log("&b[MatchAutoStarter] Tied maps: " + tiedMaps.stream().map(MapInfo::getId).toList());

        if (matchManager.isMatchInProgress()) {
            Cardinal.log("&c[MatchAutoStarter] Match already in progress — skipping countdown.");
            return;
        }

        matchManager.setCurrentPhase(GamePhase.POST_VOTING, null);

        // Build and send the same vote_results payload Hub expects
        JsonObject results = new JsonObject();
        results.addProperty("selectedMap", selectedMap.getId());
        results.addProperty("countdown", COUNTDOWN_SECONDS);
        var tiedArr = new com.google.gson.JsonArray();
        tiedMaps.stream().map(MapInfo::getId).forEach(tiedArr::add);
        results.add("tiedMaps", tiedArr);
        JsonObject namesObj = new JsonObject();
        voteManager.getCurrentOptions().forEach(m -> namesObj.addProperty(m.getId(), m.getName()));
        results.add("mapNames", namesObj);
        plugin.getMessaging().broadcastVoteResults(results);

        List<MapInfo> voteOptions = new ArrayList<>(voteManager.getCurrentOptions());
        countdownStarted = false;
        startCountdownTask(plugin.getServer().getAllPlayers(), voteOptions);
    }

    public void cancelCountdownTask() {
        if (countdownTask != null) {
            countdownTask.cancel();
            countdownTask = null;
            Cardinal.log("&c[MatchAutoStarter] Countdown task cancelled.");
        }
        countdownStarted = false;
        countdownTimeLeft = COUNTDOWN_SECONDS;
    }

    public void cancelVoteTransitionTask() {
        if (voteTransitionTask != null) {
            voteTransitionTask.cancel();
            voteTransitionTask = null;
            Cardinal.log("&c[MatchAutoStarter] Vote transition task cancelled.");
        }
    }


    // Send to ONE player using the live options from MapVoteManager
    public void sendVoteUiTo(Player p) {
        if (matchManager.getCurrentPhase() != GamePhase.VOTING) return;

        var options = voteManager.getCurrentOptions();
        if (options.isEmpty()) return;

        if (p.getCurrentServer().isEmpty()
                || !p.getCurrentServer().get().getServerInfo().getName().equalsIgnoreCase("hub")) {
            return;
        }

        p.sendMessage(Component.empty());
        p.sendMessage(Component.text("Map Voting", NamedTextColor.AQUA)
                .append(Component.text(" — click a map below to vote!", NamedTextColor.GRAY)));
        p.sendMessage(Component.empty());

        for (int i = 0; i < options.size(); i++) {
            MapInfo map = options.get(i);
            int idx = i + 1;

            Component line = Component.text("• ", NamedTextColor.GRAY)
                    .append(Component.text("[" + idx + "] ", NamedTextColor.GOLD))
                    .append(Component.text(map.getName(), NamedTextColor.GREEN))
                    .hoverEvent(HoverEvent.showText(
                            Component.text("Click to vote for ", NamedTextColor.GRAY)
                                    .append(Component.text(map.getName(), NamedTextColor.GREEN))
                    ))
                    .clickEvent(ClickEvent.runCommand("/vote " + idx));

            p.sendMessage(line);
        }

        p.sendMessage(Component.empty());
        p.sendMessage(Component.text("Tip: You can also type ", NamedTextColor.DARK_GRAY)
                .append(Component.text("/vote <number>", NamedTextColor.YELLOW)));
        p.sendMessage(Component.empty());
    }


}
