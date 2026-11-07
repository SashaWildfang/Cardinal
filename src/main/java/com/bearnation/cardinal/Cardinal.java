package com.bearnation.cardinal;

import com.bearnation.cardinal.bus.MessageBus;
import com.bearnation.cardinal.bus.NatsBus;
import com.bearnation.cardinal.chat.ChatManager;
import com.bearnation.cardinal.command.*;
import com.bearnation.cardinal.database.DatabaseManager;
import com.bearnation.cardinal.gamemode.GameModeRegistry;
import com.bearnation.cardinal.help.HelpAggregator;
import com.bearnation.cardinal.listener.LuckPermsListener;
import com.bearnation.cardinal.listener.ServerListListener;
import com.bearnation.cardinal.map.MapManager;
import com.bearnation.cardinal.match.MatchAutoStarter;
import com.bearnation.cardinal.match.MatchManager;
import com.bearnation.cardinal.messaging.Messaging;
import com.bearnation.cardinal.messaging.RegionSetupMessenger;
import com.bearnation.cardinal.proxy.ProxySubscriber;
import com.bearnation.cardinal.teams.TeamManager;
import com.bearnation.cardinal.util.LuckPermsUtil;
import com.bearnation.cardinal.vote.MapVoteManager;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Dependency;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.event.EventSubscription;
import net.luckperms.api.event.node.NodeMutateEvent;
import net.luckperms.api.event.user.UserDataRecalculateEvent;
import org.tomlj.Toml;
import org.tomlj.TomlParseResult;
import org.slf4j.Logger;
import com.bearnation.cardinal.bus.Bus; // <- add this

import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@Plugin(
        id = "cardinal",
        name = "Cardinal",
        version = "1.0.0",
        authors = {"DetectivePlapper"},
        dependencies = { @Dependency(id = "luckperms") }
)
public class Cardinal {

    private static final LegacyComponentSerializer legacy = LegacyComponentSerializer.legacyAmpersand();
    public static final MiniMessage mm = MiniMessage.miniMessage();
    private final Logger logger;
    public static Cardinal instance;

    private final ProxyServer server;
    private final PluginContainer plugin;
    private final Path dataDirectory;

    // Managers
    private final TeamManager teamManager = new TeamManager();
    private ChatManager chatManager;
    private MatchManager matchManager;
    private MatchAutoStarter autoStarter;
    private MapVoteManager voteManager;
    private RegionSetupMessenger regionSetupMessenger;

    // Messaging over NATS
    private MessageBus bus;
    private Messaging messaging;

    // LuckPerms
    private LuckPerms luckPerms;
    private LuckPermsUtil luckPermsUtil;
    private EventSubscription<UserDataRecalculateEvent> userMetaSubscription;

    // Commands with state
    private final AutoQueueCommand autoQueueCommand = new AutoQueueCommand();

    private GamePhase phase = GamePhase.IDLE;

    public static Cardinal getInstance() { return instance; }

    @Inject
    public Cardinal(ProxyServer server, PluginContainer plugin, @DataDirectory Path dataDirectory, Logger logger) {
        this.server = server;
        this.plugin = plugin;
        this.dataDirectory = dataDirectory;
        this.logger = logger;
        instance = this;

        // Ensure maps dir + default example
        Path mapsPath = dataDirectory.resolve("maps");
        try {
            if (!Files.exists(mapsPath)) {
                Files.createDirectories(mapsPath);
                Cardinal.log("&7Created missing maps directory at: " + mapsPath.toAbsolutePath());
            }
            try (var stream = Files.list(mapsPath)) {
                if (stream.findAny().isEmpty()) {
                    createMap(mapsPath, "1", "Castle", "DTW", 2);
                    Cardinal.log("&aGenerated example map");
                }
            }
        } catch (IOException e) {
            Cardinal.log("&cFailed to create maps directory or default maps.");
            e.printStackTrace();
        }

        MapManager.getInstance().loadMaps(mapsPath);
        Cardinal.log("&a[Cardinal] Loaded " + MapManager.getInstance().getMaps().size() + " maps.");
    }

