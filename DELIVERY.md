# PhantomPlayers Delivery Report

Date: 2026-10-19

## Artifact

- File: `build/libs/PhantomPlayers-1.0.0.jar`
- Size: 169,213 bytes
- SHA-256: `b19486173e0cafa8e74ffa94af0d3392921ed97683b6ed90a9a210511fa1084d`
- Main class: `me.sparklee.phantomplayers.PhantomPlayers`
- Target bytecode: Java 21
- Dependencies: none bundled (Paper/Folia/PlaceholderAPI APIs are `compileOnly`; NMS access is reflection-based)

## Scope Delivered

- Exactly six commands: `/fp spawn`, `/fp spawnall`, `/fp despawn`, `/fp despawnall`, `/fp list [page]`, `/fp reload`.
- Bulk population with batching, recent-name memory, deterministic UUIDs derived from the name pool, and optional spawn spreading.
- Global TAB list plus same-world, distance-limited virtual entities using PlayerInfo, add/remove-entity, entity-metadata, and entity-teleport packets.
- Randomized TAB ping for fake players (`ping.enabled`, range, per-player randomization, periodic variation). The server-browser sample cannot carry latency, so this affects the in-game TAB list only.
- Optional simulated gravity: fake players animate a fall to the surface at spawn using teleport packets (`presentation.gravity`, default `true`).
- Async Mojang skin lookup with bounded concurrency, positive/negative TTL cache, persistent cache preserved across reloads, in-flight deduplication, exponential-backoff retries for transient failures, and configurable fallback texture.
- Server-list count modes (`active`, `configured`, `disabled`), independent fake-count/sample-name toggles, real-player sample preservation, and configurable sample size through `getListedPlayers()`. The legacy `server-status` config section remains accepted.
- Optional PlaceholderAPI expansion (`phantomplayers`) with seven live placeholders, auto-registered when PlaceholderAPI is present and never loaded otherwise.
- Join/quit compatibility: fake players never fire real join/quit events and no announcements are emitted; no `join-message`/`quit-message` configuration exists by design.
- Shared, weighted simulated conversations with delays, concurrency/cooldown limits, speaker rotation, abort-on-despawn, and console mirroring.
- Atomic config + chat reload, deterministic identities with optional `players.yml` overrides, bundled 600-name pool, strict validation, and clear startup diagnostics.
- Central reflection-only mojmap adapter with verified signatures for 1.21.11 and 26.3, runtime fallback probing, and an opt-out for untested versions.
- Unit tests for names, population, selection, counters, status sample caching, caches, config/chat loading, identity derivation/overrides/migration, join resync scheduling, planning, chat execution, ping generation/management, skin lookup/retries, reload cache preservation, placeholders, and join/quit compatibility.

## Verification Performed

### Build

```text
JAVA_HOME=/opt/java/jdk-21.0.12 ./gradlew build
BUILD SUCCESSFUL
```

Tasks included:

- `compileJava`
- `test`
- `compileVerifyPaper121Java`
- `compileVerifyFolia121Java`
- `compileVerifyPaper263Java`
- `compileVerifyFolia263Java`
- `jar`

### Tests

```text
89 tests completed, 0 failed
```

Coverage includes:

- `NamePoolTest`
- `PopulationRegistryTest`
- `SelectionPlannerTest`
- `StatusCounterTest`
- `StatusSampleCacheTest`
- `PingGeneratorTest`
- `PingManagerTest`
- `JoinQuitCompatibilityTest`
- `JoinResyncTest`
- `ProfileCacheTest`
- `ConfigLoaderTest`
- `ChatConfigLoaderTest`
- `IdentityManagerTest`
- `ConversationPlannerTest`
- `ChatEngineTest`
- `SkinManagerTest`
- `SkinServiceReloadTest`
- `PhantomPlaceholdersTest`
- `PlaceholderIntegrationTest`

### API Surface

The following were verified with `javap` against mapped server jars before implementation:

