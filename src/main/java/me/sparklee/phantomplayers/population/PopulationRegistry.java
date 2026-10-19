package me.sparklee.phantomplayers.population;

import me.sparklee.phantomplayers.identity.FakeIdentity;
import me.sparklee.phantomplayers.names.NameRules;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.HashSet;

public final class PopulationRegistry {

    private final Map<String, ActiveFakePlayer> active = new LinkedHashMap<>();
    private final Deque<String> recent = new ArrayDeque<>();
    private int recentMemory;

    public PopulationRegistry(int recentMemory) {
        this.recentMemory = Math.max(0, recentMemory);
    }

    public synchronized Optional<ActiveFakePlayer> activate(FakeIdentity identity, SpawnPoint point, int maxActive) {
        String key = NameRules.key(identity.name());
        if (active.containsKey(key) || active.size() >= maxActive) {
            return Optional.empty();
        }
        ActiveFakePlayer fake = new ActiveFakePlayer(identity, point);
        active.put(key, fake);
        remember(key);
        return Optional.of(fake);
    }

    public synchronized Optional<ActiveFakePlayer> remove(String name) {
        String key = NameRules.key(name);
        ActiveFakePlayer removed = active.remove(key);
        if (removed != null) {
            remember(key);
        }
        return Optional.ofNullable(removed);
    }

    public synchronized List<ActiveFakePlayer> removeAll() {
        List<ActiveFakePlayer> removed = new ArrayList<>(active.values());
        active.clear();
        for (ActiveFakePlayer fake : removed) {
            remember(NameRules.key(fake.identity().name()));
        }
        return List.copyOf(removed);
    }

    public synchronized Optional<ActiveFakePlayer> find(String name) {
        return Optional.ofNullable(active.get(NameRules.key(name)));
    }

    public synchronized boolean isActive(String name) {
        return active.containsKey(NameRules.key(name));
    }

    public synchronized int activeCount() {
        return active.size();
    }

    public synchronized List<ActiveFakePlayer> snapshot() {
        return List.copyOf(active.values());
    }

    public synchronized List<String> activeNames() {
        List<String> names = new ArrayList<>(active.size());
        for (ActiveFakePlayer fake : active.values()) {
            names.add(fake.identity().name());
        }
        return List.copyOf(names);
    }

    public synchronized Set<String> activeKeys() {
        return Set.copyOf(active.keySet());
    }

    public synchronized Set<String> recentKeys() {
        return new HashSet<>(recent);
    }

    public synchronized void setRecentMemory(int recentMemory) {
        this.recentMemory = Math.max(0, recentMemory);
        trimRecent();
    }

    public synchronized int recentCount() {
        return recent.size();
    }

    private void remember(String key) {
        if (recentMemory == 0) {
            return;
        }
        recent.remove(key);
        recent.addFirst(key);
        trimRecent();
    }

    private void trimRecent() {
        while (recent.size() > recentMemory) {
            recent.removeLast();
        }
    }
}
