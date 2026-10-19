package me.sparklee.phantomplayers.population;

import me.sparklee.phantomplayers.identity.FakeIdentity;
import me.sparklee.phantomplayers.identity.UuidUtil;
import me.sparklee.phantomplayers.names.NameRules;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SelectionPlannerTest {

    private static FakeIdentity identity(String name, boolean enabled) {
        return new FakeIdentity(name, UuidUtil.deterministic(name), enabled);
    }

    @Test
    void skipsActiveDisabledAndDuplicates() {
        List<FakeIdentity> candidates = List.of(
                identity("Alpha", true),
                identity("Bravo", false),
                identity("alpha", true),
                identity("Charlie", true)
        );
        List<FakeIdentity> selected = SelectionPlanner.selectSpawnAll(candidates, Set.of("charlie"),
                Set.of(), 10, new Random(1));
        assertEquals(List.of("Alpha"), selected.stream().map(FakeIdentity::name).toList());
    }

    @Test
    void respectsCapacityAndPrefersRecentlyInactiveNames() {
        List<FakeIdentity> candidates = List.of(
                identity("Alpha", true),
                identity("Bravo", true),
                identity("Charlie", true),
                identity("Delta", true)
        );
        List<FakeIdentity> selected = SelectionPlanner.selectSpawnAll(candidates, Set.of(),
                Set.of("alpha", "bravo", "charlie"), 2, new Random(7));
        assertEquals(2, selected.size());
        Set<String> names = selected.stream().map(FakeIdentity::name)
                .map(NameRules::key).collect(Collectors.toSet());
        assertTrue(names.contains("delta"), "fresh name should be selected before recent names");
        assertFalse(names.contains("alpha") && names.contains("bravo") && names.contains("charlie"));
    }

    @Test
    void returnsEmptyWhenNoCapacity() {
        assertTrue(SelectionPlanner.selectSpawnAll(List.of(identity("Alpha", true)), Set.of(), Set.of(), 0,
                new Random(1)).isEmpty());
    }

    @Test
    void fallsBackToRecentNamesWhenNeeded() {
        List<FakeIdentity> candidates = List.of(identity("Alpha", true), identity("Bravo", true));
        List<FakeIdentity> selected = SelectionPlanner.selectSpawnAll(candidates, Set.of(),
                Set.of("alpha", "bravo"), 1, new Random(3));
        assertEquals(1, selected.size());
    }
}
