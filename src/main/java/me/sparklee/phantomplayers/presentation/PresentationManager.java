package me.sparklee.phantomplayers.presentation;

import me.sparklee.phantomplayers.config.Settings;
import me.sparklee.phantomplayers.names.NameRules;
import me.sparklee.phantomplayers.platform.Cancellable;
import me.sparklee.phantomplayers.platform.NmsPackets;
import me.sparklee.phantomplayers.platform.SchedulerService;
import me.sparklee.phantomplayers.population.ActiveFakePlayer;
import me.sparklee.phantomplayers.population.SpawnPoint;
import me.sparklee.phantomplayers.skin.SkinData;
import me.sparklee.phantomplayers.skin.SkinService;
import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Owns all presentation state: global player-info entries and per-viewer
 * virtual entities. Every player-facing send is scheduled on that viewer's
 * entity scheduler (or the global/region scheduler) so the same code path is
 * safe on Paper and Folia.
 */
public final class PresentationManager {

    private static final byte SKIN_PARTS = (byte) 0x7F;

    private final Plugin plugin;
    private final NmsPackets packets;
    private final SchedulerService scheduler;
    private final SkinService skins;
    private final Map<String, Entry> entries = new ConcurrentHashMap<>();
    private final AtomicBoolean syncRunning = new AtomicBoolean();

    private volatile Settings settings;
    private Cancellable syncTask;
    private Cancellable fallTask;

    public PresentationManager(Plugin plugin, NmsPackets packets, SchedulerService scheduler,
                               SkinService skins, Settings settings) {
        this.plugin = plugin;
        this.packets = packets;
        this.scheduler = scheduler;
        this.skins = skins;
        this.settings = settings;
    }

    public void start() {
        if (syncTask != null) {
            syncTask.cancel();
        }
        long interval = settings.presentation().syncIntervalTicks();
        syncTask = scheduler.runGlobalRepeating(interval, interval, this::syncAll);
        fallTask = scheduler.runGlobalRepeating(2L, 2L, this::tickGravity);
    }

    public void stop() {
        if (syncTask != null) {
            syncTask.cancel();
            syncTask = null;
        }
        if (fallTask != null) {
            fallTask.cancel();
            fallTask = null;
        }
        List<ActiveFakePlayer> active = new ArrayList<>();
        for (Entry entry : entries.values()) {
            active.add(entry.fake);
        }
        try {
            removeFakes(active);
        } catch (RuntimeException ignored) {
            entries.clear();
        }
    }

    public void updateSettings(Settings next) {
        Settings previous = this.settings;
        this.settings = next;
        if (previous.presentation().syncIntervalTicks() != next.presentation().syncIntervalTicks()
                || syncTask == null) {
            start();
        }
        if (previous.presentation().tabList() != next.presentation().tabList()) {
            syncAll();
        }
        if (previous.presentation().gravity() != next.presentation().gravity()) {
            if (next.presentation().gravity()) {
                entries.values().forEach(this::beginFall);
            } else {
                entries.values().forEach(entry -> entry.falling = false);
            }
        }
    }

    public boolean isPresented(String name) {
        return entries.containsKey(NameRules.key(name));
    }

    public void addFakes(List<ActiveFakePlayer> fakes) {
        if (fakes.isEmpty()) {
            return;
        }
        List<Entry> added = new ArrayList<>(fakes.size());
        for (ActiveFakePlayer fake : fakes) {
            String key = NameRules.key(fake.identity().name());
            Entry entry = new Entry(fake, skins.cached(fake.identity().name()));
            if (entries.putIfAbsent(key, entry) == null) {
                added.add(entry);
            }
        }
        if (added.isEmpty()) {
            return;
        }
        sendTab(added);
        for (Entry entry : added) {
            allocateAndSend(entry);
        }
    }