- `ClientboundPlayerInfoUpdatePacket` / `Entry` constructor and `Action` set.
- `ClientboundPlayerInfoRemovePacket`.
- `ClientboundAddEntityPacket` and `ClientboundRemoveEntitiesPacket`.
- `ClientboundSetEntityDataPacket` and `SynchedEntityData.DataValue`.
- `ClientboundTeleportEntityPacket` with `PositionMoveRotation` for simulated gravity.
- `ServerPlayer.connection` and packet listener `send`.
- Entity ID allocation: `Entity.nextEntityId()` on 1.21.11 and `ServerLevel.getNextEntityId()` on 26.3.
- Profile classes: `GameProfile`, `Property`, `PropertyMap`.
- Paper status event: `getListedPlayers()`, `ListedPlayerInfo(String name, UUID id)`.
- Scheduler API availability on Paper and Folia.

## Follow-up Runtime Fix

An initial startup report from Paper `26.3.build.181-stable` exposed two reflection issues that compilation could not catch:

1. Minecraft 26.3 moved entity-type constants from `net.minecraft.world.entity.EntityType` to `EntityTypes`. Player-type resolution now probes both holders, preferring `EntityType.PLAYER` and falling back to `EntityTypes.PLAYER`.
2. `ClientboundPlayerInfoUpdatePacket` has both `(EnumSet, Collection<ServerPlayer>)` and `(EnumSet, List<Entry>)` constructors. The adapter now selects the `List<Entry>` constructor explicitly; the previous `Collection` lookup could select the wrong overload.
3. `GameProfile` built with the two-argument constructor exposes an immutable property map, so `properties().put(...)` threw `UnsupportedOperationException`. The adapter now builds a mutable Guava multimap, wraps it in a `PropertyMap`, and uses the three-argument `GameProfile` constructor. If that path is unavailable, skins degrade to no-texture profiles instead of failing.

All fixes are in the artifact above. Build and all tests pass after the change. A successful runtime retest has not yet been recorded.

## Enhancement Round (Addendum)

A second implementation round added the following, all with unit coverage:

1. **Randomized TAB ping** — new `ping` config section; values are assigned per fake player (distinct when the range allows), drift periodically, and are pushed to viewers via `ClientboundPlayerInfoUpdatePacket`. The vanilla server-browser sample cannot carry latency, so `server-list` samples are unchanged by design.
2. **Skin lookup retries** — transient failures are retried with exponential backoff (capped at 30 s); missing accounts are cached immediately; the resolved cache is preserved in memory across `/fp reload` along with the updated TTLs.
3. **Server-list refinement** — the section was renamed to `server-list` with independent toggles (`include-fake-count`, `include-fake-sample-names`, `include-real-players`, `sample-size`); the legacy `server-status` schema is still parsed. Active/configured samples are cached and rebuilt only when population/identity versions change.
4. **PlaceholderAPI expansion** — `%phantomplayers_*%` placeholders for fake/real/total online, max, configured, spawned, and unspawned counts. The expansion is registered only when PlaceholderAPI is installed, is re-registered idempotently on reload, and never loaded otherwise.
5. **Join/quit compatibility decision** — packet-only fake players cannot trigger or receive real events, so no announcements are emitted and no message options exist. This is reported in `/fp list` and documented instead of being faked.
6. **Reload safety** — `/fp reload` preserves the skin cache and PlaceholderAPI registration; population/identity versions drive incremental sample-cache invalidation.

## Relog and Identity Storage Fixes

A third round addressed the reported rejoin behavior and simplified `players.yml`:

