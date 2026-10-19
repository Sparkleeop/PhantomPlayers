package me.sparklee.phantomplayers.ping;

import me.sparklee.phantomplayers.config.Settings;
import me.sparklee.phantomplayers.names.NameRules;
import me.sparklee.phantomplayers.platform.Cancellable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

public final class PingManager {

    @FunctionalInterface
    public interface Scheduler {
        Cancellable later(long delayTicks, Runnable task);
    }

    @FunctionalInterface
    public interface Listener {
        void onPingChanged(Collection<String> names);
    }

    private final Scheduler scheduler;
    private final Listener listener;
    private final Random random;
    private final Map<String, Integer> values = new ConcurrentHashMap<>();

    private volatile Settings.Ping settings = Settings.defaults().ping();
    private volatile int shared;
    private Cancellable pending;
    private boolean stopped;

    public PingManager(Scheduler scheduler, Listener listener, Random random) {
        this.scheduler = scheduler;
        this.listener = listener;
        this.random = random;
    }

    public synchronized void apply(Settings.Ping next) {
        this.settings = next;
        cancelPending();
        values.clear();
        if (!next.canRun()) {
            return;
        }
        if (!next.randomizePerPlayer()) {
            shared = PingGenerator.pick(next, random, List.of());
        }
        if (next.variationEnabled()) {
            scheduleNext();
        }
    }

    public synchronized int allocate(String username) {
        Settings.Ping current = settings;
        String key = NameRules.key(username);
        if (!current.enabled()) {
            values.remove(key);
            return 0;
        }
        int value = current.randomizePerPlayer() ? PingGenerator.pick(current, random, values.values()) : shared;
        values.put(key, value);
        return value;
    }

    public synchronized void forget(String username) {
        values.remove(NameRules.key(username));
    }

    public synchronized int current(String username) {
        if (!settings.enabled()) {
            return 0;
        }
        Integer value = values.get(NameRules.key(username));
        if (value != null) {
            return value;
        }
        return settings.randomizePerPlayer() ? 0 : shared;
    }

    public synchronized int tracked() {
        return values.size();
    }

    public synchronized void stop() {
        stopped = true;
        cancelPending();
        values.clear();
    }

    private void scheduleNext() {
        Settings.Ping current = settings;
        if (stopped || !current.canRun() || !current.variationEnabled()
                || current.updateIntervalSeconds() <= 0) {
            return;
        }
        long baseTicks = current.updateIntervalSeconds() * 20L;
        long jitter = Math.max(1L, baseTicks / 4L);
        long delay = Math.max(1L, baseTicks - jitter + random.nextLong(jitter * 2L + 1L));
        try {
            pending = scheduler.later(delay, this::tick);
        } catch (RuntimeException ignored) {
            pending = null;
        }
    }

    private synchronized void tick() {
        pending = null;
        if (stopped) {
            return;
        }
        Settings.Ping current = settings;
        if (!current.canRun() || !current.variationEnabled()) {
            return;
        }
        List<String> changed = new ArrayList<>();
        if (current.randomizePerPlayer()) {
            for (Map.Entry<String, Integer> entry : values.entrySet()) {
                int next = PingGenerator.vary(entry.getValue(), current, random);
                if (next != entry.getValue()) {
                    entry.setValue(next);
                    changed.add(entry.getKey());
                }
            }
        } else {
            int next = PingGenerator.vary(shared, current, random);
            if (next != shared) {
                shared = next;
                for (String key : values.keySet()) {
                    values.put(key, next);
                    changed.add(key);
                }
            }
        }
        if (!changed.isEmpty()) {
            listener.onPingChanged(List.copyOf(changed));
        }
        scheduleNext();
    }

    private void cancelPending() {
        if (pending != null) {
            pending.cancel();
            pending = null;
        }
    }
}
