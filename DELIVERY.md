# PhantomPlayers Delivery Report

Date: 2026-10-19

## Artifact

- File: `build/libs/PhantomPlayers-1.0.0.jar`
- Size: 147,168 bytes
- SHA-256: `004b2f31710e31e2653fe5b9cf785df67bb3edbe69273db49f1108fc5690e78e`
- Main class: `me.sparklee.phantomplayers.PhantomPlayers`
- Target bytecode: Java 21
- Dependencies: none bundled (Paper/Folia APIs are `compileOnly`; NMS access is reflection-based)

## Scope Delivered

- Exactly six commands: `/fp spawn`, `/fp spawnall`, `/fp despawn`, `/fp despawnall`, `/fp list [page]`, `/fp reload`.
- Bulk population with batching, recent-name memory, deterministic persistent UUIDs, and optional spawn spreading.
- Global TAB list plus same-world, distance-limited virtual entities using PlayerInfo, add/remove-entity, entity-metadata, and entity-teleport packets.
- Optional simulated gravity: fake players animate a fall to the surface at spawn using teleport packets (`presentation.gravity`, default `true`).
- Async Mojang skin lookup with bounded concurrency, positive/negative TTL cache, persistent cache, in-flight deduplication, and configurable fallback texture.
- Server-list count modes (`active`, `configured`, `disabled`) and mutable listed-player samples through `getListedPlayers()`.
- Shared, weighted simulated conversations with delays, concurrency/cooldown limits, speaker rotation, abort-on-despawn, and console mirroring.
- Atomic config + chat reload, persistent identities, bundled 600-name pool, strict validation, and clear startup diagnostics.
- Central reflection-only mojmap adapter with verified signatures for 1.21.11 and 26.3, runtime fallback probing, and an opt-out for untested versions.
- Unit tests for names, population, selection, counters, caches, config/chat loading, identity persistence, planning, chat execution, and skin lookup.

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
50 tests completed, 0 failed
```

Coverage includes:

- `NamePoolTest`
- `PopulationRegistryTest`
- `SelectionPlannerTest`
- `StatusCounterTest`
- `ProfileCacheTest`
- `ConfigLoaderTest`
- `ChatConfigLoaderTest`
- `IdentityManagerTest`
- `ConversationPlannerTest`
- `ChatEngineTest`
- `SkinManagerTest`

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

All fixes are in the artifact above. Build and all 50 tests pass after the change. A successful runtime retest has not yet been recorded.

## Not Verified

Per instruction, **no test Minecraft server was run**. The following remain unverified:

- Actual client rendering of TAB entries, skins, skin layers, and virtual entities.
- Server-list count/sample rendering in the multiplayer client.
- Runtime packet acceptance on Paper/Folia 1.21.11 and 26.3 beyond the reported startup failure.
- Folia region-thread behavior under real load.
- Plugin loading on Paper/Folia 26.3 with `api-version: '1.21'`.
- Live Mojang API behavior from this host.
- Behavior on any version other than the four compile targets.

## Residual Risks

1. **Reflection adapter fragility.** Future server builds may rename or change mojmap packet constructors. Startup fails with a clear `CompatibilityException` if the expected surface is absent; `compatibility.allow-untested-adapters` only affects unknown versions, not missing classes.
2. **Status API assumptions.** The plugin relies on `getListedPlayers()` being mutable and on the client honoring the sample list. The API and list mutability were compile-verified, not rendered.
3. **Folia assumptions.** Scheduling uses official Folia APIs, but no live Folia server exercised region ownership or viewer reconciliation timing.
4. **Skin service dependence.** Mojang rate limits, network failures, and profile changes affect appearance. Failures are cached negatively and fall back to Steve/Alex or the configured fallback skin.
5. **Virtual-player semantics.** Fake players are not real connections; plugins that inspect real online players will not see them. They have no AI, collision, inventory, or signed chat.
6. **Chat authenticity.** Simulated chat is a normal broadcast and is not signed player chat. This is intentional and documented.
7. **Version floor.** 1.21.11 is the minimum; 1.21.8 is not supported and was not compiled.
8. **API version declaration.** `plugin.yml` uses `api-version: '1.21'`; 26.3 acceptance was not runtime-tested.

## Operational Notes

- First startup writes defaults and saves no active population.
- `/fp spawnall` respects `population.max-active` and batches spawns.
- `/fp reload` aborts without applying changes if `config.yml` or `chat.yml` fails validation.
- Identities persist in `players.yml`; skin cache persists in `cache/profiles.yml`.
- `/fp list` reports platform, adapter, skin cache, chat state, and counters for diagnosis.