1. **Relog presentation loss** — join resync previously sent the TAB upsert and entity reconciliation only through the player's entity scheduler on the next tick. `PlayerJoinEvent` fires before Paper sends the joining player's initial player-info packet, and a client that is still loading chunks can ignore entity adds. PhantomPlayers now sends the TAB upsert immediately (player-info packets are netty-thread-safe), reconciles entities on the entity scheduler, and repeats both at 1, 20, and 60 ticks after join. Join retries clear per-viewer entity membership first, so a dropped attempt cannot stop the periodic reconciliation from re-adding the entities.
2. **Scheduler fallback** — `runForPlayer` now logs and falls back to the global scheduler when an entity scheduler rejects a task instead of silently dropping it. Periodic `syncAll` isolates failures per viewer, and TAB broadcast/removal/ping/skin updates no longer depend on the entity scheduler.
3. **Compact `players.yml`** — identities are derived from `names.txt`; `players.yml` is now an optional override file with `disabled:` and `uuids:` sections. Default identities are not written, and `/fp spawn` no longer persists a full roster entry. Legacy `identities:` files are read once and rewritten automatically.

## Not Verified

Per instruction, **no test Minecraft server was run**. The following remain unverified:

- Actual client rendering of TAB entries, skins, skin layers, virtual entities, and assigned TAB ping values.
- Server-list count/sample rendering in the multiplayer client.
- PlaceholderAPI expansion registration and placeholder output on a live server.
- Runtime packet acceptance on Paper/Folia 1.21.11 and 26.3 beyond the reported startup failure.
- Folia region-thread behavior under real load.
- Plugin loading on Paper/Folia 26.3 with `api-version: '1.21'`.
- Live Mojang API behavior from this host (retry/backoff timing against real rate limits).
- The rejoin resync fix and the `players.yml` migration were validated by unit tests only, not against a live client.
- Behavior on any version other than the four compile targets.

## Residual Risks

1. **Reflection adapter fragility.** Future server builds may rename or change mojmap packet constructors. Startup fails with a clear `CompatibilityException` if the expected surface is absent; `compatibility.allow-untested-adapters` only affects unknown versions, not missing classes.
2. **Status API assumptions.** The plugin relies on `getListedPlayers()` being mutable and on the client honoring the sample list. The API and list mutability were compile-verified, not rendered.
3. **Folia assumptions.** Scheduling uses official Folia APIs, but no live Folia server exercised region ownership or viewer reconciliation timing, including the ping variation rescheduler.
4. **Skin service dependence.** Mojang rate limits, network failures, and profile changes affect appearance. Retries add bounded delay (max 30 s per attempt chain) before negative caching; failures fall back to no-texture or the configured fallback skin.
5. **Virtual-player semantics.** Fake players are not real connections; plugins that inspect real online players will not see them. They have no AI, collision, inventory, or signed chat.
6. **Join/quit events.** Because fake players are packet-only, no join/quit events fire for them and no announcements are produced. Plugins that count joins/quits will not see fakes. This is documented and deliberately not faked.
7. **TAB ping display.** Ping values are protocol-supported in TAB entries, but a client may smooth or format them; the values are assignment-time snapshots and are not measured. The server-browser sample never shows ping (protocol limitation).
8. **PlaceholderAPI coupling.** The `integration` package is only class-loaded when PlaceholderAPI is installed; if an unusual PlaceholderAPI fork changes its API, registration fails soft (logged, disabled) and never blocks plugin startup.
9. **Chat authenticity.** Simulated chat is a normal broadcast and is not signed player chat. This is intentional and documented.
10. **Version floor.** 1.21.11 is the minimum; 1.21.8 is not supported and was not compiled.
11. **API version declaration.** `plugin.yml` uses `api-version: '1.21'`; 26.3 acceptance was not runtime-tested.

## Operational Notes

- First startup writes defaults and saves no active population.
- `/fp spawnall` respects `population.max-active` and batches spawns.
- `/fp reload` aborts without applying changes if `config.yml` or `chat.yml` fails validation.
- `/fp reload` preserves the resolved skin cache in memory and re-registers the PlaceholderAPI expansion only once.
- Identity overrides persist in the optional `players.yml`; the roster itself is rebuilt from `names.txt`. The skin cache persists in `cache/profiles.yml`.
- PlaceholderAPI placeholders are read live, so they reflect the current population without re-registration.
- `/fp list` reports platform, adapter, skin cache, chat state, ping mode, population counters, PlaceholderAPI state, and join/quit compatibility for diagnosis.
