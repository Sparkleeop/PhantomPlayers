package me.sparklee.phantomplayers.skin;

import me.sparklee.phantomplayers.names.NameRules;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class ProfileCache {

    private long successTtlMillis;
    private long negativeTtlMillis;
    private final Map<String, CacheEntry> entries = new HashMap<>();

    public ProfileCache(long successTtlMillis, long negativeTtlMillis) {
        this.successTtlMillis = Math.max(0L, successTtlMillis);
        this.negativeTtlMillis = Math.max(0L, negativeTtlMillis);
    }

    public synchronized void setTtls(long successTtlMillis, long negativeTtlMillis) {
        this.successTtlMillis = Math.max(0L, successTtlMillis);
        this.negativeTtlMillis = Math.max(0L, negativeTtlMillis);
    }

    public synchronized Optional<CacheEntry> get(String username, long now) {
        CacheEntry entry = entries.get(NameRules.key(username));
        if (entry == null || !entry.isFresh(now, successTtlMillis, negativeTtlMillis)) {
            return Optional.empty();
        }
        return Optional.of(entry);
    }

    public synchronized CacheEntry peek(String username) {
        return entries.get(NameRules.key(username));
    }

    public synchronized void put(String username, CacheEntry entry) {
        entries.put(NameRules.key(username), entry);
    }

    public synchronized void putAll(Map<String, CacheEntry> loaded) {
        loaded.forEach((name, entry) -> entries.put(NameRules.key(name), entry));
    }

    public synchronized Map<String, CacheEntry> snapshot() {
        return Map.copyOf(entries);
    }

    public synchronized void prune(long now) {
        entries.entrySet().removeIf(entry -> !entry.getValue().isFresh(now, successTtlMillis, negativeTtlMillis));
    }

    public synchronized int size() {
        return entries.size();
    }
}
