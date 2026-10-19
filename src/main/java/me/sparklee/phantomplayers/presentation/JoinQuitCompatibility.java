package me.sparklee.phantomplayers.presentation;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Display-only fake players are packet constructs: they have no ServerPlayer
 * and never fire PlayerJoinEvent/PlayerQuitEvent. Joining/leaving flows are
 * therefore observed here without emitting any announcement, which prevents
 * duplicated or phantom join/quit messages.
 */
public final class JoinQuitCompatibility {

    public static final String MECHANISM = "none";

    private final AtomicLong spawnObservations = new AtomicLong();
    private final AtomicLong despawnObservations = new AtomicLong();
    private final AtomicLong announcements = new AtomicLong();

    public void recordSpawn(String name) {
        if (name == null || name.isEmpty()) {
            return;
        }
        spawnObservations.incrementAndGet();
    }

    public void recordDespawn(String name) {
        if (name == null || name.isEmpty()) {
            return;
        }
        despawnObservations.incrementAndGet();
    }

    public long spawnObservations() {
        return spawnObservations.get();
    }

    public long despawnObservations() {
        return despawnObservations.get();
    }

    public long announcementsSent() {
        return announcements.get();
    }

    public String describe() {
        return "native join/quit events are unavailable for display-only fakes; no announcements are emitted";
    }
}