    @Subscribe
    public void onProxyInit(ProxyInitializeEvent event) {
        // --- Load config (DB + NATS URL + CTF target) ---
        Path configPath = dataDirectory.resolve("config.toml");
        TomlParseResult config;
        try {
            config = Toml.parse(configPath);
        } catch (IOException e) {
            Cardinal.log("&c[Cardinal] Failed to read config.toml: " + e.getMessage());
            return;
        }
        if (config.hasErrors()) {
            Cardinal.log("&cFailed to load config.toml:");
            config.errors().forEach(err -> Cardinal.log("&c" + err));
            return;
        }

        // DB init
        DatabaseManager.initialize(
                config.getString("database.host"),
                config.getLong("database.port").intValue(),
                config.getString("database.name"),
                config.getString("database.user"),
                config.getString("database.password")
        );

        // --- NATS connect ---
        String natsUrl = config.getString("nats.url");
        if (natsUrl == null || natsUrl.isBlank()) {
            Cardinal.log("&c[Cardinal] nats.url is not set in config.toml (remember: $ => %24 in passwords)");
            return;
        }
        try {
            bus = new NatsBus(natsUrl);
        } catch (Exception e) {
            Cardinal.log("&c[Cardinal] NATS connect failed: " + e.getMessage());
            return;
        }

        // If you shard CTF, set a target (null => broadcast)
        String ctfTarget = config.getString("ctf.target"); // e.g. "ctf-1"
        messaging = new Messaging(bus, ctfTarget);
        new ProxySubscriber(server, bus, logger).subscribe();

        // Let DB trigger Hub stat refreshes via NATS
        DatabaseManager.setMessaging(messaging);

        // Managers that depend on messaging
        matchManager = new MatchManager(teamManager);
        matchManager.setMessaging(messaging);

        voteManager = new MapVoteManager(this, server, plugin, messaging);
        autoStarter = new MatchAutoStarter(this, matchManager, voteManager);
        voteManager.setMatchAutoStarter(autoStarter);


        var playtimeService = new com.bearnation.cardinal.playtime.PlaytimeService(this);
        server.getEventManager().register(this, playtimeService);

        try {
            messaging.broadcastAutoStartStatus(autoStarter.isAutoStartEnabled());
        } catch (Throwable t) {
            Cardinal.log("&c[Cardinal] Failed to broadcast autostart status on init: " + t.getMessage());
        }

        bus.subscribe("bn.proxy", msg -> {
            try {
                JsonObject env = JsonParser.parseString(msg).getAsJsonObject();
                String type = env.has("type") ? env.get("type").getAsString() : "";
                if ("autostart_status_request".equalsIgnoreCase(type)) {
                    messaging.broadcastAutoStartStatus(autoStarter.isAutoStartEnabled());
                }
            } catch (Exception e) {
                Cardinal.log("&c[Cardinal] Failed to handle bn.proxy message: " + e.getMessage());
            }
        });

        regionSetupMessenger = new RegionSetupMessenger(messaging);

        chatManager = new ChatManager(this);

        // LuckPerms
        try {
            this.luckPerms = LuckPermsProvider.get();
            this.luckPermsUtil = new LuckPermsUtil(this.luckPerms);

            LuckPermsListener lpListener = new LuckPermsListener(this, this.luckPerms, luckPermsUtil);
            this.luckPerms.getEventBus().subscribe(this, UserDataRecalculateEvent.class, lpListener::onUserMetaUpdate);
            this.luckPerms.getEventBus().subscribe(this, NodeMutateEvent.class, lpListener::onNodeMutate);
            this.luckPerms.getEventBus().subscribe(this, net.luckperms.api.event.group.GroupDataRecalculateEvent.class, lpListener::onGroupDataChange);

            Cardinal.log("&a[Cardinal] LuckPerms hooked and metadata listeners registered.");
        } catch (IllegalStateException e) {
            Cardinal.log("&c[Cardinal] Failed to hook into LuckPerms: " + e.getMessage());
        }

        server.getEventManager().register(this, new ServerListListener(server));

        // Start monitoring
        autoStarter.startMonitoring();


        HelpAggregator aggregator = new HelpAggregator(server, bus /* your MessageBus instance */);
        var help = new AutoHelpCommand(server, aggregator);
        var meta = server.getCommandManager().metaBuilder("help").aliases("?", "h").build();
        server.getCommandManager().register(meta, help);

        // Commands
        CommandManager cm = server.getCommandManager();
        cm.register(cm.metaBuilder("startmatch").plugin(this).build(), new StartMatchCommand(server, matchManager));
        cm.register(cm.metaBuilder("maps").plugin(this).build(), new MapsCommand());
        cm.register(cm.metaBuilder("join").plugin(this).build(), new JoinCommand(matchManager));
        cm.register(cm.metaBuilder("teamjoin").plugin(this).build(), new TeamJoinCommand(teamManager));
        cm.register(cm.metaBuilder("vote").plugin(this).build(), new VoteCommand(voteManager));
        cm.register(cm.metaBuilder("cardinal").plugin(this).build(), new CardinalCommand(matchManager, voteManager));
        cm.register(cm.metaBuilder("hub").plugin(this).build(), new HubCommand(this));
        cm.register(cm.metaBuilder("send").plugin(this).build(), new SendCommand(this));
        cm.register(cm.metaBuilder("whereami").plugin(this).build(), new WhereAmICommand());
        cm.register(cm.metaBuilder("alert").plugin(this).build(), new AlertCommand(this));
        cm.register(cm.metaBuilder("chat").plugin(this).build(), new ChatCommand(this));
        cm.register(cm.metaBuilder("channel").plugin(this).build(), new ChatCommand(this));
        cm.register(cm.metaBuilder("staffchat").plugin(this).build(), new StaffChatCommand(this));
        cm.register(cm.metaBuilder("sc").plugin(this).build(), new StaffChatCommand(this));

        // Coins commands: use the version that relies on Messaging (not PluginMessagingManager)
        cm.register(cm.metaBuilder("coins").plugin(this).build(), new CoinsCommand(server, messaging));
        cm.register(cm.metaBuilder("balance").plugin(this).build(), new CoinsCommand(server, messaging));
        cm.register(cm.metaBuilder("coinsadmin").plugin(this).build(), new CoinsCommand(server, messaging));

        cm.register(cm.metaBuilder("phase").plugin(this).build(), new PhaseCommand(matchManager));
        cm.register(cm.metaBuilder("setphase").plugin(this).build(), new SetPhaseCommand(matchManager));
        cm.register(cm.metaBuilder("find").plugin(this).build(), new FindCommand(this));
        cm.register(cm.metaBuilder("forcestart").plugin(this).build(), new ForceStartCommand(matchManager));
        cm.register(cm.metaBuilder("getvotes").plugin(this).build(), new GetVotesCommand(this.getMapVoteManager()));
        cm.register(cm.metaBuilder("msg").plugin(this).aliases("message", "tell", "w").build(), new MsgCommand(this, this.getLuckPermsUtil()));
        cm.register(cm.metaBuilder("r").plugin(this).build(), new ReplyCommand(this, this.getLuckPermsUtil()));
        cm.register(cm.metaBuilder("ignore").plugin(this).build(), new IgnoreCommand(this.getServer()));
        cm.register(cm.metaBuilder("unignore").plugin(this).build(), new UnignoreCommand(this.getServer()));
        cm.register(cm.metaBuilder("ignorelist").plugin(this).build(), new IgnoreListCommand(this.getServer()));
        cm.register(cm.metaBuilder("spy").plugin(this).aliases("ss", "socialspy").build(), new SpyCommand());
        cm.register(cm.metaBuilder("discord").plugin(this).build(), new DiscordCommand());
        cm.register(cm.metaBuilder("store").plugin(this).build(), new StoreCommand());
        cm.register(cm.metaBuilder("restartgame").plugin(this).build(), new RestartGameCommand());
        cm.register(cm.metaBuilder("pausevote").plugin(this).build(), new PauseVoteCommand());
        cm.register(cm.metaBuilder("resumevote").plugin(this).build(), new ResumeVoteCommand());
        cm.register(cm.metaBuilder("stopvote").plugin(this).build(), new StopVoteCommand(voteManager, autoStarter));
        cm.register(cm.metaBuilder("autostart").plugin(this).build(), new AutoStartCommand(this));
        cm.register(cm.metaBuilder("sendall").plugin(this).build(), new SendAllCommand(this));
        cm.register(cm.metaBuilder("queue").plugin(this).build(), new QueueCommand(matchManager));
        cm.register(cm.metaBuilder("queuegui").plugin(this).build(), QueueGuiBrigadier.create(matchManager));
        cm.register(cm.metaBuilder("autoqueue").plugin(this).build(), autoQueueCommand);
        cm.register(cm.metaBuilder("joinqueue").plugin(this).build(), new JoinQueueCommand(matchManager));
        cm.register(cm.metaBuilder("leavequeue").plugin(this).build(), new LeaveQueueCommand(matchManager));
        cm.register(cm.metaBuilder("staff").plugin(this).build(), new StaffCommand(this, luckPerms));
        cm.register(cm.metaBuilder("list").plugin(this).build(), new ListCommand(getProxy()));
        cm.register(cm.metaBuilder("mapinfo").plugin(this).build(), new MapInfoCommand());
        cm.register(cm.metaBuilder("playtime").plugin(this).aliases("pt","ptime").build(), new com.bearnation.cardinal.command.PlaytimeCommand(this, playtimeService));
        GameModeRegistry.registerDefaults(server);

        Cardinal.log("&a[Cardinal] Plugin loaded (NATS online).");
    }

