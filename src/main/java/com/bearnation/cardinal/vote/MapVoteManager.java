package com.bearnation.cardinal.vote;

import com.bearnation.cardinal.Cardinal;
import com.bearnation.cardinal.GamePhase;
import com.bearnation.cardinal.map.MapInfo;
import com.bearnation.cardinal.match.MatchAutoStarter;
import com.bearnation.cardinal.messaging.Messaging;
import com.bearnation.cardinal.bus.Bus;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.scheduler.ScheduledTask;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class MapVoteManager {
    private final Map<Integer, String> voteIndexToMapId = new HashMap<>();
    private final Map<UUID, String> playerVotes = new HashMap<>();
    private final Map<String, Integer> voteCounts = new ConcurrentHashMap<>();
    private final List<MapInfo> currentOptions = new ArrayList<>();
    private final Gson gson = new Gson();

    // 🔄 NATS facade (replaces PluginMessagingManager)
    private final Messaging messaging;

    private boolean votePaused = false;
    private long pausedTimeRemaining = 0;
    private boolean votingEnded = false;
    private MatchAutoStarter matchAutoStarter;

    private final Cardinal cardinal;
    private final ProxyServer proxy;
    private final PluginContainer plugin;

    private ScheduledTask voteTask;
    private boolean votingActive = false;
    private long voteEndTime = 0;

    public MapVoteManager(Cardinal cardinal, ProxyServer proxy, PluginContainer plugin, Messaging messaging) {
        this.cardinal = cardinal;
        this.proxy = proxy;
        this.plugin = plugin;
        this.messaging = messaging;
    }

    public void startVoting(List<MapInfo> options) {
        clearVotes();
        votingEnded = false;
        currentOptions.clear();
        currentOptions.addAll(options);
        voteIndexToMapId.clear();

        for (int i = 0; i < options.size(); i++) {
            voteIndexToMapId.put(i + 1, options.get(i).getId()); // 1-based index
            Cardinal.log("&7[Vote] #" + (i + 1) + " = " + options.get(i).getId() + " (" + options.get(i).getName() + ")");
        }

        votingActive = true;

        if (!votePaused) {
            pausedTimeRemaining = 0;
            voteEndTime = System.currentTimeMillis() + 30_000;
        }

        for (MapInfo option : options) {
            voteCounts.put(option.getId(), 0);
        }

        if (voteTask != null) {
            voteTask.cancel();
            voteTask = null;
        }

        if (!votePaused) {
            voteTask = proxy.getScheduler()
                    .buildTask(plugin, () -> {
                        if (votingActive && !votePaused) {
                            if (System.currentTimeMillis() >= voteEndTime) {
                                endVoting();
                            } else {
                                broadcastVoteCounts();
                            }
                        }
                    })
                    .repeat(1, TimeUnit.SECONDS)
                    .schedule();
        }
    }

    public void castVote(UUID playerId, String mapId, int voteIndex) {
        if (!votingActive || !voteCounts.containsKey(mapId)) return;

        String previousVote = playerVotes.get(playerId);
        if (previousVote != null && !previousVote.equalsIgnoreCase(mapId)) {
            voteCounts.put(previousVote, Math.max(0, voteCounts.getOrDefault(previousVote, 0) - 1));
        }

        playerVotes.put(playerId, mapId);
        if (!mapId.equalsIgnoreCase(previousVote)) {
            voteCounts.put(mapId, voteCounts.getOrDefault(mapId, 0) + 1);
        }

        setPlayerVote(playerId, voteIndex);

        // 🔊 Hub: who voted for which index (1-based)
        JsonObject voteMsg = new JsonObject();
        voteMsg.addProperty("uuid", playerId.toString());
        voteMsg.addProperty("voteIndex", voteIndex + 1);
        voteMsg.addProperty("mapId", mapId);

        // Publish directly (no dedicated facade method needed)
        messaging.getBus().publish(
                "bn.hub",
                Bus.env("vote_cast", "broadcast", voteMsg).toString()
        );

        broadcastVoteCounts();

        // Early-end if all queued players voted
        proxy.getScheduler().buildTask(plugin, () -> {
            if (cardinal.getMatchManager().haveAllQueuedPlayersVoted()) {
                Cardinal.log("&a[MapVoteManager] All queued players have voted! Ending vote early...");
                for (Player player : proxy.getAllPlayers()) {
                    player.getCurrentServer().ifPresent(server -> {
                        if (server.getServerInfo().getName().equalsIgnoreCase("hub")) {
                            player.sendMessage(Component.text("§a[Cardinal]§e Everybody voted, voting phase ending early..."));
                        }
                    });
                }
                endVoting();
            }
        }).schedule();
    }

    public void setPlayerVote(UUID playerId, int index) {
        if (!votingActive || index < 0 || index >= currentOptions.size()) return;
        String selectedMapId = currentOptions.get(index).getId();
        playerVotes.put(playerId, selectedMapId);
    }

    public void removePlayerVote(UUID playerId) {
        if (!votingActive) return;
        String previousVote = playerVotes.remove(playerId);
        if (previousVote != null && voteCounts.containsKey(previousVote)) {
            voteCounts.put(previousVote, Math.max(0, voteCounts.get(previousVote) - 1));
            Cardinal.log("[MapVoteManager] Removed vote from " + playerId);
            broadcastVoteCounts();
        }
    }

    public boolean hasPlayerVoted(UUID uuid) { return playerVotes.containsKey(uuid); }

    public int getPlayerVoteIndex(UUID playerId) {
        String mapId = playerVotes.get(playerId);
        if (mapId == null) return -1;
        for (int i = 0; i < currentOptions.size(); i++) {
            if (currentOptions.get(i).getId().equalsIgnoreCase(mapId)) return i + 1;
        }
        return -1;
    }

    public Optional<MapInfo> getWinner() {
        if (currentOptions.isEmpty()) return Optional.empty();

        int maxVotes = currentOptions.stream()
                .mapToInt(m -> voteCounts.getOrDefault(m.getId(), 0))
                .max()
                .orElse(-1);

        List<MapInfo> tied = currentOptions.stream()
                .filter(m -> voteCounts.getOrDefault(m.getId(), 0) == maxVotes)
                .toList();

        if (tied.isEmpty()) return Optional.empty();
        return Optional.of(tied.get(new Random().nextInt(tied.size())));
    }

    public void endVoting() {
        votingActive = false;
        voteEndTime = 0;
        if (votingEnded) return;
        votingEnded = true;

        if (voteTask != null) {
            voteTask.cancel();
            voteTask = null;
        }

        int maxVotes = voteCounts.values().stream().max(Integer::compareTo).orElse(0);
        List<MapInfo> topMaps = currentOptions.stream()
                .filter(m -> voteCounts.getOrDefault(m.getId(), 0) == maxVotes)
                .toList();

        MapInfo selectedMap;
        List<MapInfo> tiedMaps = new ArrayList<>();

        if (!topMaps.isEmpty()) {
            if (topMaps.size() == 1) {
                selectedMap = topMaps.get(0);
            } else {
                tiedMaps = topMaps;
                selectedMap = topMaps.get(new Random().nextInt(topMaps.size()));
            }
        } else {
            selectedMap = currentOptions.get(new Random().nextInt(currentOptions.size()));
            Cardinal.log("[MapVoteManager] No votes cast, randomly selected: " + selectedMap.getName());
        }

        // Hub chat toast
        for (Player player : proxy.getAllPlayers()) {
            player.getCurrentServer().ifPresent(server -> {
                if (server.getServerInfo().getName().equalsIgnoreCase("hub")) {
                    player.sendMessage(Component.text("§a[Cardinal]§e The Vote is over..."));
                    player.sendMessage(Component.text("§a[Cardinal]§e The next Map is §a" + selectedMap.getName()));
                    player.sendMessage(Component.text("§a[Cardinal]§e Match will start in §a10s"));
                }
            });
        }

        // Update phase
        cardinal.getMatchManager().setCurrentPhase(GamePhase.POST_VOTING, null);

        // Build results payload
        JsonObject obj = new JsonObject();
        obj.addProperty("selectedMap", selectedMap.getId());
        obj.addProperty("countdown", 10);

        JsonArray tiedArray = new JsonArray();
        for (MapInfo tied : tiedMaps) tiedArray.add(tied.getId());
        obj.add("tiedMaps", tiedArray);

        JsonObject namesObj = new JsonObject();
        for (MapInfo map : currentOptions) namesObj.addProperty(map.getId(), map.getName());
        obj.add("mapNames", namesObj);

        // 🔊 Hub: final results
        messaging.broadcastVoteResults(obj);

        String logMessage = tiedMaps.isEmpty()
                ? "Voting ended! Winning map: " + selectedMap.getName()
                : "Voting ended in a tie! Randomly selected: " + selectedMap.getName();
        Cardinal.log("[MapVoteManager] " + logMessage + " Match starting in 10s.");

        if (cardinal.getAutoStarter() != null) {
            Cardinal.log("&b[MapVoteManager] Triggering forceStartPostVotingCountdown...");
            cardinal.getAutoStarter().forceStartPostVotingCountdown(selectedMap, tiedMaps);
        }
    }

    public void cancelVoting() {
        Cardinal.log("[MapVoteManager] Vote canceled.");
        votingActive = false;
        voteEndTime = 0;
        if (voteTask != null) voteTask.cancel();
        clearVotes();
        currentOptions.clear();

        // 🔊 Hub: tell it why, and it can clear UI on its side.
        messaging.notifyVoteCancelled("Voting cancelled.");
    }

    public void clearVotes() {
        playerVotes.clear();
        voteCounts.clear();
        broadcastVoteCounts();
    }

    public boolean hasVotes() { return !playerVotes.isEmpty(); }
    public boolean isVotingActive() { return votingActive; }
    public Map<String, Integer> getVoteCounts() { return Collections.unmodifiableMap(voteCounts); }
    public List<MapInfo> getCurrentOptions() { return Collections.unmodifiableList(currentOptions); }

    public int getTimeLeftSeconds() {
        if (!votingActive) return 0;
        if (votePaused) return (int) pausedTimeRemaining;
        return Math.max(0, (int) ((voteEndTime - System.currentTimeMillis()) / 1000));
    }

    private void broadcastVoteCounts() {
        JsonObject data = new JsonObject();

        Map<String, Integer> voteSnapshot = new HashMap<>(voteCounts);
        data.add("counts", gson.toJsonTree(voteSnapshot));
        data.addProperty("timeLeft", getTimeLeftSeconds());

        JsonObject names = new JsonObject();
        for (MapInfo map : currentOptions) names.addProperty(map.getId(), map.getName());
        data.add("mapNames", names);

        JsonArray order = new JsonArray();
        for (MapInfo map : currentOptions) order.add(map.getId());
        data.add("mapOrder", order);

        data.addProperty("paused", votePaused);

        // 🔊 Hub: live counts
        messaging.broadcastVoteCounts(data);
    }

    public List<MapInfo> getTiedMaps(MapInfo selected) {
        if (selected == null) return List.of();

        int maxVotes = voteCounts.getOrDefault(selected.getId(), 0);
        return currentOptions.stream()
                .filter(m -> voteCounts.getOrDefault(m.getId(), 0) == maxVotes)
                .toList();
    }

    public String getPlayerVote(UUID uuid) {
        return playerVotes.getOrDefault(uuid, null);
    }

    public Optional<String> getMapIdFromVoteIndex(int voteIndex) {
        return Optional.ofNullable(voteIndexToMapId.get(voteIndex));
    }

    public boolean isVotePaused() { return votePaused; }

    public boolean pauseVoting() {
        if (!votingActive || votePaused) return false;

        votePaused = true;
        pausedTimeRemaining = getTimeLeftSeconds();
        if (voteTask != null) {
            voteTask.cancel();
            voteTask = null;
        }

        // 🔊 Hub: pause flag (event + next counts tick will include paused=true)
        messaging.getBus().publish(
                "bn.hub",
                Bus.env("vote_paused", "broadcast", new JsonObject()).toString()
        );

        Cardinal.log("[MapVoteManager] Voting paused with " + pausedTimeRemaining + "s remaining.");
        return true;
    }

    public boolean resumeVoting() {
        if (!votingActive || !votePaused) return false;

        votePaused = false;
        voteEndTime = System.currentTimeMillis() + (pausedTimeRemaining * 1000L);
        pausedTimeRemaining = 0;

        if (voteTask != null) voteTask.cancel();

        voteTask = proxy.getScheduler()
                .buildTask(plugin, () -> {
                    if (votingActive && !votePaused) {
                        if (System.currentTimeMillis() >= voteEndTime) {
                            endVoting();
                        } else {
                            broadcastVoteCounts();
                        }
                    }
                })
                .repeat(1, TimeUnit.SECONDS)
                .schedule();

        // 🔊 Hub: tell clients to clear “paused” state
        messaging.notifyVoteResumed();
        Cardinal.log("[MapVoteManager] Voting resumed.");
        return true;
    }

    public void resetPauseState() {
        votePaused = false;
        pausedTimeRemaining = 0;
    }

    public String getCurrentVoteStateJson() {
        JsonObject obj = new JsonObject();

        JsonObject counts = new JsonObject();
        for (Map.Entry<String, Integer> entry : voteCounts.entrySet()) {
            counts.addProperty(entry.getKey(), entry.getValue());
        }
        obj.add("counts", counts);

        JsonArray order = new JsonArray();
        for (MapInfo info : currentOptions) order.add(info.getId());
        obj.add("mapOrder", order);

        JsonObject mapNames = new JsonObject();
        for (MapInfo info : currentOptions) mapNames.addProperty(info.getId(), info.getName());
        obj.add("mapNames", mapNames);

        int timeLeft = votePaused
                ? (int) pausedTimeRemaining
                : (int) Math.max((voteEndTime - System.currentTimeMillis()) / 1000L, 0);
        obj.addProperty("timeLeft", timeLeft);

        obj.addProperty("paused", votePaused);
        return obj.toString();
    }

    public void setMatchAutoStarter(MatchAutoStarter matchAutoStarter) {
        this.matchAutoStarter = matchAutoStarter;
    }


}
