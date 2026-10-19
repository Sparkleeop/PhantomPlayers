package me.sparklee.phantomplayers.skin;

import me.sparklee.phantomplayers.config.Settings;
import me.sparklee.phantomplayers.metrics.Metrics;
import me.sparklee.phantomplayers.names.NameRules;
import me.sparklee.phantomplayers.platform.SchedulerService;
import me.sparklee.phantomplayers.util.AtomicFiles;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class SkinService implements AutoCloseable {

    private final Plugin plugin;
    private final SchedulerService scheduler;
    private final Metrics metrics;
    private final Path cacheFile;
    private final AtomicReference<SkinManager> manager = new AtomicReference<>();
    private final AtomicBoolean saveScheduled = new AtomicBoolean();
    private final Object saveLock = new Object();
    private final AtomicInteger threadCounter = new AtomicInteger();

    private volatile Settings.Profiles profiles = Settings.defaults().profiles();
    private volatile SkinData fallback = SkinData.EMPTY;
    private volatile boolean closed;

    public SkinService(Plugin plugin, SchedulerService scheduler, Metrics metrics) {
        this.plugin = plugin;
        this.scheduler = scheduler;
        this.metrics = metrics;
        this.cacheFile = plugin.getDataFolder().toPath().resolve("cache").resolve("profiles.yml");
    }

    public void apply(Settings.Profiles next) {
        if (closed) {
            return;
        }
        saveNow();
        SkinManager old = manager.getAndSet(null);
        if (old != null) {
            old.close();
        }
        this.profiles = next;
        this.fallback = next.fallbackSkin();

        ProfileCache cache = loadCache(next);
        ProfileLookup lookup;
        if (next.enabled()) {
            lookup = new MojangProfileLookup(next.requestTimeoutSeconds(), "PhantomPlayers/1.0.0");
        } else {
            lookup = username -> LookupOutcome.notFound("profile lookups are disabled");
        }
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable,
                    "PhantomPlayers-Skins-" + threadCounter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        ExecutorService executor = Executors.newFixedThreadPool(next.maxConcurrentRequests(), factory);
        SkinManager created = new SkinManager(cache, lookup, executor, next.maxConcurrentRequests(),
                System::currentTimeMillis, () -> fallback, this::onLookupWarning, this::scheduleSave);
        manager.set(created);
    }

    public CompletableFuture<SkinData> resolve(String username) {
        SkinManager current = manager.get();
        if (current == null) {
            return CompletableFuture.completedFuture(fallback);
        }
        return current.resolve(username);
    }

    public SkinData cached(String username) {
        SkinManager current = manager.get();
        return current == null ? fallback : current.cachedSkin(username);
    }

    public int cacheSize() {
        SkinManager current = manager.get();
        return current == null ? 0 : current.cache().size();
    }

    public void saveNow() {
        SkinManager current = manager.get();
        if (current == null) {
            return;
        }
        synchronized (saveLock) {
            ProfileCache cache = current.cache();
            YamlConfiguration configuration = new YamlConfiguration();
            configuration.options().setHeader(List.of(
                    "PhantomPlayers resolved profile cache.",
                    "Entries contain only public Mojang texture data and lookup timestamps."
            ));
            ConfigurationSection section = configuration.createSection("entries");
            for (Map.Entry<String, CacheEntry> cached : cache.snapshot().entrySet()) {
                CacheEntry entry = cached.getValue();
                ConfigurationSection target = section.createSection(cached.getKey());
                target.set("value", entry.skin().value());
                target.set("signature", entry.skin().signature());
                target.set("status", entry.status().name());
                target.set("fetched-at", entry.fetchedAt());
            }
            try {
                AtomicFiles.write(cacheFile, configuration.saveToString());
            } catch (IOException exception) {
                plugin.getLogger().warning("Unable to save profile cache: " + exception.getMessage());
            }
        }
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        saveNow();
        SkinManager current = manager.getAndSet(null);
        if (current != null) {
            current.close();
        }
    }

    public Settings.Profiles profiles() {
        return profiles;
    }

    private void scheduleSave() {
        if (closed) {
            return;
        }
        if (!saveScheduled.compareAndSet(false, true)) {
            return;
        }
        try {
            scheduler.runAsync(() -> {
                saveScheduled.set(false);
                saveNow();
            });
        } catch (RuntimeException exception) {
            saveScheduled.set(false);
        }
    }

    private void onLookupWarning(String message) {
        metrics.skinLookupFailure();
        plugin.getLogger().warning(message);
    }

    private ProfileCache loadCache(Settings.Profiles settings) {
        ProfileCache cache = new ProfileCache(settings.cacheTtlMillis(), settings.negativeCacheMillis());
        if (!Files.exists(cacheFile)) {
            return cache;
        }
        YamlConfiguration configuration = new YamlConfiguration();
        try {
            configuration.load(cacheFile.toFile());
        } catch (IOException | InvalidConfigurationException exception) {
            plugin.getLogger().warning("Unable to read profile cache: " + exception.getMessage());
            return cache;
        }
        ConfigurationSection entries = configuration.getConfigurationSection("entries");
        if (entries == null) {
            return cache;
        }
        Map<String, CacheEntry> loaded = new LinkedHashMap<>();
        List<String> invalid = new ArrayList<>();
        for (String key : entries.getKeys(false)) {
            ConfigurationSection section = entries.getConfigurationSection(key);
            if (section == null || !NameRules.isValid(key)) {
                invalid.add(key);
                continue;
            }
            String statusText = section.getString("status", "ERROR");
            LookupStatus status;
            try {
                status = LookupStatus.valueOf(statusText.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                invalid.add(key);
                continue;
            }
            SkinData skin = new SkinData(section.getString("value", ""), section.getString("signature", ""));
            long fetchedAt = section.getLong("fetched-at", 0L);
            loaded.put(key, new CacheEntry(skin, status, fetchedAt));
        }
        if (!invalid.isEmpty()) {
            plugin.getLogger().warning("Profile cache: skipped " + invalid.size() + " invalid entr"
                    + (invalid.size() == 1 ? "y" : "ies"));
        }
        cache.putAll(loaded);
        cache.prune(System.currentTimeMillis());
        return cache;
    }
}
