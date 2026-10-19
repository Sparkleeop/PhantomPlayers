package me.sparklee.phantomplayers.config;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigLoaderTest {

    private static final Logger LOGGER = Logger.getLogger("phantom-test");
    static {
        LOGGER.setLevel(Level.OFF);
    }

    private static String resource(String name) throws Exception {
        try (InputStream stream = ConfigLoaderTest.class.getResourceAsStream(name)) {
            if (stream == null) {
                throw new IllegalStateException(name + " missing");
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void bundledConfigIsValid() throws Exception {
        ConfigLoader.LoadResult result = ConfigLoader.parse(resource("/config.yml"), LOGGER);
        assertFalse(result.hasErrors(), String.join("; ", result.errors()));
        assertEquals(1, result.settings().configVersion());
        assertEquals(100, result.settings().population().maxActive());
        assertEquals(200L, result.settings().presentation().syncIntervalTicks());
        assertTrue(result.settings().serverStatus().enabled());
        assertEquals(StatusCountMode.ACTIVE, result.settings().serverStatus().countMode());
        assertTrue(result.settings().presentation().gravity());
    }

    @Test
    void overridesAreAppliedAndMissingKeysUseDefaults() {
        ConfigLoader.LoadResult result = ConfigLoader.parse("""
                population:
                  max-active: 25
                presentation:
                  entity-view-distance: 128
                server-status:
                  count-mode: configured
                profiles:
                  cache-ttl-hours: 24
                """, LOGGER);
        assertFalse(result.hasErrors(), String.join("; ", result.errors()));
        assertEquals(25, result.settings().population().maxActive());
        assertEquals(128.0D, result.settings().presentation().entityViewDistance());
        assertEquals(StatusCountMode.CONFIGURED, result.settings().serverStatus().countMode());
        assertEquals(24L, result.settings().profiles().cacheTtlHours());
        assertEquals(10, result.settings().population().spawnBatchSize());
    }

    @Test
    void invalidValuesReportErrorsAndFallBackToDefaults() {
        ConfigLoader.LoadResult result = ConfigLoader.parse("""
                population:
                  max-active: 0
                  spawn-batch-size: -4
                presentation:
                  entity-view-distance: 1
                  sync-interval-ticks: 5
                server-status:
                  count-mode: nonsense
                  sample:
                    max-entries: 55
                profiles:
                  fallback-skin:
                    signature: only-signature
                chat:
                  delivery: packets
                """, LOGGER);
        assertTrue(result.hasErrors());
        assertTrue(result.errors().stream().anyMatch(error -> error.contains("max-active")));
        assertTrue(result.errors().stream().anyMatch(error -> error.contains("entity-view-distance")));
        assertTrue(result.errors().stream().anyMatch(error -> error.contains("count-mode")));
        assertTrue(result.errors().stream().anyMatch(error -> error.contains("signature")));
        assertTrue(result.errors().stream().anyMatch(error -> error.contains("chat.delivery")));
        assertEquals(100, result.settings().population().maxActive());
        assertEquals(96.0D, result.settings().presentation().entityViewDistance());
        assertEquals(StatusCountMode.ACTIVE, result.settings().serverStatus().countMode());
        assertFalse(result.settings().profiles().fallbackSkin().hasTexture());
        assertTrue(result.warnings().stream().anyMatch(warning -> warning.contains("max-entries")));
    }

    @Test
    void malformedYamlIsReported() {
        ConfigLoader.LoadResult result = ConfigLoader.parse("population: [", LOGGER);
        assertTrue(result.hasErrors());
    }
}
