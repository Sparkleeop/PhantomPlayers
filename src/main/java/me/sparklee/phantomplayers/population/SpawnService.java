package me.sparklee.phantomplayers.population;

import me.sparklee.phantomplayers.config.ConfigManager;
import me.sparklee.phantomplayers.config.Settings;
import me.sparklee.phantomplayers.identity.FakeIdentity;
import me.sparklee.phantomplayers.identity.IdentityManager;
import me.sparklee.phantomplayers.metrics.Metrics;
import me.sparklee.phantomplayers.names.NameRules;
import me.sparklee.phantomplayers.platform.Cancellable;
import me.sparklee.phantomplayers.platform.SchedulerService;
import me.sparklee.phantomplayers.presentation.PresentationManager;
import me.sparklee.phantomplayers.skin.SkinService;
import me.sparklee.phantomplayers.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public final class SpawnService {

    private final ConfigManager config;
    private final PopulationRegistry registry;
    private final IdentityManager identities;
    private final PresentationManager presentation;
    private final SkinService skins;
    private final SchedulerService scheduler;
    private final Metrics metrics;
    private final Random random = new Random();
    private final AtomicBoolean spawnAllRunning = new AtomicBoolean();

    public SpawnService(ConfigManager config, PopulationRegistry registry, IdentityManager identities,
                        PresentationManager presentation, SkinService skins, SchedulerService scheduler,
                        Metrics metrics) {
        this.config = config;
        this.registry = registry;
        this.identities = identities;
        this.presentation = presentation;
        this.skins = skins;
        this.scheduler = scheduler;
        this.metrics = metrics;
    }

    public boolean spawn(String name, CommandSender sender) {
        Settings settings = config.settings();
        metrics.spawnAttempt();
        if (!NameRules.isValid(name)) {
            metrics.spawnFailure();
            sender.sendMessage(Text.error("Invalid username. Use 3-16 characters: A-Z, a-z, 0-9, _"));
            return false;
        }
        Location base = baseLocation(sender);
        if (base == null) {
            metrics.spawnFailure();
            sender.sendMessage(Text.error("Console spawn world \"" + settings.population().consoleSpawn().world()
                    + "\" is not loaded."));
            return false;
        }
        FakeIdentity identity = identities.ensure(name);
        SpawnPoint point = pointAt(base, false);
        var active = registry.activate(identity, point, settings.population().maxActive());
        if (active.isEmpty()) {
            metrics.spawnFailure();
            if (registry.isActive(name)) {
                sender.sendMessage(Text.warn(name + " is already active."));
            } else {
                sender.sendMessage(Text.error("Population limit reached (" + settings.population().maxActive()
                        + ")."));
            }
            return false;
        }
        metrics.spawnSuccess();
        presentation.addFakes(List.of(active.get()));
        resolveSkin(name);
        sender.sendMessage(Text.success("Spawned " + name + ". Population: "
                + registry.activeCount() + "/" + settings.population().maxActive() + "."));
        return true;
    }

    public boolean despawn(String name, CommandSender sender) {
        var removed = registry.remove(name);
        if (removed.isEmpty()) {
            sender.sendMessage(Text.warn(name + " is not active."));
            return false;
        }
        presentation.removeFakes(List.of(removed.get()));
        metrics.despawned();
        sender.sendMessage(Text.success("Despawned " + removed.get().identity().name() + ". Population: "
                + registry.activeCount() + "/" + config.settings().population().maxActive() + "."));
        return true;
    }

    public int despawnAll(CommandSender sender) {
        List<ActiveFakePlayer> removed = registry.removeAll();
        if (removed.isEmpty()) {
            sender.sendMessage(Text.warn("No fake players are active."));
            return 0;
        }
        presentation.removeFakes(removed);
        for (int index = 0; index < removed.size(); index++) {
            metrics.despawned();
        }
        sender.sendMessage(Text.success("Despawned " + removed.size() + " fake player"
                + (removed.size() == 1 ? "" : "s") + "."));
        return removed.size();
    }

    public void spawnAll(CommandSender sender) {
        if (!spawnAllRunning.compareAndSet(false, true)) {
            sender.sendMessage(Text.warn("A spawnall operation is already running."));
            return;
        }
        Settings settings = config.settings();
        Location base = baseLocation(sender);
        if (base == null) {
            spawnAllRunning.set(false);
            sender.sendMessage(Text.error("Console spawn world \"" + settings.population().consoleSpawn().world()
                    + "\" is not loaded."));
            return;
        }
        int capacity = Math.max(0, settings.population().maxActive() - registry.activeCount());
        if (capacity == 0) {
            spawnAllRunning.set(false);
            sender.sendMessage(Text.warn("Population is already at the configured limit ("
                    + settings.population().maxActive() + ")."));
            return;
        }
        List<FakeIdentity> selected = SelectionPlanner.selectSpawnAll(identities.spawnPool(),
                registry.activeKeys(), registry.recentKeys(), capacity, random);
        if (selected.isEmpty()) {
            spawnAllRunning.set(false);
            sender.sendMessage(Text.warn("No spawnable identities. Add usernames to names.txt or despawn "
                    + "active entries."));
            return;
        }

        int batchSize = settings.population().spawnBatchSize();
        List<List<FakeIdentity>> batches = new ArrayList<>();
        for (int start = 0; start < selected.size(); start += batchSize) {
            batches.add(List.copyOf(selected.subList(start, Math.min(selected.size(), start + batchSize))));
        }

        sender.sendMessage(Text.info("Spawning " + selected.size() + " fake player"
                + (selected.size() == 1 ? "" : "s") + " in " + batches.size() + " batch"
                + (batches.size() == 1 ? "" : "es") + "..."));

        AtomicInteger index = new AtomicInteger();
        AtomicInteger spawned = new AtomicInteger();
        Cancellable[] handle = new Cancellable[1];
        long delay = settings.population().spawnBatchDelayTicks();
        handle[0] = scheduler.runGlobalRepeating(delay, Math.max(1L, delay), () -> {
            int batchIndex = index.getAndIncrement();
            if (batchIndex >= batches.size()) {
                handle[0].cancel();
                finishSpawnAll(sender, spawned.get(), selected.size());
                return;
            }
            List<ActiveFakePlayer> activated = new ArrayList<>();
            for (FakeIdentity identity : batches.get(batchIndex)) {
                metrics.spawnAttempt();
                SpawnPoint point = pointAt(base, settings.population().spread().enabled());
                var active = registry.activate(identity, point, settings.population().maxActive());
                if (active.isPresent()) {
                    activated.add(active.get());
                    spawned.incrementAndGet();
                    metrics.spawnSuccess();
                } else {
                    metrics.spawnFailure();
                }
            }
            if (!activated.isEmpty()) {
                presentation.addFakes(activated);
                for (ActiveFakePlayer fake : activated) {
                    resolveSkin(fake.identity().name());
                }
            }
            if (batchIndex >= batches.size() - 1) {
                handle[0].cancel();
                finishSpawnAll(sender, spawned.get(), selected.size());
            }
        });
    }

    private void finishSpawnAll(CommandSender sender, int spawned, int requested) {
        spawnAllRunning.set(false);
        int active = registry.activeCount();
        int max = config.settings().population().maxActive();
        if (spawned == 0) {
            sender.sendMessage(Text.warn("No fake players were spawned. Population: " + active + "/" + max + "."));
        } else {
            sender.sendMessage(Text.success("Spawned " + spawned + " of " + requested + " requested fake player"
                    + (spawned == 1 ? "" : "s") + ". Population: " + active + "/" + max + "."));
        }
    }

    private void resolveSkin(String name) {
        skins.resolve(name).thenAccept(skin -> {
            if (skin.hasTexture()) {
                presentation.updateSkin(name, skin);
            }
        });
    }

    private Location baseLocation(CommandSender sender) {
        if (sender instanceof Player player) {
            return player.getLocation().clone();
        }
        Settings.ConsoleSpawn console = config.settings().population().consoleSpawn();
        World world = Bukkit.getWorld(console.world());
        if (world == null) {
            return null;
        }
        return new Location(world, console.x(), console.y(), console.z(), console.yaw(), console.pitch());
    }

    private SpawnPoint pointAt(Location base, boolean spread) {
        Settings settings = config.settings();
        double x = base.getX();
        double z = base.getZ();
        if (spread && settings.population().spread().enabled()) {
            double radius = settings.population().spread().radius();
            if (radius > 0.0D) {
                double angle = random.nextDouble() * Math.PI * 2.0D;
                double distance = Math.sqrt(random.nextDouble()) * radius;
                x += Math.cos(angle) * distance;
                z += Math.sin(angle) * distance;
            }
        }
        float yaw = random.nextFloat() * 360.0F;
        return new SpawnPoint(base.getWorld().getName(), x, base.getY(), z, yaw, 0.0F);
    }
}
