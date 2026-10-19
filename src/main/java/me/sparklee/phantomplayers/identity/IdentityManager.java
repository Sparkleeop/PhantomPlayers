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

/**
 * Builds the identity list from {@code names.txt} and applies the optional overrides in
 * {@code players.yml}. UUIDs are deterministic, so the override file never needs to contain the
 * full roster; it only records names the administrator disabled or gave a custom UUID.
 *
 * <p>The previous generated format ({@code identities:} sections) is still read once and
 * rewritten in the compact format on the next save.</p>
 */
public final class IdentityManager {

    private static final List<String> HEADER = List.of(
            "PhantomPlayers identity overrides (optional).",
            "",
            "names.txt is the spawn pool. This file only changes how individual",
            "names behave, and it can be deleted at any time.",
            "",
            "disabled:",
            "  Usernames excluded from /fp spawnall and configured counts.",
            "uuids:",
            "  Custom UUIDs for usernames. Values must be unique UUIDs."
    );

    private final Path file;
    private final Logger logger;
    private final Map<String, FakeIdentity> identities = new LinkedHashMap<>();
    private final Set<String> spawnPool = new LinkedHashSet<>();
    private final Set<String> disabled = new LinkedHashSet<>();
    private final Map<String, UUID> customUuids = new LinkedHashMap<>();
    private final Map<String, String> overrideNames = new LinkedHashMap<>();
    private boolean dirty;
    private long version;

    public IdentityManager(Path dataFolder, Logger logger) {
        this.file = dataFolder.resolve("players.yml");
        this.logger = logger;
    }

    public synchronized void load(NamePool pool) {
        identities.clear();
        spawnPool.clear();
        disabled.clear();
        customUuids.clear();
        overrideNames.clear();
        dirty = false;
        version++;

        if (Files.exists(file)) {
            YamlConfiguration configuration = YamlConfiguration.loadConfiguration(file.toFile());
            readLegacyIdentities(configuration);
            readOverrides(configuration);
        }

        for (String name : pool.names()) {
            String key = NameRules.key(name);
            spawnPool.add(key);
            overrideNames.putIfAbsent(key, name);
            addIdentity(name);
        }
        for (Map.Entry<String, String> override : overrideNames.entrySet()) {
            if (!identities.containsKey(override.getKey())) {
                addIdentity(override.getValue());
            }
        }
    }

    public synchronized FakeIdentity ensure(String name) {
        String key = NameRules.key(name);
        FakeIdentity existing = identities.get(key);
        if (existing != null) {
            return existing;
        }
        FakeIdentity created = new FakeIdentity(name,
                customUuids.getOrDefault(key, UuidUtil.deterministic(name)),
                !disabled.contains(key));
        identities.put(key, created);
        version++;
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

    public synchronized long version() {
        return version;
    }

    public synchronized boolean isDirty() {
        return dirty;
    }

    public synchronized void saveNow() throws IOException {
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.options().setHeader(HEADER);

        List<String> disabledNames = new ArrayList<>(disabled.size());
        for (String key : disabled) {
            disabledNames.add(displayName(key));
        }
        if (!disabledNames.isEmpty()) {
            configuration.set("disabled", disabledNames);
        }
        if (!customUuids.isEmpty()) {
            ConfigurationSection section = configuration.createSection("uuids");
            for (Map.Entry<String, UUID> entry : customUuids.entrySet()) {
                section.set(displayName(entry.getKey()), entry.getValue().toString());
            }
        }
        AtomicFiles.write(file, configuration.saveToString());
        dirty = false;
    }

    private void addIdentity(String name) {
        String key = NameRules.key(name);
        UUID uuid = customUuids.getOrDefault(key, UuidUtil.deterministic(name));
        identities.put(key, new FakeIdentity(name, uuid, !disabled.contains(key)));
    }

    private void readLegacyIdentities(YamlConfiguration configuration) {
        ConfigurationSection section = configuration.getConfigurationSection("identities");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            ConfigurationSection entry = section.getConfigurationSection(key);
            if (entry == null) {
                logger.warning("players.yml: identity \"" + key + "\" is not a section; dropped");
                dirty = true;
                continue;
            }
            String name = entry.getString("name");
            if (!NameRules.isValid(name)) {
                logger.warning("players.yml: identity \"" + key + "\" has an invalid username; dropped");
                dirty = true;
                continue;
            }
            String normalized = NameRules.key(name);
            String uuidText = entry.getString("uuid");
            if (uuidText != null) {
                try {
                    UUID uuid = UUID.fromString(uuidText);
                    if (!uuid.equals(UuidUtil.deterministic(name))) {
                        customUuids.put(normalized, uuid);
                    }
                } catch (RuntimeException exception) {
                    logger.warning("players.yml: identity \"" + name
                            + "\" has an invalid UUID; using the deterministic UUID");
                }
            }
            if (!entry.getBoolean("enabled", true)) {
                disabled.add(normalized);
            }
            overrideNames.putIfAbsent(normalized, name);
            dirty = true;
        }
    }

    private void readOverrides(YamlConfiguration configuration) {
        for (String raw : configuration.getStringList("disabled")) {
            if (!NameRules.isValid(raw)) {
                logger.warning("players.yml: disabled entry \"" + raw + "\" is not a valid username; ignored");
                dirty = true;
                continue;
            }
            String key = NameRules.key(raw);
            disabled.add(key);
            overrideNames.putIfAbsent(key, raw);
        }

        ConfigurationSection uuids = configuration.getConfigurationSection("uuids");
        if (uuids == null) {
            return;
        }
        for (String raw : uuids.getKeys(false)) {
            if (!NameRules.isValid(raw)) {
                logger.warning("players.yml: uuids entry \"" + raw + "\" is not a valid username; ignored");
                dirty = true;
                continue;
            }
            UUID uuid;
            try {
                uuid = UUID.fromString(String.valueOf(uuids.getString(raw)));
            } catch (RuntimeException exception) {
                logger.warning("players.yml: uuids entry \"" + raw + "\" is not a valid UUID; ignored");
                dirty = true;
                continue;
            }
            String key = NameRules.key(raw);
            if (!uuid.equals(UuidUtil.deterministic(raw))) {
                customUuids.put(key, uuid);
            }
            overrideNames.putIfAbsent(key, raw);
        }
    }

    private String displayName(String key) {
        FakeIdentity identity = identities.get(key);
        if (identity != null) {
            return identity.name();
        }
        return overrideNames.getOrDefault(key, key);
    }
}
