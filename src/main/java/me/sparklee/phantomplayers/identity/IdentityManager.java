package me.sparklee.phantomplayers.identity;

import me.sparklee.phantomplayers.names.NamePool;
import me.sparklee.phantomplayers.names.NameRules;
import me.sparklee.phantomplayers.util.AtomicFiles;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

public final class IdentityManager {

    private final Path file;
    private final Logger logger;
    private final Map<String, FakeIdentity> identities = new LinkedHashMap<>();
    private final Set<String> spawnPool = new LinkedHashSet<>();
    private boolean dirty;

    public IdentityManager(Path dataFolder, Logger logger) {
        this.file = dataFolder.resolve("players.yml");
        this.logger = logger;
    }

    public synchronized void load(NamePool pool) {
        identities.clear();
        spawnPool.clear();
        dirty = false;

        if (Files.exists(file)) {
            YamlConfiguration configuration = YamlConfiguration.loadConfiguration(file.toFile());
            ConfigurationSection section = configuration.getConfigurationSection("identities");
            if (section != null) {
                for (String key : section.getKeys(false)) {
                    ConfigurationSection entry = section.getConfigurationSection(key);
                    if (entry == null) {
                        logger.warning("players.yml: identity \"" + key + "\" is not a section; skipped");
                        continue;
                    }
                    String name = entry.getString("name");
                    String uuidText = entry.getString("uuid");
                    if (!NameRules.isValid(name)) {
                        logger.warning("players.yml: identity \"" + key + "\" has an invalid username; skipped");
                        continue;
                    }
                    UUID uuid;
                    try {
                        uuid = UUID.fromString(uuidText);
                    } catch (RuntimeException exception) {
                        uuid = UuidUtil.deterministic(name);
                        dirty = true;
                        logger.warning("players.yml: identity \"" + name + "\" has an invalid UUID; regenerated");
                    }
                    boolean enabled = entry.getBoolean("enabled", true);
                    String normalized = NameRules.key(name);
                    if (identities.putIfAbsent(normalized, new FakeIdentity(name, uuid, enabled)) != null) {
                        logger.warning("players.yml: duplicate identity \"" + name + "\" ignored");
                    }
                }
            }
        }

        for (String name : pool.names()) {
            String key = NameRules.key(name);
            spawnPool.add(key);
            if (!identities.containsKey(key)) {
                identities.put(key, new FakeIdentity(name, UuidUtil.deterministic(name), true));
                dirty = true;
            }
        }
    }

    public synchronized FakeIdentity ensure(String name) {
        String key = NameRules.key(name);
        FakeIdentity existing = identities.get(key);
        if (existing != null) {
            return existing;
        }
        FakeIdentity created = new FakeIdentity(name, UuidUtil.deterministic(name), true);
        identities.put(key, created);
        dirty = true;
        return created;
    }

    public synchronized FakeIdentity get(String name) {
        return identities.get(NameRules.key(name));
    }

    public synchronized boolean contains(String name) {
        return identities.containsKey(NameRules.key(name));
    }

    public synchronized List<FakeIdentity> all() {
        return List.copyOf(identities.values());
    }

    public synchronized List<FakeIdentity> spawnPool() {
        List<FakeIdentity> result = new ArrayList<>(spawnPool.size());
        for (String key : spawnPool) {
            FakeIdentity identity = identities.get(key);
            if (identity != null) {
                result.add(identity);
            }
        }
        return List.copyOf(result);
    }

    public synchronized List<FakeIdentity> enabled() {
        List<FakeIdentity> result = new ArrayList<>();
        for (FakeIdentity identity : identities.values()) {
            if (identity.enabled()) {
                result.add(identity);
            }
        }
        return List.copyOf(result);
    }

    public synchronized int enabledCount() {
        int count = 0;
        for (FakeIdentity identity : identities.values()) {
            if (identity.enabled()) {
                count++;
            }
        }
        return count;
    }

    public synchronized int size() {
        return identities.size();
    }

    public synchronized boolean isDirty() {
        return dirty;
    }

    public synchronized void saveNow() throws IOException {
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.options().setHeader(List.of(
                "PhantomPlayers persistent fake identities.",
                "UUIDs are deterministic and may be edited, but must remain unique.",
                "Set enabled to false to exclude an identity from spawnall and configured counts."
        ));
        ConfigurationSection section = configuration.createSection("identities");
        for (FakeIdentity identity : identities.values()) {
            ConfigurationSection entry = section.createSection(NameRules.key(identity.name()));
            entry.set("name", identity.name());
            entry.set("uuid", identity.uuid().toString());
            entry.set("enabled", identity.enabled());
        }
        AtomicFiles.write(file, configuration.saveToString());
        dirty = false;
    }
}
