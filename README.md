# Cardinal

The **Velocity proxy plugin** behind the **Bear Nation** minigame network. Cardinal runs matchmaking, queues, map voting, game phases, cross-server chat and staff tools. It talks to the Hub and game servers over **NATS**.

![Velocity](https://img.shields.io/badge/Velocity-3.4-1197d1) ![Java](https://img.shields.io/badge/Java-17-orange) ![Build](https://img.shields.io/badge/build-Gradle-02303A)

## Architecture

```
            ┌──────────────┐
 players ──▶│   Velocity   │  Cardinal (this plugin)
            │    proxy     │  queues · votes · match state · chat
            └──────┬───────┘
                   │ NATS message bus
        ┌──────────┼───────────┐
        ▼          ▼           ▼
     ┌─────┐   ┌───────┐   ┌───────┐
     │ Hub │   │ CTF-1 │   │ CTF-n │   Paper servers
     └─────┘   └───────┘   └───────┘
                   │
                MySQL (stats, coins, playtime)
```

## Features

- **Match lifecycle**: queueing, auto-start, force start, and phase control (`GamePhase`)
- **Game modes**: Capture the Flag, Four-Team CTF, Destroy the Wool, King of the Hill
- **Map voting**, with pause, resume and stop controls
- **Map registry** with region bounds, sent to game servers for arena setup
- **Cross-server chat**: channels, staff chat, private messages, ignore lists and social spy
- **Player tools**: `/hub`, `/find`, `/whereami`, `/playtime`, `/coins`, `/list`
- **Staff tools**: `/alert`, `/send`, `/sendall`, `/restartgame`, `/reload`
- **Help aggregation** across every backend server
- **LuckPerms** integration and a custom server-list (MOTD) listener

## Configuration

`config.toml` in the plugin's data folder:

```toml
[database]
host = "localhost"
port = 3306
name = "bearnation"
user = ""
password = ""

[nats]
url = "nats://user:password@host:4222"   # encode '$' in passwords as %24

[ctf]
target = "ctf-1"   # optional: route CTF messages to a single shard
```

## Requirements

- Velocity 3.4+
- Java 17+
- A NATS server
- MySQL / MariaDB
- [LuckPerms](https://luckperms.net/)

## Building

```bash
gradle build
```

The plugin jar goes to `build/libs/`.

## Project layout

```
src/main/java/com/bearnation/cardinal/
├── Cardinal.java        # Plugin entry point
├── bus/                 # NATS message bus
├── chat/                # Channels and chat routing
├── command/             # ~50 proxy commands
├── database/            # MySQL access
├── gamemode/            # Game mode definitions and registry
├── map/                 # Map metadata and regions
├── match/               # Match state and auto-starter
├── messaging/           # Typed messages to backend servers
├── vote/                # Map voting
└── ...                  # help, playtime, teams, utils
```

## Related

- [Hub](https://github.com/SashaWildfang/Hub): the lobby server plugin
- [CTF](https://github.com/SashaWildfang/CTF): the game server plugin

---

Part of the Bear Nation plugin suite.
