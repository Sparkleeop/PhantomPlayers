package me.sparklee.phantomplayers.population;

import me.sparklee.phantomplayers.identity.FakeIdentity;
import me.sparklee.phantomplayers.identity.UuidUtil;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PopulationRegistryTest {

    private static FakeIdentity identity(String name) {
        return new FakeIdentity(name, UuidUtil.deterministic(name), true);
    }

    private static SpawnPoint point() {
        return new SpawnPoint("world", 0.5D, 64.0D, 0.5D, 0.0F, 0.0F);
    }

    @Test
    void enforcesLimitAndRejectsDuplicates() {
        PopulationRegistry registry = new PopulationRegistry(10);
        assertTrue(registry.activate(identity("Alpha"), point(), 2).isPresent());
        assertTrue(registry.activate(identity("Bravo"), point(), 2).isPresent());
        assertTrue(registry.activate(identity("Charlie"), point(), 2).isEmpty());
        assertTrue(registry.activate(identity("Alpha"), point(), 2).isEmpty());
        assertEquals(2, registry.activeCount());
        assertEquals(List.of("Alpha", "Bravo"), registry.activeNames());
    }

    @Test
    void removeMovesNameIntoRecentMemory() {
        PopulationRegistry registry = new PopulationRegistry(2);
        registry.activate(identity("Alpha"), point(), 10);
        registry.activate(identity("Bravo"), point(), 10);
        assertTrue(registry.remove("alpha").isPresent());
        assertEquals(1, registry.activeCount());
        assertTrue(registry.recentKeys().contains("alpha"));
        assertTrue(registry.remove("alpha").isEmpty());
    }

    @Test
    void recentMemoryIsBoundedAndConfigurable() {
        PopulationRegistry registry = new PopulationRegistry(2);
        for (String name : List.of("Alpha", "Bravo", "Charlie", "Delta")) {
            registry.activate(identity(name), point(), 10);
            registry.remove(name);
        }
        assertEquals(2, registry.recentCount());
        assertTrue(registry.recentKeys().contains("delta"));
        registry.setRecentMemory(0);
        assertEquals(0, registry.recentCount());
    }

    @Test
    void removeAllClearsActiveSet() {
        PopulationRegistry registry = new PopulationRegistry(10);
        registry.activate(identity("Alpha"), point(), 10);
        registry.activate(identity("Bravo"), point(), 10);
        List<ActiveFakePlayer> removed = registry.removeAll();
        assertEquals(2, removed.size());
        assertEquals(0, registry.activeCount());
        assertFalse(registry.isActive("Alpha"));
        assertEquals(2, registry.recentCount());
    }
}
