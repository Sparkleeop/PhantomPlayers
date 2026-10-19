package me.sparklee.phantomplayers.identity;

import me.sparklee.phantomplayers.names.NamePool;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
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
    void createsDeterministicIdentitiesAndPersistsThem(@TempDir Path directory) throws Exception {
        NamePool pool = NamePool.parse("Alpha\nBravo\n");
        IdentityManager manager = new IdentityManager(directory, LOGGER);
        manager.load(pool);
        assertTrue(manager.isDirty());
        FakeIdentity alpha = manager.ensure("Alpha");
        assertEquals(UuidUtil.deterministic("Alpha"), alpha.uuid());
        assertEquals(UuidUtil.deterministic("Alpha"), manager.ensure("alpha").uuid());
        manager.ensure("Charlie");
        manager.saveNow();
        assertFalse(manager.isDirty());

        IdentityManager reloaded = new IdentityManager(directory, LOGGER);
        reloaded.load(pool);
        assertFalse(reloaded.isDirty());
        assertTrue(reloaded.contains("Charlie"));
        assertEquals(alpha.uuid(), reloaded.get("Alpha").uuid());
        assertEquals(3, reloaded.size());
        assertEquals(2, reloaded.spawnPool().size());
        assertTrue(Files.exists(directory.resolve("players.yml")));
    }

    @Test
    void preservesDisabledFlagsAndExtraIdentities(@TempDir Path directory) throws Exception {
        NamePool pool = NamePool.parse("Alpha\nBravo\n");
        IdentityManager manager = new IdentityManager(directory, LOGGER);
        manager.load(pool);
        manager.ensure("Charlie");
        manager.saveNow();

        Path file = directory.resolve("players.yml");
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file.toFile());
        yaml.set("identities.bravo.enabled", false);
        yaml.save(file.toFile());

        IdentityManager reloaded = new IdentityManager(directory, LOGGER);
        reloaded.load(pool);
        assertFalse(reloaded.get("Bravo").enabled());
        assertEquals(2, reloaded.enabledCount());
        assertEquals(3, reloaded.size());
        assertTrue(reloaded.contains("Charlie"));
    }

    @Test
    void regeneratesInvalidUuid(@TempDir Path directory) throws Exception {
        Path file = directory.resolve("players.yml");
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("identities.alpha.name", "Alpha");
        yaml.set("identities.alpha.uuid", "not-a-uuid");
        yaml.set("identities.alpha.enabled", true);
        yaml.save(file.toFile());

        IdentityManager manager = new IdentityManager(directory, LOGGER);
        manager.load(NamePool.parse("Alpha\n"));
        assertEquals(UuidUtil.deterministic("Alpha"), manager.get("Alpha").uuid());
        assertTrue(manager.isDirty());
    }
}
