package me.sparklee.phantomplayers.population;

import me.sparklee.phantomplayers.config.StatusCountMode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StatusCounterTest {

    private static StatusSample sample(String name) {
        return new StatusSample(name, UUID.nameUUIDFromBytes(name.getBytes()));
    }

    @Test
    void activeModeAddsLiveFakeCountAndPrefersFakeSamples() {
        StatusCounter.Plan plan = StatusCounter.plan(StatusCountMode.ACTIVE, 5,
                List.of(sample("RealOne"), sample("RealTwo")),
                List.of(sample("FakeOne"), sample("FakeTwo"), sample("FakeThree")),
                List.of(), true, true, 4);
        assertEquals(8, plan.totalCount());
        assertEquals(3, plan.fakeCount());
        assertEquals(List.of("FakeOne", "FakeTwo", "FakeThree", "RealOne"),
                plan.samples().stream().map(StatusSample::name).toList());
    }

    @Test
    void configuredModeUsesPersistedEnabledIdentities() {
        StatusCounter.Plan plan = StatusCounter.plan(StatusCountMode.CONFIGURED, 2,
                List.of(sample("RealOne")), List.of(sample("ActiveOne")),
                List.of(sample("SavedOne"), sample("SavedTwo")), true, true, 12);
        assertEquals(4, plan.totalCount());
        assertEquals(2, plan.fakeCount());
        assertEquals("SavedOne", plan.samples().get(0).name());
    }

    @Test
    void disabledModeLeavesRealPlayersOnly() {
        StatusCounter.Plan plan = StatusCounter.plan(StatusCountMode.DISABLED, 4,
                List.of(sample("RealOne")), List.of(sample("FakeOne")), List.of(sample("SavedOne")),
                true, true, 12);
        assertEquals(4, plan.totalCount());
        assertEquals(0, plan.fakeCount());
        assertEquals(List.of("RealOne"), plan.samples().stream().map(StatusSample::name).toList());
    }

    @Test
    void deduplicatesNamesAndHonorsMaxSamples() {
        StatusCounter.Plan plan = StatusCounter.plan(StatusCountMode.ACTIVE, 1,
                List.of(sample("RealOne")),
                List.of(sample("FakeOne"), sample("fakeone"), sample("FakeTwo")),
                List.of(), true, true, 2);
        assertEquals(2, plan.samples().size());
        assertEquals("FakeOne", plan.samples().get(0).name());
    }

    @Test
    void sampleCanBeDisabledWithoutChangingCount() {
        StatusCounter.Plan plan = StatusCounter.plan(StatusCountMode.ACTIVE, 3,
                List.of(sample("RealOne")), List.of(sample("FakeOne")), List.of(), false, true, 12);
        assertEquals(4, plan.totalCount());
        assertTrue(plan.samples().isEmpty());
    }

    @Test
    void realSamplesCanBeExcluded() {
        StatusCounter.Plan plan = StatusCounter.plan(StatusCountMode.ACTIVE, 1,
                List.of(sample("RealOne")), List.of(sample("FakeOne"), sample("FakeTwo")), List.of(),
                true, false, 12);
        assertEquals(List.of("FakeOne", "FakeTwo"),
                plan.samples().stream().map(StatusSample::name).toList());
    }
}
