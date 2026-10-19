package me.sparklee.phantomplayers.identity;

import me.sparklee.phantomplayers.names.NamePool;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IdentityManagerTest {

    private static final Logger LOGGER = Logger.getLogger("identity-test");
    static {
        LOGGER.setLevel(Level.OFF);
    }

    @Test
    void derivesIdentitiesFromNamesWithoutWritingAFile(@TempDir Path directory) {
        IdentityManager manager = new IdentityManager(directory, LOGGER);
        manager.load(NamePool.parse("Alpha\nBravo\n"));

        assertFalse(manager.isDirty());
        assertFalse(Files.exists(directory.resolve("players.yml")));
        assertEquals(2, manager.size());
        assertEquals(2, manager.spawnPool().size());
        assertEquals(2, manager.enabledCount());
        assertEquals(UuidUtil.deterministic("Alpha"), manager.get("Alpha").uuid());
    }

    @Test
    void ensuredNamesAreTransientAndDoNotTouchTheOverrideFile(@TempDir Path directory) {
        IdentityManager manager = new IdentityManager(directory, LOGGER);
        manager.load(NamePool.parse("Alpha\n"));

        FakeIdentity charlie = manager.ensure("Charlie");
        assertEquals(UuidUtil.deterministic("Charlie"), charlie.uuid());
        assertTrue(charlie.enabled());
        assertFalse(manager.isDirty());
        assertFalse(Files.exists(directory.resolve("players.yml")));
        assertEquals(1, manager.spawnPool().size());
        assertEquals(2, manager.size());
    }

    @Test
    void readsDisabledAndCustomUuidOverrides(@TempDir Path directory) throws Exception {
        UUID custom = UUID.fromString("11111111-2222-3333-4444-555555555555");
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("disabled", List.of("Bravo"));
        yaml.set("uuids.Alpha", custom.toString());
        yaml.save(directory.resolve("players.yml").toFile());

        IdentityManager manager = new IdentityManager(directory, LOGGER);
        manager.load(NamePool.parse("Alpha\nBravo\n"));

        assertFalse(manager.isDirty());
        assertEquals(custom, manager.get("Alpha").uuid());
        assertFalse(manager.get("Bravo").enabled());
        assertEquals(1, manager.enabledCount());
        assertEquals(2, manager.spawnPool().size());
    }

    @Test
    void overridesForNamesOutsideThePoolRemainAvailable(@TempDir Path directory) throws Exception {
        UUID custom = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("uuids.Ghost", custom.toString());
        yaml.save(directory.resolve("players.yml").toFile());

        IdentityManager manager = new IdentityManager(directory, LOGGER);
        manager.load(NamePool.parse("Alpha\n"));

        assertTrue(manager.contains("Ghost"));
        assertEquals(custom, manager.ensure("ghost").uuid());
        assertEquals(1, manager.spawnPool().size());
    }

    @Test
    void migratesLegacyGeneratedFileToCompactOverrides(@TempDir Path directory) throws Exception {
        UUID custom = UUID.fromString("12345678-1234-1234-1234-123456789abc");
        Path file = directory.resolve("players.yml");
        YamlConfiguration legacy = new YamlConfiguration();
        legacy.set("identities.alpha.name", "Alpha");
        legacy.set("identities.alpha.uuid", UuidUtil.deterministic("Alpha").toString());
        legacy.set("identities.alpha.enabled", true);
        legacy.set("identities.bravo.name", "Bravo");
        legacy.set("identities.bravo.uuid", custom.toString());
        legacy.set("identities.bravo.enabled", false);
        legacy.save(file.toFile());

        IdentityManager manager = new IdentityManager(directory, LOGGER);
        manager.load(NamePool.parse("Alpha\nBravo\n"));
        assertTrue(manager.isDirty());
        assertFalse(manager.get("Bravo").enabled());
        assertEquals(custom, manager.get("Bravo").uuid());

        manager.saveNow();
        assertFalse(manager.isDirty());

        YamlConfiguration migrated = YamlConfiguration.loadConfiguration(file.toFile());
        assertFalse(migrated.isConfigurationSection("identities"));
        assertEquals(List.of("Bravo"), migrated.getStringList("disabled"));
        assertEquals(custom.toString(), migrated.getString("uuids.Bravo"));
        assertEquals(null, migrated.getString("uuids.Alpha"));
    }

    @Test
    void invalidOverridesFallBackAndMarkTheFileForRewrite(@TempDir Path directory) throws Exception {
        Path file = directory.resolve("players.yml");
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("disabled", List.of("not a name!"));
        yaml.set("uuids.Alpha", "not-a-uuid");
        yaml.save(file.toFile());

        IdentityManager manager = new IdentityManager(directory, LOGGER);
        manager.load(NamePool.parse("Alpha\n"));

        assertTrue(manager.isDirty());
        assertEquals(UuidUtil.deterministic("Alpha"), manager.get("Alpha").uuid());
        assertTrue(manager.get("Alpha").enabled());

        manager.saveNow();
        YamlConfiguration rewritten = YamlConfiguration.loadConfiguration(file.toFile());
        assertTrue(rewritten.getStringList("disabled").isEmpty());
        assertFalse(rewritten.isConfigurationSection("uuids"));
    }
}
