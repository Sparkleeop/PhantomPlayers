package me.sparklee.phantomplayers.metrics;

import java.util.concurrent.atomic.AtomicLong;

public final class Metrics {

    private final AtomicLong spawnAttempts = new AtomicLong();
    private final AtomicLong spawnSuccesses = new AtomicLong();
    private final AtomicLong spawnFailures = new AtomicLong();
    private final AtomicLong despawned = new AtomicLong();
    private final AtomicLong skinLookupFailures = new AtomicLong();
    private final AtomicLong chatLines = new AtomicLong();

    public void spawnAttempt() {
        spawnAttempts.incrementAndGet();
    }

    public void spawnSuccess() {
        spawnSuccesses.incrementAndGet();
    }

    public void spawnFailure() {
        spawnFailures.incrementAndGet();
    }

    public void despawned() {
        despawned.incrementAndGet();
    }

    public void skinLookupFailure() {
        skinLookupFailures.incrementAndGet();
    }

    public void chatLine() {
        chatLines.incrementAndGet();
    }

    public Snapshot snapshot() {
        return new Snapshot(spawnAttempts.get(), spawnSuccesses.get(), spawnFailures.get(),
                despawned.get(), skinLookupFailures.get(), chatLines.get());
    }

    public record Snapshot(long spawnAttempts, long spawnSuccesses, long spawnFailures,
                           long despawned, long skinLookupFailures, long chatLines) {
    }
}
