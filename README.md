# PhantomPlayers

PhantomPlayers is a configuration-driven fake-player population plugin for Paper and Folia. It creates packet-only virtual players that appear in the global TAB list and as entities to nearby players, without creating real server-side player connections.

The plugin is designed for servers that want a populated-looking world with minimal overhead: identities are deterministic and persistent, names are selected fairly from `names.txt`, skins are fetched and cached from Mojang, and simulated conversations are shared across the population.

## Compatibility

| Platform | Version | Build verification | Runtime verification |
|---|---|---|---|
| Paper | 1.21.11 | Compiles against `paper-api:1.21.11-R0.1-SNAPSHOT` | Not tested on a live server |
| Folia | 1.21.11 | Compiles against `folia-api:1.21.11-R0.1-SNAPSHOT` | Not tested on a live server |
| Paper | 26.3 | Compiles against `paper-api:26.3.build.206-stable` | Not tested on a live server |
| Folia | 26.3 | Compiles against `folia-api:26.3.build.3-alpha` | Not tested on a live server |

- The jar is compiled to Java 21 bytecode. Minecraft 26.3 servers require Java 25 at runtime; Java 21 is sufficient for 1.21.11 servers.
- `api-version` is declared as `1.21`. Plugin loading on 26.3 is expected but was not runtime-tested.
- Older versions are not supported. `1.21.11` is the minimum because the plugin uses `PaperServerListPingEvent#getListedPlayers()`.
- The packet adapter is reflection-based and probes the server at startup. On an unverified version it can still try the closest known mojmap layout when `compatibility.allow-untested-adapters: true`.

## Installation

1. Build or obtain `PhantomPlayers-1.0.0.jar`.
2. Place it in the server's `plugins/` directory.
3. Start the server. The plugin creates:
   - `plugins/PhantomPlayers/config.yml`
   - `plugins/PhantomPlayers/chat.yml`
   - `plugins/PhantomPlayers/names.txt`
   - `plugins/PhantomPlayers/players.yml` (saved identities)
   - `plugins/PhantomPlayers/cache/profiles.yml` (skin cache)
4. Run `/fp spawnall` to populate the server, or `/fp spawn <name>` for one identity.

The plugin does **not** spawn anyone automatically on startup. Active fake players do not survive a restart; saved identities do, and `/fp spawnall` can reuse them.

## Commands

| Command | Description | Permission |
|---|---|---|
| `/fp spawn <name>` | Spawn one fake player with a known or new identity | `phantomplayers.spawn` |
| `/fp spawnall` | Spawn up to `population.max-active` identities in batches | `phantomplayers.spawn` |
| `/fp despawn <name>` | Remove one active fake player | `phantomplayers.despawn` |
| `/fp despawnall` | Remove every active fake player | `phantomplayers.despawnall` |
| `/fp list [page]` | Show population, skin, chat, platform, and adapter statistics | `phantomplayers.list` |
| `/fp reload` | Atomically reload config, chat templates, and names | `phantomplayers.reload` |

`phantomplayers.admin` grants all of the permissions above. All permissions default to operators.

Tab completion is provided for subcommands, spawnable identities, active names, and list pages.

## Configuration

### `config.yml`

Key sections:

- `population`
  - `max-active`: hard cap on active fake players (default `100`).
  - `spawn-batch-size` / `spawn-batch-delay-ticks`: batching for `spawnall` (default 10 identities every 20 ticks).
  - `recent-name-memory`: recently used names to avoid when alternatives exist (default `50`).
  - `spawn-spread`: horizontal spread around the origin for `spawnall`.
  - `console-spawn`: world and coordinates used when spawning from the console.
- `presentation`
  - `tab-list`: whether fake players appear in the global TAB list.
  - `entity-view-distance`: same-world visibility distance for virtual entities (default `96`).
  - `sync-interval-ticks`: viewer reconciliation interval (default `200` ticks).
  - `skin-layers`: send skin-layer metadata when supported.
  - `gravity`: simulate a fall to the surface at spawn using teleport packets (default `true`). Fake players are packet-only and have no real physics, so the fall is an animation; set to `false` to keep them exactly at the spawn point.
- `profiles`
  - `enabled`: enable Mojang username/UUID and skin lookup.
  - `cache-ttl-hours`, `negative-cache-minutes`, `request-timeout-seconds`, `max-concurrent-requests`.
  - `fallback-skin`: optional signed texture value/signature used when a lookup fails.
- `server-status`
  - `enabled`: modify the server-list ping.
  - `count-mode`: `active`, `configured`, or `disabled`.
  - `sample.enabled`, `sample.include-real-players`, `sample.max-entries` (default `12`).
