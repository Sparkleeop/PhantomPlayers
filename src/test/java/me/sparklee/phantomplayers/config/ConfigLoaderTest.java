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
        assertTrue(result.settings().serverList().enabled());
        assertEquals(StatusCountMode.ACTIVE, result.settings().serverList().countMode());
        assertTrue(result.settings().serverList().includeFakeCount());
        assertTrue(result.settings().serverList().includeFakeSampleNames());
        assertEquals(12, result.settings().serverList().sampleSize());
        assertTrue(result.settings().presentation().gravity());
        assertTrue(result.settings().ping().enabled());
        assertEquals(20, result.settings().ping().minimumMs());
        assertEquals(220, result.settings().ping().maximumMs());
        assertEquals(2, result.settings().profiles().retryAttempts());
        assertEquals(1_500L, result.settings().profiles().retryBackoffMillis());
    }

    @Test
    void overridesAreAppliedAndMissingKeysUseDefaults() {
        ConfigLoader.LoadResult result = ConfigLoader.parse("""
                population:
                  max-active: 25
                presentation:
                  entity-view-distance: 128
                server-list:
                  count-mode: configured
                  include-fake-count: false
                ping:
                  minimum-ms: 30
                  maximum-ms: 90
                  randomize-per-player: false
                profiles:
                  cache-ttl-hours: 24
                  retry-attempts: 0
                """, LOGGER);
        assertFalse(result.hasErrors(), String.join("; ", result.errors()));
        assertEquals(25, result.settings().population().maxActive());
        assertEquals(128.0D, result.settings().presentation().entityViewDistance());
        assertEquals(StatusCountMode.CONFIGURED, result.settings().serverList().countMode());
        assertFalse(result.settings().serverList().includeFakeCount());
        assertEquals(30, result.settings().ping().minimumMs());
        assertEquals(90, result.settings().ping().maximumMs());
        assertFalse(result.settings().ping().randomizePerPlayer());
        assertEquals(24L, result.settings().profiles().cacheTtlHours());
        assertEquals(0, result.settings().profiles().retryAttempts());
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
                server-list:
                  count-mode: nonsense
                  sample-size: 55
                ping:
                  minimum-ms: 500
                  maximum-ms: 100
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
        assertTrue(result.errors().stream().anyMatch(error -> error.contains("ping.minimum-ms")));
        assertTrue(result.errors().stream().anyMatch(error -> error.contains("signature")));
        assertTrue(result.errors().stream().anyMatch(error -> error.contains("chat.delivery")));
        assertEquals(100, result.settings().population().maxActive());
        assertEquals(96.0D, result.settings().presentation().entityViewDistance());
        assertEquals(StatusCountMode.ACTIVE, result.settings().serverList().countMode());
        assertEquals(20, result.settings().ping().minimumMs());
        assertEquals(220, result.settings().ping().maximumMs());
        assertFalse(result.settings().profiles().fallbackSkin().hasTexture());
        assertTrue(result.warnings().stream().anyMatch(warning -> warning.contains("sample-size")));
    }

    @Test
    void legacyServerStatusSectionIsStillSupported() {
        ConfigLoader.LoadResult result = ConfigLoader.parse("""
                server-status:
                  enabled: true
                  count-mode: configured
                  sample:
                    enabled: false
                    include-real-players: false
                    max-entries: 6
                """, LOGGER);
        assertFalse(result.hasErrors(), String.join("; ", result.errors()));
        assertTrue(result.settings().serverList().enabled());
        assertEquals(StatusCountMode.CONFIGURED, result.settings().serverList().countMode());
        assertFalse(result.settings().serverList().includeFakeSampleNames());
        assertFalse(result.settings().serverList().sampleEnabled());
        assertFalse(result.settings().serverList().includeRealPlayers());
        assertEquals(6, result.settings().serverList().sampleSize());
    }

    @Test
    void bundledConfigDeclaresNoJoinOrQuitMessageKeys() throws Exception {
        String config = resource("/config.yml");
        String chat = resource("/chat.yml");
        assertFalse(config.contains("join-message"));
        assertFalse(config.contains("quit-message"));
        assertFalse(config.contains("join_message"));
        assertFalse(config.contains("quit_message"));
        assertFalse(chat.contains("join-message"));
        assertFalse(chat.contains("quit-message"));
    }

    @Test
    void malformedYamlIsReported() {
        ConfigLoader.LoadResult result = ConfigLoader.parse("population: [", LOGGER);
        assertTrue(result.hasErrors());
    }
}
