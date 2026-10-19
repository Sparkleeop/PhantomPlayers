package me.sparklee.phantomplayers.integration;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PhantomPlaceholdersTest {

    private static PhantomPlaceholders expansion(AtomicReference<PlaceholderStats> holder) {
        return new PhantomPlaceholders(holder::get, "1.0.0-test");
    }

    @Test
    void exposesAllDocumentedPlaceholders() {
        AtomicReference<PlaceholderStats> stats = new AtomicReference<>(
                new PlaceholderStats(7, 3, 100, 42, 7, 35));
        PhantomPlaceholders expansion = expansion(stats);
        assertEquals("phantomplayers", expansion.getIdentifier());
        assertEquals("1.0.0-test", expansion.getVersion());
        assertEquals("7", expansion.onRequest(null, "fake_online"));
        assertEquals("3", expansion.onRequest(null, "real_online"));
        assertEquals("10", expansion.onRequest(null, "total_online"));
        assertEquals("100", expansion.onRequest(null, "fake_max"));
        assertEquals("42", expansion.onRequest(null, "configured"));
        assertEquals("7", expansion.onRequest(null, "spawned"));
        assertEquals("35", expansion.onRequest(null, "unspawned"));
    }

    @Test
    void valuesAreReadLiveForReloadCompatibility() {
        AtomicReference<PlaceholderStats> stats = new AtomicReference<>(
                new PlaceholderStats(0, 1, 100, 5, 0, 5));
        PhantomPlaceholders expansion = expansion(stats);
        assertEquals("0", expansion.onRequest(null, "fake_online"));
        stats.set(new PlaceholderStats(5, 1, 100, 5, 5, 0));
        assertEquals("5", expansion.onRequest(null, "fake_online"));
        assertEquals("6", expansion.onRequest(null, "total_online"));
    }

    @Test
    void unknownPlaceholdersReturnNull() {
        PhantomPlaceholders expansion = expansion(new AtomicReference<>(PlaceholderStats.EMPTY));
        assertNull(expansion.onRequest(null, "nonsense"));
        assertFalse(expansion.onRequest(null, "FAKE_ONLINE").isEmpty());
    }

    @Test
    void missingStatsFallBackToZero() {
        PhantomPlaceholders expansion = expansion(new AtomicReference<>(null));
        assertEquals("0", expansion.onRequest(null, "fake_online"));
    }
}
