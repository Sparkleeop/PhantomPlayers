package me.sparklee.phantomplayers.population;

import me.sparklee.phantomplayers.identity.FakeIdentity;
import me.sparklee.phantomplayers.identity.IdentityManager;
import me.sparklee.phantomplayers.names.NamePool;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StatusSampleCacheTest {

    private static final Logger LOGGER = Logger.getLogger("phantom-test");

    private static FakeIdentity identity(String name) {
        return new FakeIdentity(name, UUID.nameUUIDFromBytes(name.getBytes()), true);
    }

    @Test
    void activeSamplesAreRebuiltOnlyWhenTheRegistryChanges() {
        PopulationRegistry registry = new PopulationRegistry(10);
        StatusSampleCache cache = new StatusSampleCache();
        assertTrue(cache.active(registry).isEmpty());

        registry.activate(identity("Alpha"), new SpawnPoint("world", 0.0D, 64.0D, 0.0D, 0.0F, 0.0F), 10);
        List<StatusSample> first = cache.active(registry);
        assertEquals(List.of("Alpha"), first.stream().map(StatusSample::name).toList());
        assertSame(first, cache.active(registry));

        registry.remove("Alpha");
        List<StatusSample> second = cache.active(registry);
        assertNotSame(first, second);
        assertTrue(second.isEmpty());
    }

    @Test
    void configuredSamplesFollowIdentityVersion(@TempDir Path folder) {
        IdentityManager identities = new IdentityManager(folder, LOGGER);
        identities.load(NamePool.empty());
        StatusSampleCache cache = new StatusSampleCache();
        assertTrue(cache.configured(identities).isEmpty());

        identities.ensure("Bravo");
        List<StatusSample> first = cache.configured(identities);
        assertEquals(List.of("Bravo"), first.stream().map(StatusSample::name).toList());
        assertSame(first, cache.configured(identities));

        identities.load(NamePool.empty());
        assertTrue(cache.configured(identities).isEmpty());
    }
}
