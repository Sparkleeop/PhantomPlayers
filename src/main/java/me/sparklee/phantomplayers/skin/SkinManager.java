package me.sparklee.phantomplayers.skin;

import me.sparklee.phantomplayers.names.NameRules;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Resolves skins asynchronously, deduplicates concurrent lookups, and caches
 * both successful and negative results. No method blocks the calling thread.
 */
public final class SkinManager implements AutoCloseable {

    private final ProfileCache cache;
    private final ProfileLookup lookup;
    private final Executor executor;
    private final Semaphore permits;
    private final LongSupplier clock;
    private final Supplier<SkinData> fallback;
    private final Consumer<String> warnings;
    private final Runnable cacheChanged;
    private final Map<String, CompletableFuture<SkinData>> inFlight = new ConcurrentHashMap<>();
    private final AtomicBoolean closed = new AtomicBoolean();

    public SkinManager(ProfileCache cache, ProfileLookup lookup, Executor executor, int maxConcurrentRequests,
                       LongSupplier clock, Supplier<SkinData> fallback, Consumer<String> warnings,
                       Runnable cacheChanged) {
        this.cache = cache;
        this.lookup = lookup;
        this.executor = executor;
        this.permits = new Semaphore(Math.max(1, maxConcurrentRequests));
        this.clock = clock;
        this.fallback = fallback;
        this.warnings = warnings;
        this.cacheChanged = cacheChanged;
    }

    public CompletableFuture<SkinData> resolve(String username) {
        Optional<CacheEntry> fresh = cache.get(username, clock.getAsLong());
        if (fresh.isPresent()) {
            return CompletableFuture.completedFuture(render(fresh.get().skin()));
        }
        if (closed.get()) {
            return CompletableFuture.completedFuture(fallback.get());
        }

        String key = NameRules.key(username);
        CompletableFuture<SkinData> future = new CompletableFuture<>();
        CompletableFuture<SkinData> existing = inFlight.putIfAbsent(key, future);
        if (existing != null) {
            return existing;
        }

        try {
            executor.execute(() -> performLookup(username, key, future));
        } catch (RuntimeException exception) {
            inFlight.remove(key, future);
            future.complete(fallback.get());
        }
        return future;
    }

    public SkinData cachedSkin(String username) {
        CacheEntry entry = cache.peek(username);
        if (entry == null) {
            return fallback.get();
        }
        return render(entry.skin());
    }

    public ProfileCache cache() {
        return cache;
    }

    private void performLookup(String username, String key, CompletableFuture<SkinData> future) {
        boolean acquired = false;
        try {
            if (closed.get()) {
                future.complete(fallback.get());
                return;
            }
            permits.acquire();
            acquired = true;

            Optional<CacheEntry> fresh = cache.get(username, clock.getAsLong());
            if (fresh.isPresent()) {
                future.complete(render(fresh.get().skin()));
                return;
            }

            LookupOutcome outcome;
            try {
                outcome = lookup.lookup(username);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                outcome = LookupOutcome.error("interrupted");
            } catch (Exception exception) {
                outcome = LookupOutcome.error(exception.getClass().getSimpleName()
                        + (exception.getMessage() == null ? "" : ": " + exception.getMessage()));
            }

            SkinData stored = outcome.status() == LookupStatus.FOUND ? outcome.skin() : SkinData.EMPTY;
            cache.put(username, new CacheEntry(stored, outcome.status(), clock.getAsLong()));
            cacheChanged.run();

            if (outcome.status() == LookupStatus.ERROR) {
                warnings.accept("Skin lookup failed for " + username + " (" + outcome.detail() + ")");
            }
            future.complete(render(stored));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            future.complete(fallback.get());
        } catch (RuntimeException exception) {
            warnings.accept("Unexpected skin lookup failure for " + username + ": " + exception.getMessage());
            future.complete(fallback.get());
        } finally {
            if (acquired) {
                permits.release();
            }
            inFlight.remove(key, future);
        }
    }

    private SkinData render(SkinData skin) {
        return skin != null && skin.hasTexture() ? skin : fallback.get();
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        for (Map.Entry<String, CompletableFuture<SkinData>> entry : inFlight.entrySet()) {
            entry.getValue().complete(fallback.get());
        }
        inFlight.clear();
        if (executor instanceof ExecutorService service) {
            service.shutdownNow();
        }
    }
}
