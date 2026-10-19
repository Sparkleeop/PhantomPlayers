package me.sparklee.phantomplayers.ping;

import me.sparklee.phantomplayers.config.Settings;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PingGeneratorTest {

    private static Settings.Ping ping(int minimum, int maximum) {
        return new Settings.Ping(true, minimum, maximum, 30, true, true);
    }

    @Test
    void picksValuesInsideTheConfiguredRange() {
        Random random = new Random(7);
        for (int attempt = 0; attempt < 200; attempt++) {
            int value = PingGenerator.pick(ping(20, 220), random, List.of());
            assertTrue(value >= 20 && value <= 220);
        }
    }

    @Test
    void allocatesDistinctValuesWhenTheRangeAllowsIt() {
        Settings.Ping settings = ping(50, 54);
        Random random = new Random(11);
        Set<Integer> taken = new HashSet<>();
        for (int index = 0; index < 5; index++) {
            int value = PingGenerator.pick(settings, random, taken);
            taken.add(value);
        }
        assertEquals(5, taken.size());
        assertEquals(Set.of(50, 51, 52, 53, 54), taken);
    }

    @Test
    void toleratesExhaustedRange() {
        Settings.Ping settings = ping(50, 51);
        Random random = new Random(3);
        List<Integer> taken = new ArrayList<>(List.of(50, 51));
        for (int attempt = 0; attempt < 20; attempt++) {
            int value = PingGenerator.pick(settings, random, taken);
            assertTrue(value == 50 || value == 51);
        }
    }

    @Test
    void variationStaysInRangeAndChangesWhenPossible() {
        Settings.Ping settings = ping(10, 40);
        Random random = new Random(5);
        int current = 25;
        for (int attempt = 0; attempt < 100; attempt++) {
            int next = PingGenerator.vary(current, settings, random);
            assertTrue(next >= 10 && next <= 40);
            assertTrue(next != current);
            current = next;
        }
    }

    @Test
    void variationIsStableForSingleValueRange() {
        Settings.Ping settings = ping(42, 42);
        assertEquals(42, PingGenerator.vary(42, settings, new Random(1)));
    }
}
