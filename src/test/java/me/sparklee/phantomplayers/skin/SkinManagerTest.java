package me.sparklee.phantomplayers.skin;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkinManagerTest {

    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private final AtomicLong clock = new AtomicLong(1_000L);
    private final List<String> warnings = new ArrayList<>();
    private final List<Long> backoffDelays = new ArrayList<>();
    private final AtomicInteger cacheChanges = new AtomicInteger();
    private SkinManager manager;

    @AfterEach
    void tearDown() {
        if (manager != null) {
            manager.close();
        }
        executor.shutdownNow();
    }

    private SkinManager manager(ProfileLookup lookup, SkinData fallback) {
        return manager(lookup, fallback, 0, 0L);
    }

    private SkinManager manager(ProfileLookup lookup, SkinData fallback, int retryAttempts, long retryBackoff) {
        manager = new SkinManager(new ProfileCache(60_000, 1_000), lookup, executor, 2, retryAttempts,
                retryBackoff, backoffDelays::add, clock::get, () -> fallback, warnings::add,
                cacheChanges::incrementAndGet);
        return manager;
    }

    @Test
    void deduplicatesConcurrentLookups() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        ProfileLookup lookup = name -> {
            calls.incrementAndGet();
            release.await(2, TimeUnit.SECONDS);
            return LookupOutcome.found(new SkinData("texture", "signature"));
        };
        SkinManager skinManager = manager(lookup, SkinData.EMPTY);
        CompletableFuture<SkinData> first = skinManager.resolve("PixelRanger");
        CompletableFuture<SkinData> second = skinManager.resolve("pixelranger");
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (calls.get() == 0 && System.nanoTime() < deadline) {
            Thread.sleep(5);
        }
        release.countDown();
        assertEquals("texture", first.get(2, TimeUnit.SECONDS).value());
        assertEquals("texture", second.get(2, TimeUnit.SECONDS).value());
        assertEquals(1, calls.get());
        assertEquals(1, cacheChanges.get());
        assertTrue(warnings.isEmpty());
    }

    @Test
    void usesCacheUntilExpiry() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        ProfileLookup lookup = name -> {
            calls.incrementAndGet();
            return LookupOutcome.found(new SkinData("v" + calls.get(), "s"));
        };
        SkinManager skinManager = manager(lookup, SkinData.EMPTY);
        assertEquals("v1", skinManager.resolve("Alpha").get(2, TimeUnit.SECONDS).value());
        assertEquals("v1", skinManager.resolve("Alpha").get(2, TimeUnit.SECONDS).value());
        assertEquals(1, calls.get());
        clock.addAndGet(61_000);
        assertEquals("v2", skinManager.resolve("Alpha").get(2, TimeUnit.SECONDS).value());
        assertEquals(2, calls.get());
    }

    @Test
    void cachesNegativeResultsAndUsesFallback() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        ProfileLookup lookup = name -> {
            calls.incrementAndGet();
            return LookupOutcome.notFound("missing");
        };
        SkinData fallback = new SkinData("fallback", "sig");
        SkinManager skinManager = manager(lookup, fallback);
        assertEquals("fallback", skinManager.resolve("Missing").get(2, TimeUnit.SECONDS).value());
        assertEquals("fallback", skinManager.resolve("Missing").get(2, TimeUnit.SECONDS).value());
        assertEquals(1, calls.get());
        assertTrue(skinManager.cachedSkin("Missing").hasTexture());
    }

    @Test
    void lookupErrorsWarnAndFallBack() throws Exception {
        ProfileLookup lookup = name -> {
            throw new IllegalStateException("boom");
        };
        SkinManager skinManager = manager(lookup, SkinData.EMPTY);
        SkinData result = skinManager.resolve("Broken").get(2, TimeUnit.SECONDS);
        assertEquals(SkinData.EMPTY, result);
        assertEquals(1, warnings.size());
        assertTrue(warnings.get(0).contains("Broken"));
    }

    @Test
    void retriesTransientErrorsWithExponentialBackoff() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        ProfileLookup lookup = name -> {
            if (calls.incrementAndGet() < 3) {
                return LookupOutcome.error("temporary outage");
            }
            return LookupOutcome.found(new SkinData("texture", "signature"));
        };
        SkinManager skinManager = manager(lookup, SkinData.EMPTY, 2, 100L);
        assertEquals("texture", skinManager.resolve("Flaky").get(2, TimeUnit.SECONDS).value());
        assertEquals(3, calls.get());
        assertEquals(List.of(100L, 200L), backoffDelays);
        assertTrue(warnings.isEmpty());
    }

    @Test
    void doesNotRetryMissingAccounts() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        ProfileLookup lookup = name -> {
            calls.incrementAndGet();
            return LookupOutcome.notFound("username is not registered");
        };
        SkinManager skinManager = manager(lookup, SkinData.EMPTY, 3, 50L);
        skinManager.resolve("Nobody").get(2, TimeUnit.SECONDS);
        assertEquals(1, calls.get());
        assertTrue(backoffDelays.isEmpty());
    }

    @Test
    void exhaustedRetriesFallBackAndWarnOnce() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        ProfileLookup lookup = name -> {
            calls.incrementAndGet();
            return LookupOutcome.error("still down");
        };
        SkinManager skinManager = manager(lookup, SkinData.EMPTY, 2, 10L);
        assertEquals(SkinData.EMPTY, skinManager.resolve("Broken").get(2, TimeUnit.SECONDS));
        assertEquals(3, calls.get());
        assertEquals(List.of(10L, 20L), backoffDelays);
        assertEquals(1, warnings.size());
    }

    @Test
    void prefilledCacheSkipsLookupEntirely() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        ProfileCache cache = new ProfileCache(60_000, 1_000);
        cache.put("Cached", new CacheEntry(new SkinData("cached", "sig"), LookupStatus.FOUND, clock.get()));
        manager = new SkinManager(cache, name -> {
            calls.incrementAndGet();
            return LookupOutcome.error("should not run");
        }, executor, 1, 0, 0L, backoffDelays::add, clock::get, () -> SkinData.EMPTY, warnings::add, () -> {
        });
        assertEquals("cached", manager.resolve("cached").get(2, TimeUnit.SECONDS).value());
        assertEquals(0, calls.get());
    }
}
