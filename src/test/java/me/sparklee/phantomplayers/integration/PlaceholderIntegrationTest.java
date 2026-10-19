package me.sparklee.phantomplayers.integration;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlaceholderIntegrationTest {

    @Test
    void absenceIsHandledWithoutTouchingPapi() {
        PlaceholderIntegration integration = new PlaceholderIntegration(false);
        AtomicInteger registrars = new AtomicInteger();
        assertFalse(integration.install(() -> PlaceholderStats.EMPTY, "1.0.0", expansion -> {
            registrars.incrementAndGet();
            return true;
        }));
        assertEquals(0, registrars.get());
        assertFalse(integration.installed());
        assertEquals("not installed", integration.describe());
    }

    @Test
    void installRegistersOnlyOnceEvenAcrossReloads() {
        PlaceholderIntegration integration = new PlaceholderIntegration(true);
        AtomicInteger registrars = new AtomicInteger();
        assertTrue(integration.install(() -> PlaceholderStats.EMPTY, "1.0.0", expansion -> {
            registrars.incrementAndGet();
            return true;
        }));
        assertTrue(integration.install(() -> PlaceholderStats.EMPTY, "1.0.0", expansion -> {
            registrars.incrementAndGet();
            return true;
        }));
        assertEquals(1, registrars.get());
        assertTrue(integration.installed());
        assertEquals("registered", integration.describe());
    }

    @Test
    void failedRegistrationIsReported() {
        PlaceholderIntegration integration = new PlaceholderIntegration(true);
        assertFalse(integration.install(() -> PlaceholderStats.EMPTY, "1.0.0", expansion -> false));
        assertFalse(integration.installed());
        assertEquals("present but not registered", integration.describe());
    }
}