- `chat`
  - `delivery`: only `broadcast` is supported.
  - `broadcast-to-console`: mirror simulated lines to the console.
- `compatibility`
  - `allow-untested-adapters`: if `false`, refuse to start on versions other than the verified targets.

Reloads are atomic: if either `config.yml` or `chat.yml` has a validation error, no changes are applied. `names.txt` warnings (invalid or duplicate names) are non-fatal. Missing default files are recreated; existing files are never overwritten.

### `chat.yml`

Conversation templates are global, not per-player. Each template has a `weight` and a list of lines. Placeholders `{player1}`, `{player2}`, and so on are resolved to active fake players; if fewer players are active than the template needs, names are cycled.

Settings include:

- `enabled`
- `min-delay-seconds` / `max-delay-seconds`
- `conversation-cooldown-seconds`
- `max-concurrent-conversations`
- `max-lines-per-conversation`
- delay randomization, consecutive-speaker avoidance, and template-repeat avoidance

A conversation aborts safely if a required speaker despawns. Chat idles when no real audience is online.

### `names.txt`

One username per line; blank lines and lines starting with `#` are ignored. Names must match `[A-Za-z0-9_]{3,16}`. The bundled file contains 600 fictional names. Duplicate and invalid entries are skipped with a warning.

## Behavior Notes

- **Packet-only players.** Fake players are not real connections. They do not appear in `/list`, do not count as real players for other plugins, have no AI, and cannot be interacted with like real players.
- **Simulated gravity.** With `presentation.gravity: true`, a new fake player falls from the spawn point to the highest motion-blocking surface in its column. The fall is drawn with teleport packets in a straight line with no block collision; it is appearance only, not server-side physics.
- **Global TAB, local entities.** TAB entries are sent to every viewer. Entity spawn/remove packets are only sent to viewers in the same world within `entity-view-distance`.
- **Deterministic identities.** Each name receives a stable UUID derived from `PhantomPlayers:<name>`, persisted in `players.yml`. Restarting the server does not restore the active population.
- **Skin refresh.** Skins resolve asynchronously at spawn time. When a texture arrives, TAB entries and nearby entity views are refreshed.
- **Status ping.** The configured count and sample are applied through the Paper server-list ping event. Real players can be preserved in the sample.
- **Threading.** Global work uses the global region scheduler; location-bound work uses the region scheduler; identity/file work uses the async scheduler.

## Skins and Network Access

When `profiles.enabled` is `true`, the plugin sends requested usernames to Mojang's public HTTP APIs:

- `api.mojang.com/users/profiles/minecraft/<name>`
- `sessionserver.mojang.com/session/minecraft/profile/<uuid>`

No credentials are required. Successful and negative results are cached locally. Disable `profiles.enabled` if the server must not make these outbound requests. Texture data is only used for display; no Mojang account authentication is performed.

## Simulated Chat

Simulated lines are broadcast as normal Adventure components. They look like ordinary chat to players, but they are **not signed player-chat packets** and should not be treated as authentic player messages. `delivery` intentionally supports only `broadcast` to avoid implying signed-chat semantics.

## Building

Requirements:

- JDK 21 (required for the plugin jar and tests)
- JDK 25 (required only for the full `./gradlew build`, which compile-verifies the 26.3 API targets)

```bash
# Full build: jar, tests, and all four API compile checks
JAVA_HOME=/path/to/jdk-21 ./gradlew build

# Jar only
JAVA_HOME=/path/to/jdk-21 ./gradlew jar
```

The jar is written to `build/libs/PhantomPlayers-1.0.0.jar`. No Paper/Folia/NMS libraries are bundled; the plugin uses server-provided APIs and reflection only.

Run tests with:

```bash
JAVA_HOME=/path/to/jdk-21 ./gradlew test
```

## Verification Status

Build and automated verification completed:

- 50 unit tests passing.
- Main source compiles against Paper 1.21.11.
- The same source compiles against Paper 1.21.11, Folia 1.21.11, Paper 26.3, and Folia 26.3.
- Packet constructor and method signatures were inspected with `javap` against mapped 1.21.11 and 26.3 server jars.

A startup report from Paper `26.3.build.181-stable` surfaced reflection issues in 26.3 (entity-type constants moved to `EntityTypes`, player-info packet constructor overload selection, and immutable `GameProfile` property maps). All three are fixed in this build. A successful runtime retest has not yet been recorded.

Client-visible behavior (TAB rendering, skin display, entity visibility, server-list sample rendering, Folia threading behavior, and 26.3 plugin loading) remains runtime-unverified. See `DELIVERY.md` for the full report and residual risks.
