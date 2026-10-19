package me.sparklee.phantomplayers.chat;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatConfigLoaderTest {

    @Test
    void bundledChatConfigIsValid() throws Exception {
        try (InputStream stream = getClass().getResourceAsStream("/chat.yml")) {
            assertTrue(stream != null);
            ChatConfigLoader.Result result = ChatConfigLoader.parse(
                    new String(stream.readAllBytes(), StandardCharsets.UTF_8));
            assertFalse(result.hasErrors(), String.join("; ", result.errors()));
            assertEquals(12, result.config().templates().size());
            assertTrue(result.config().canRun());
            assertTrue(result.config().settings().maxDelaySeconds()
                    >= result.config().settings().minDelaySeconds());
        }
    }

    @Test
    void parsesMinimalConfig() {
        ChatConfigLoader.Result result = ChatConfigLoader.parse("""
                settings:
                  enabled: true
                  min-delay-seconds: 2
                  max-delay-seconds: 4
                templates:
                  hello:
                    weight: 3
                    lines:
                      - "{player1}: hi"
                      - "{player2}: hello {player1}"
                """);
        assertFalse(result.hasErrors(), String.join("; ", result.errors()));
        assertEquals(1, result.config().templates().size());
        assertEquals(3, result.config().templates().get(0).weight());
        assertEquals(2, result.config().templates().get(0).lines().size());
    }

    @Test
    void invalidDelaysAndTemplatesAreReported() {
        ChatConfigLoader.Result result = ChatConfigLoader.parse("""
                settings:
                  min-delay-seconds: 10
                  max-delay-seconds: 2
                templates:
                  bad:
                    weight: 0
                    lines:
                      - "no placeholder here"
                      - "{player0}: invalid"
                  also-bad:
                    weight: 1
                    lines: []
                  good:
                    weight: 1
                    lines:
                      - "{player1}: ok"
                """);
        assertTrue(result.hasErrors());
        assertTrue(result.errors().stream().anyMatch(error -> error.contains("min-delay-seconds")));
        assertTrue(result.errors().stream().anyMatch(error -> error.contains("weight")));
        assertTrue(result.errors().stream().anyMatch(error -> error.contains("placeholder")));
        assertEquals(1, result.config().templates().size());
        assertEquals("good", result.config().templates().get(0).name());
    }

    @Test
    void missingTemplatesDisableChat() {
        ChatConfigLoader.Result result = ChatConfigLoader.parse("""
                settings:
                  enabled: true
                """);
        assertTrue(result.hasErrors());
        assertFalse(result.config().canRun());
    }

    @Test
    void malformedYamlIsReported() {
        ChatConfigLoader.Result result = ChatConfigLoader.parse("templates: [");
        assertTrue(result.hasErrors());
    }
}
