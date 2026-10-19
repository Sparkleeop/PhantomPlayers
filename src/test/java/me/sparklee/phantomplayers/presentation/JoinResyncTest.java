package me.sparklee.phantomplayers.presentation;

import me.sparklee.phantomplayers.platform.Cancellable;
import me.sparklee.phantomplayers.platform.SchedulerService;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JoinResyncTest {

    private static final class FakeScheduler implements SchedulerService {
        private final List<Long> delays = new ArrayList<>();
        private final List<Runnable> tasks = new ArrayList<>();
        private RuntimeException failure;

        @Override
        public Cancellable runGlobal(Runnable task) {
            return Cancellable.NOOP;
        }

        @Override
        public Cancellable runGlobalLater(long delayTicks, Runnable task) {
            if (failure != null) {
                throw failure;
            }
            delays.add(delayTicks);
            tasks.add(task);
            return Cancellable.NOOP;
        }

        @Override
        public Cancellable runGlobalRepeating(long delayTicks, long periodTicks, Runnable task) {
            return Cancellable.NOOP;
        }

        @Override
        public Cancellable runAtLocation(World world, Location location, Runnable task) {
            return Cancellable.NOOP;
        }

        @Override
        public Cancellable runForPlayer(Player player, Runnable task) {
            return Cancellable.NOOP;
        }

        @Override
        public Cancellable runAsync(Runnable task) {
            return Cancellable.NOOP;
        }
    }

    private static Player player(boolean[] online) {
        return (Player) Proxy.newProxyInstance(JoinResyncTest.class.getClassLoader(),
                new Class<?>[]{Player.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "isOnline" -> online[0];
                    case "getName" -> "Viewer";
                    case "toString" -> "Viewer";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> null;
                });
    }

    @Test
    void retriesAreScheduledAfterJoin() {
        FakeScheduler scheduler = new FakeScheduler();
        JoinResync.schedule(scheduler, player(new boolean[]{true}), () -> {
        }, exception -> {
        });
        assertEquals(List.of(1L, 20L, 60L), scheduler.delays);
    }

    @Test
    void retriesRunOnlyWhileTheViewerIsOnline() {
        int[] runs = {0};
        boolean[] online = {true};
        FakeScheduler scheduler = new FakeScheduler();
        JoinResync.schedule(scheduler, player(online), () -> runs[0]++, exception -> {
        });

        scheduler.tasks.forEach(Runnable::run);
        assertEquals(3, runs[0]);

        online[0] = false;
        scheduler.tasks.forEach(Runnable::run);
        assertEquals(3, runs[0]);
    }

    @Test
    void schedulingFailureIsReportedAndStopsFurtherRetries() {
        FakeScheduler scheduler = new FakeScheduler();
        scheduler.failure = new IllegalStateException("scheduler unavailable");
        List<RuntimeException> failures = new ArrayList<>();

        JoinResync.schedule(scheduler, player(new boolean[]{true}), () -> {
        }, failures::add);

        assertEquals(1, failures.size());
        assertEquals(List.of(), scheduler.delays);
    }
}
