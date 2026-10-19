package me.sparklee.phantomplayers.chat;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatEngineTest {

    private static ChatConfig config() {
        return config(2);
    }

    private static ChatConfig config(int cooldownSeconds) {
        ChatSettings settings = new ChatSettings(true, 1, 1, cooldownSeconds, 1, 2);
        ChatTemplate template = new ChatTemplate("test", 1, List.of(
                "{player1}: hello",
                "{player2}: hi there"
        ));
        return new ChatConfig(settings, false, true, false, List.of(template));
    }

    private static final class FakeScheduler implements ChatEngine.Scheduler {

        private final PriorityQueue<Task> queue = new PriorityQueue<>(
                Comparator.comparingLong((Task task) -> task.due).thenComparingLong(task -> task.sequence));
        private long now;
        private long sequence;

        private final class Task implements ChatEngine.Cancellable {
            private long due;
            private final long period;
            private long sequence;
            private final Runnable runnable;
            private boolean cancelled;

            private Task(long due, long period, Runnable runnable) {
                this.due = due;
                this.period = period;
                this.sequence = FakeScheduler.this.sequence++;
                this.runnable = runnable;
            }

            @Override
            public void cancel() {
                cancelled = true;
            }
        }

        @Override
        public ChatEngine.Cancellable later(long delayTicks, Runnable task) {
            Task scheduled = new Task(now + Math.max(0L, delayTicks), 0L, task);
            queue.add(scheduled);
            return scheduled;
        }

        @Override
        public ChatEngine.Cancellable repeating(long periodTicks, Runnable task) {
            Task scheduled = new Task(now + Math.max(1L, periodTicks), Math.max(1L, periodTicks), task);
            queue.add(scheduled);
            return scheduled;
        }

        public void advance(long ticks) {
            long target = now + ticks;
            while (!queue.isEmpty() && queue.peek().due <= target) {
                Task task = queue.poll();
                now = Math.max(now, task.due);
                if (task.cancelled) {
                    continue;
                }
                task.runnable.run();
                if (task.period > 0 && !task.cancelled) {
                    task.due = now + task.period;
                    task.sequence = FakeScheduler.this.sequence++;
                    queue.add(task);
                }
            }
            now = target;
        }
    }

    private static final class FakeDelivery implements ChatEngine.Delivery {
        private final List<String> lines = new ArrayList<>();
        private boolean audience = true;

        @Override
        public boolean hasAudience() {
            return audience;
        }

        @Override
        public void broadcast(String line) {
            lines.add(line);
        }
    }

    private static final class Names implements ChatEngine.Names {
        private final List<String> names = new ArrayList<>(List.of("Alpha", "Bravo", "Charlie"));

        @Override
        public List<String> activeNames() {
            return List.copyOf(names);
        }
    }

    @Test
    void deliversConversationLinesThenHonorsCooldown() {
        FakeScheduler scheduler = new FakeScheduler();
        FakeDelivery delivery = new FakeDelivery();
        ChatEngine engine = new ChatEngine(config(), new Names(), delivery, scheduler, new Random(1));
        engine.start();
        scheduler.advance(20);
        assertTrue(delivery.lines.isEmpty());
        scheduler.advance(20);
        assertEquals(1, delivery.lines.size());
        scheduler.advance(20);
        assertEquals(2, delivery.lines.size());
        String firstConversation = delivery.lines.get(0);
        assertTrue(firstConversation.contains(": "));

        scheduler.advance(20);
        scheduler.advance(20);
        assertEquals(2, delivery.lines.size(), "cooldown must prevent starting during the cooldown window");
        scheduler.advance(20);
        assertEquals(3, delivery.lines.size());
        engine.stop();
    }

    @Test
    void stopsWhenAudienceDisappears() {
        FakeScheduler scheduler = new FakeScheduler();
        FakeDelivery delivery = new FakeDelivery();
        delivery.audience = false;
        ChatEngine engine = new ChatEngine(config(), new Names(), delivery, scheduler, new Random(2));
        engine.start();
        scheduler.advance(200);
        assertTrue(delivery.lines.isEmpty());
        delivery.audience = true;
        scheduler.advance(20);
        scheduler.advance(20);
        assertFalse(delivery.lines.isEmpty());
        delivery.audience = false;
        scheduler.advance(100);
        engine.stop();
    }

    @Test
    void abortsWhenSpeakerDespawns() {
        FakeScheduler scheduler = new FakeScheduler();
        FakeDelivery delivery = new FakeDelivery();
        Names names = new Names();
        ChatEngine engine = new ChatEngine(config(), names, delivery, scheduler, new Random(3));
        engine.start();
        scheduler.advance(20);
        scheduler.advance(20);
        assertEquals(1, delivery.lines.size());
        names.names.clear();
        names.names.add("Alpha");
        scheduler.advance(20);
        assertEquals(1, delivery.lines.size(), "conversation must abort when a required speaker is gone");
        engine.stop();
    }

    @Test
    void stopCancelsPendingLines() {
        FakeScheduler scheduler = new FakeScheduler();
        FakeDelivery delivery = new FakeDelivery();
        ChatEngine engine = new ChatEngine(config(), new Names(), delivery, scheduler, new Random(4));
        engine.start();
        scheduler.advance(20);
        engine.stop();
        scheduler.advance(1_000);
        assertTrue(delivery.lines.isEmpty());
        assertFalse(engine.isRunning());
    }

    @Test
    void disabledConfigNeverStarts() {
        FakeScheduler scheduler = new FakeScheduler();
        FakeDelivery delivery = new FakeDelivery();
        ChatConfig disabled = new ChatConfig(new ChatSettings(false, 1, 1, 0, 1, 2), true, true, true,
                config().templates());
        ChatEngine engine = new ChatEngine(disabled, new Names(), delivery, scheduler, new Random(5));
        engine.start();
        scheduler.advance(1_000);
        assertTrue(delivery.lines.isEmpty());
        engine.stop();
    }
}
