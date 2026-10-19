package me.sparklee.phantomplayers.ping;

import me.sparklee.phantomplayers.config.Settings;
import me.sparklee.phantomplayers.platform.Cancellable;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PingManagerTest {

    private static final class FakeScheduler implements PingManager.Scheduler {
        private final List<Runnable> tasks = new ArrayList<>();
        private final List<Boolean> cancelled = new ArrayList<>();

        @Override
        public Cancellable later(long delayTicks, Runnable task) {
            tasks.add(task);
            int index = tasks.size() - 1;
            cancelled.add(false);
            return new Cancellable() {
                @Override
                public void cancel() {
                    cancelled.set(index, true);
                }

                @Override
                public boolean isCancelled() {
                    return cancelled.get(index);
                }
            };
        }
    }

    private static Settings.Ping ping(boolean randomize, boolean variation) {
        return new Settings.Ping(true, 40, 80, 30, randomize, variation);
    }

    @Test
    void disabledPingNeverSchedulesWork() {
        FakeScheduler scheduler = new FakeScheduler();
        PingManager manager = new PingManager(scheduler, names -> {
        }, new Random(1));
        manager.apply(new Settings.Ping(false, 20, 220, 30, true, true));
        assertTrue(scheduler.tasks.isEmpty());
        assertEquals(0, manager.allocate("Alpha"));
    }

    @Test
    void variationTaskIsRescheduledAndCancelledOnReapply() {
        FakeScheduler scheduler = new FakeScheduler();
        PingManager manager = new PingManager(scheduler, names -> {
        }, new Random(2));
        manager.apply(ping(true, true));
        assertEquals(1, scheduler.tasks.size());
        manager.apply(ping(true, true));
        assertEquals(2, scheduler.tasks.size());
        assertEquals(List.of(true, false), scheduler.cancelled);
        manager.stop();
        assertEquals(List.of(true, true), scheduler.cancelled);
    }

    @Test
    void variationNotifiesListenerForTrackedPlayers() {
        FakeScheduler scheduler = new FakeScheduler();
        List<String> changed = new ArrayList<>();
        PingManager manager = new PingManager(scheduler, changed::addAll, new Random(3));
        manager.apply(ping(true, true));
        manager.allocate("Alpha");
        manager.allocate("Bravo");
        scheduler.tasks.get(0).run();
        assertFalse(changed.isEmpty());
        assertTrue(changed.contains("alpha"));
        assertEquals(2, scheduler.tasks.size());
    }

    @Test
    void sharedValuesStayIdenticalAcrossPlayers() {
        PingManager manager = new PingManager(new FakeScheduler(), names -> {
        }, new Random(4));
        manager.apply(ping(false, false));
        int alpha = manager.allocate("Alpha");
        int bravo = manager.allocate("Bravo");
        assertEquals(alpha, bravo);
        assertTrue(alpha >= 40 && alpha <= 80);
    }

    @Test
    void allocationIsDistinctWhenRangeAllows() {
        PingManager manager = new PingManager(new FakeScheduler(), names -> {
        }, new Random(5));
        manager.apply(new Settings.Ping(true, 40, 44, 30, true, true));
        List<Integer> values = new ArrayList<>();
        for (String name : List.of("Alpha", "Bravo", "Charlie", "Delta", "Echo")) {
            values.add(manager.allocate(name));
        }
        assertEquals(5, values.stream().distinct().count());
    }

    @Test
    void forgettingPlayerDropsTrackedValue() {
        PingManager manager = new PingManager(new FakeScheduler(), names -> {
        }, new Random(6));
        manager.apply(ping(true, false));
        manager.allocate("Alpha");
        assertEquals(1, manager.tracked());
        manager.forget("Alpha");
        assertEquals(0, manager.tracked());
        assertEquals(0, manager.current("Alpha"));
    }

    @Test
    void reapplyReassignsValuesForExistingNames() {
        PingManager manager = new PingManager(new FakeScheduler(), names -> {
        }, new Random(7));
        manager.apply(new Settings.Ping(true, 40, 90, 30, true, false));
        int before = manager.allocate("Alpha");
        manager.apply(new Settings.Ping(true, 200, 210, 30, true, false));
        int after = manager.allocate("Alpha");
        assertNotEquals(before, after);
        assertTrue(after >= 200 && after <= 210);
    }
}