    public void removeFakes(List<ActiveFakePlayer> fakes) {
        if (fakes.isEmpty()) {
            return;
        }
        List<UUID> ids = new ArrayList<>(fakes.size());
        List<Entry> removed = new ArrayList<>(fakes.size());
        for (ActiveFakePlayer fake : fakes) {
            Entry entry = entries.remove(NameRules.key(fake.identity().name()));
            if (entry != null) {
                removed.add(entry);
                ids.add(entry.fake.identity().uuid());
            }
        }
        if (removed.isEmpty()) {
            return;
        }
        Object removePacket = packets.tabRemove(ids);
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            scheduleTab(viewer, removePacket);
        }
        for (Entry entry : removed) {
            int entityId = entry.entityId.get();
            if (entityId <= 0) {
                entry.viewers.clear();
                continue;
            }
            Object removeEntities = packets.removeEntities(new int[]{entityId});
            for (UUID viewerId : List.copyOf(entry.viewers)) {
                Player viewer = Bukkit.getPlayer(viewerId);
                if (viewer != null) {
                    scheduleEntitySend(viewer, removeEntities);
                }
            }
            entry.viewers.clear();
        }
    }

    public void updateSkin(String name, SkinData skin) {
        Entry entry = entries.get(NameRules.key(name));
        if (entry == null) {
            return;
        }
        entry.skin = skin;
        sendTab(List.of(entry));

        int entityId = entry.entityId.get();
        if (entityId <= 0) {
            return;
        }
        for (UUID viewerId : List.copyOf(entry.viewers)) {
            Player viewer = Bukkit.getPlayer(viewerId);
            if (viewer == null) {
                continue;
            }
            scheduler.runForPlayer(viewer, () -> {
                if (!viewer.isOnline() || entries.get(NameRules.key(name)) != entry
                        || !entry.viewers.contains(viewerId)) {
                    return;
                }
                Location location = fakeLocation(entry);
                if (location == null) {
                    return;
                }
                Object add = packets.addEntity(entityId, entry.fake.identity().uuid(), location);
                packets.send(viewer, packets.removeEntities(new int[]{entityId}));
                packets.send(viewer, add);
                sendMetadata(viewer, entityId);
            });
        }
    }

    public void syncViewer(Player viewer) {
        if (!viewer.isOnline()) {
            return;
        }
        List<Entry> snapshot = List.copyOf(entries.values());
        if (snapshot.isEmpty()) {
            return;
        }
        Object tabPacket = buildTab(snapshot);
        scheduler.runForPlayer(viewer, () -> {
            if (viewer.isOnline()) {
                packets.send(viewer, tabPacket);
                reconcile(viewer, snapshot);
            }
        });
    }

    public void resyncViewer(Player viewer) {
        forgetViewer(viewer.getUniqueId());
        syncViewer(viewer);
    }

    public void forgetViewer(UUID viewerId) {
        for (Entry entry : entries.values()) {
            entry.viewers.remove(viewerId);
        }
    }

    public void syncAll() {
        if (!syncRunning.compareAndSet(false, true)) {
            return;
        }
        try {
            List<Entry> snapshot = List.copyOf(entries.values());
            if (snapshot.isEmpty()) {
                return;
            }
            Object tabPacket = buildTab(snapshot);
            for (Player viewer : Bukkit.getOnlinePlayers()) {
                scheduleTab(viewer, tabPacket);
                scheduler.runForPlayer(viewer, () -> {
                    if (viewer.isOnline()) {
                        reconcile(viewer, snapshot);
                    }
                });
            }
            for (Entry entry : snapshot) {
                if (entry.entityId.get() <= 0) {
                    allocateAndSend(entry);
                }
            }
        } finally {
            syncRunning.set(false);
        }
    }

    private void reconcile(Player viewer, Collection<Entry> snapshot) {
        if (!viewer.isOnline()) {
            forgetViewer(viewer.getUniqueId());
            return;
        }
        UUID viewerId = viewer.getUniqueId();
        Location viewerLocation = viewer.getLocation();
        World viewerWorld = viewer.getWorld();
        double maxDistance = settings.presentation().entityViewDistance();
        double maxDistanceSquared = maxDistance * maxDistance;

        List<Entry> toAdd = new ArrayList<>();
        List<Integer> toRemove = new ArrayList<>();
        for (Entry entry : snapshot) {
            if (entries.get(NameRules.key(entry.fake.identity().name())) != entry) {
                continue;
            }
            Location fakeLocation = fakeLocation(entry);
            boolean eligible = fakeLocation != null && Objects.equals(viewerWorld, fakeLocation.getWorld())
                    && viewerLocation.distanceSquared(fakeLocation) <= maxDistanceSquared;
            int entityId = entry.entityId.get();
            if (!eligible) {
                if (entityId > 0 && entry.viewers.remove(viewerId)) {
                    toRemove.add(entityId);
                }
            } else if (entityId > 0 && !entry.viewers.contains(viewerId)) {
                toAdd.add(entry);
            }
        }
        if (!toRemove.isEmpty()) {
            int[] ids = new int[toRemove.size()];
            for (int index = 0; index < ids.length; index++) {
                ids[index] = toRemove.get(index);
            }
            packets.send(viewer, packets.removeEntities(ids));
        }
        for (Entry entry : toAdd) {
            Location location = fakeLocation(entry);
            if (location == null) {
                continue;
            }
            int entityId = entry.entityId.get();
            if (entityId <= 0) {
                continue;
            }
            entry.viewers.add(viewerId);
            packets.send(viewer, packets.addEntity(entityId, entry.fake.identity().uuid(), location));
            sendMetadata(viewer, entityId);
        }
    }

    private void allocateAndSend(Entry entry) {
        SpawnPoint point = entry.fake.location();
        World world = Bukkit.getWorld(point.world());
        if (world == null) {
            return;
        }
        Location location = new Location(world, point.x(), point.y(), point.z(), point.yaw(), point.pitch());
        try {
            scheduler.runAtLocation(world, location, () -> {
                if (entries.get(NameRules.key(entry.fake.identity().name())) != entry) {
                    return;
                }
                if (entry.entityId.get() <= 0) {
                    int entityId = packets.allocateEntityId(world, location);
                    if (entityId > 0) {
                        entry.entityId.set(entityId);
                    }
                }
                sendEntityToEligible(entry);
                beginFall(entry);
            });
        } catch (RuntimeException ignored) {
            // Scheduler unavailable during shutdown.
        }
    }

    private void sendEntityToEligible(Entry entry) {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            scheduler.runForPlayer(viewer, () -> sendEntityToViewer(entry, viewer));
        }
    }

    private void sendEntityToViewer(Entry entry, Player viewer) {
        if (entries.get(NameRules.key(entry.fake.identity().name())) != entry) {
            return;
        }
        UUID viewerId = viewer.getUniqueId();
        if (!viewer.isOnline()) {
            entry.viewers.remove(viewerId);
            return;
        }
        int entityId = entry.entityId.get();
        if (entityId <= 0) {
            return;
        }
        Location location = fakeLocation(entry);
        if (location == null) {
            return;
        }
        boolean eligible = Objects.equals(viewer.getWorld(), location.getWorld())
                && viewer.getLocation().distanceSquared(location)
                <= settings.presentation().entityViewDistance() * settings.presentation().entityViewDistance();
        if (!eligible) {
            if (entry.viewers.remove(viewerId)) {
                packets.send(viewer, packets.removeEntities(new int[]{entityId}));
            }
            return;
        }
        if (entry.viewers.add(viewerId)) {
            packets.send(viewer, packets.addEntity(entityId, entry.fake.identity().uuid(), location));
            sendMetadata(viewer, entityId);
        }
    }

    private void sendMetadata(Player viewer, int entityId) {
        if (!settings.presentation().skinLayers()) {
            return;
        }
        Object metadata = packets.metadata(entityId, SKIN_PARTS);
        if (metadata != null) {
            packets.send(viewer, metadata);
        }
    }

    private void sendTab(List<Entry> snapshot) {
        if (snapshot.isEmpty()) {
            return;
        }
        Object packet = buildTab(snapshot);
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            scheduleTab(viewer, packet);
        }
    }

    private Object buildTab(List<Entry> snapshot) {
        List<NmsPackets.TabEntry> profiles = new ArrayList<>(snapshot.size());
        boolean listed = settings.presentation().tabList();
        for (Entry entry : snapshot) {
            profiles.add(new NmsPackets.TabEntry(entry.fake.identity().uuid(), entry.fake.identity().name(),
                    entry.skin, listed));
        }
        return packets.tabUpsert(profiles);
    }

    private void scheduleTab(Player viewer, Object packet) {
        scheduler.runForPlayer(viewer, () -> {
            if (viewer.isOnline()) {
                packets.send(viewer, packet);
            }
        });
    }

    private void scheduleEntitySend(Player viewer, Object packet) {
        scheduler.runForPlayer(viewer, () -> {
            if (viewer.isOnline()) {
                packets.send(viewer, packet);
            }
        });
    }

    private void beginFall(Entry entry) {
        if (!settings.presentation().gravity()) {
            return;
        }
        int entityId = entry.entityId.get();
        if (entityId <= 0) {
            return;
        }
        SpawnPoint point = entry.fake.location();
        World world = Bukkit.getWorld(point.world());
        if (world == null) {
            return;
        }
        Location probe = new Location(world, entry.x, entry.y, entry.z);
        try {
            scheduler.runAtLocation(world, probe, () -> {
                if (entries.get(NameRules.key(entry.fake.identity().name())) != entry
                        || entry.entityId.get() != entityId) {
                    return;
                }
                int groundY = world.getHighestBlockYAt(probe.getBlockX(), probe.getBlockZ(),
                        HeightMap.MOTION_BLOCKING_NO_LEAVES);
                double targetY = Math.max(world.getMinHeight() + 1.0D, groundY + 1.0D);
                if (entry.y <= targetY) {
                    entry.falling = false;
                    return;
                }
                entry.fallTargetY = targetY;
                entry.fallVelocity = 0.0D;
                entry.falling = true;
            });
        } catch (RuntimeException ignored) {
            // Scheduler unavailable during shutdown.
        }
    }

    private void tickGravity() {
        if (!settings.presentation().gravity()) {
            for (Entry entry : entries.values()) {
                entry.falling = false;
            }
            return;
        }
        for (Entry entry : entries.values()) {
            if (!entry.falling) {
                continue;
            }
            int entityId = entry.entityId.get();
            if (entityId <= 0) {
                entry.falling = false;
                continue;
            }
            double velocity = Math.min(4.0D, entry.fallVelocity + 0.16D);
            double nextY = entry.y - velocity;
            if (nextY <= entry.fallTargetY) {
                nextY = entry.fallTargetY;
                entry.falling = false;
            } else {
                entry.fallVelocity = velocity;
            }
            entry.y = nextY;
            Location location = fakeLocation(entry);
            if (location == null) {
                entry.falling = false;
                continue;
            }
            Object packet = packets.teleportEntity(entityId, location);
            for (UUID viewerId : List.copyOf(entry.viewers)) {
                Player viewer = Bukkit.getPlayer(viewerId);
                if (viewer != null) {
                    scheduleEntitySend(viewer, packet);
                }
            }
        }
    }

    private Location fakeLocation(Entry entry) {
        SpawnPoint point = entry.fake.location();
        World world = Bukkit.getWorld(point.world());
        if (world == null) {
            return null;
        }
        return new Location(world, entry.x, entry.y, entry.z, entry.yaw, entry.pitch);
    }

    private static final class Entry {
        private final ActiveFakePlayer fake;
        private final AtomicInteger entityId = new AtomicInteger(-1);
        private final Set<UUID> viewers = ConcurrentHashMap.newKeySet();
        private volatile SkinData skin;
        private volatile double x;
        private volatile double y;
        private volatile double z;
        private volatile float yaw;
        private volatile float pitch;
        private volatile boolean falling;
        private volatile double fallVelocity;
        private volatile double fallTargetY;

        private Entry(ActiveFakePlayer fake, SkinData skin) {
            this.fake = fake;
            this.skin = skin;
            SpawnPoint point = fake.location();
            this.x = point.x();
            this.y = point.y();
            this.z = point.z();
            this.yaw = point.yaw();
            this.pitch = point.pitch();
        }
    }
}