    private void createMap(Path basePath, String id, String name, String gamemode, int requiredPlayers) {
        Path mapDir = basePath.resolve(gamemode.toUpperCase()).resolve(name);
        try {
            Files.createDirectories(mapDir);
            Path config = mapDir.resolve("config.yml");

            Files.writeString(config, String.format("""
        id: "%s"
        name: "%s"
        author: "JustADev"
        date_added: "2025-07-27"
        gamemode: "%s"
        schematic: "%s.schem"

        paste_x: 0
        paste_y: 64
        paste_z: 0

        supported_modes:
          - %s
        required_players: %d

        region_bounds:
          spawn_red:
            world: world
            minX: 0
            minY: 64
            minZ: 0
            maxX: 10
            maxY: 80
            maxZ: 10
          spawn_blue:
            world: world
            minX: 100
            minY: 64
            minZ: 100
            maxX: 110
            maxY: 80
            maxZ: 110
          flag_red:
            world: world
            minX: 5
            minY: 64
            minZ: 5
            maxX: 6
            maxY: 65
            maxZ: 6
          flag_blue:
            world: world
            minX: 105
            minY: 64
            minZ: 105
            maxX: 106
            maxY: 65
            maxZ: 106

        teams:
          red:
            spawn_region: "spawn_red"
            flag_region: "flag_red"
            flag_spawn:
              world: world
              x: 5.5
              y: 65
              z: 5.5
          blue:
            spawn_region: "spawn_blue"
            flag_region: "flag_blue"
            flag_spawn:
              world: world
              x: 105.5
              y: 65
              z: 105.5

        flags:
          spawn_red:
            pvp: false
            greeting: "&cWelcome to Red Spawn"
          spawn_blue:
            pvp: false
            greeting: "&9Welcome to Blue Spawn"
        """, id, name.toLowerCase(), gamemode, id, gamemode, requiredPlayers));

            Cardinal.log("&7Created default map config for: " + id);
        } catch (IOException e) {
            Cardinal.log("&cFailed to create map " + id + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Subscribe
    public void onServerConnected(ServerConnectedEvent event) {
        Player player = event.getPlayer();
        var target = event.getServer();

        if (target.getServerInfo().getName().equalsIgnoreCase("hub")) {
            if (autoQueueCommand.isAutoQueueEnabled(player.getUniqueId())) {
                matchManager.playerJoinLobby(player);
                autoStarter.addToQueue(player.getUniqueId());
                Cardinal.log("&a[Cardinal] &fAdded &e" + player.getUsername() + " &7to queue via AutoQueue");
            } else {
                Cardinal.log("&7[AutoQueue] " + player.getUsername() + " has AutoQueue OFF.");
            }

            autoStarter.handleLobbyEmptyCheck();

            // 🔽 NEW: if they're in the queue AND we're already in VOTING, show clickable vote UI
            if (matchManager.getCurrentPhase() == GamePhase.VOTING
                    && autoStarter.isQueued(player.getUniqueId())) {
                autoStarter.sendVoteUiTo(player);
            }
        }
    }


    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        matchManager.playerLeaveLobby(player);
        autoStarter.handleLobbyEmptyCheck();

        if (voteManager.hasPlayerVoted(uuid)) {
            voteManager.removePlayerVote(uuid);
            Cardinal.log("&c[Vote] &e" + player.getUsername() + " &7left, removing their vote.");
        }

        Cardinal.log("&a[Cardinal] &fRemoved &e" + player.getUsername() + " &7from lobby queue");
    }

    public GamePhase getPhase() { return matchManager.getCurrentPhase(); }

    public void setPhase(GamePhase newPhase) {
        this.phase = newPhase;
        // Publish to Hub + CTF directly via NATS
        messaging.updatePhaseForHub(newPhase.name());
        messaging.updatePhaseForCtf(newPhase.name());
        Cardinal.log("&b[Cardinal] Phase set to " + newPhase.name());
    }

    public static Component component(String legacyMessage) { return legacy.deserialize(legacyMessage); }
    public static Component color(String message) { return legacy.deserialize(message); }
    public static void log(String message) { instance.server.getConsoleCommandSource().sendMessage(legacy.deserialize(message)); }

    public ProxyServer getServer() { return server; }
    public MatchManager getMatchManager() { return matchManager; }
    public TeamManager getTeamManager() { return teamManager; }
    public MapVoteManager getMapVoteManager() { return voteManager; }
    public MatchAutoStarter getAutoStarter() { return autoStarter; }
    public LuckPerms getLuckPerms() { return luckPerms; }
    public LuckPermsUtil getLuckPermsUtil() { return luckPermsUtil; }
    public Path getDataDirectory() { return dataDirectory; }
    public ChatManager getChatManager() { return chatManager; }
    public MapManager getMapManager() { return MapManager.getInstance(); }
    public ProxyServer getProxy() { return server; }
    public static LegacyComponentSerializer legacy() { return legacy; }
    public AutoQueueCommand getAutoQueueCommand() { return autoQueueCommand; }
    public RegionSetupMessenger getRegionSetupMessenger() { return regionSetupMessenger; }
    public Messaging getMessaging() { return messaging; }
}
