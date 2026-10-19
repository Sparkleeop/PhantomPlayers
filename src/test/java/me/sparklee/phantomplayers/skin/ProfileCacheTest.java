package me.sparklee.phantomplayers.skin;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProfileCacheTest {

    @Test
    void successfulEntriesUseSuccessTtl() {
        ProfileCache cache = new ProfileCache(1_000, 100);
        cache.put("PixelRanger", new CacheEntry(new SkinData("value", "signature"), LookupStatus.FOUND, 100));
        assertTrue(cache.get("PixelRanger", 500).isPresent());
        assertTrue(cache.get("PixelRanger", 1_200).isEmpty());
        assertTrue(cache.peek("PixelRanger") != null);
    }

    @Test
    void negativeEntriesUseNegativeTtl() {
        ProfileCache cache = new ProfileCache(1_000, 100);
        cache.put("Unknown", new CacheEntry(SkinData.EMPTY, LookupStatus.NOT_FOUND, 100));
        assertTrue(cache.get("Unknown", 150).isPresent());
        assertTrue(cache.get("Unknown", 300).isEmpty());
    }

    @Test
    void keysAreCaseInsensitive() {
        ProfileCache cache = new ProfileCache(1_000, 100);
        cache.put("PixelRanger", new CacheEntry(new SkinData("v", "s"), LookupStatus.FOUND, 100));
        assertTrue(cache.get("pixelranger", 200).isPresent());
        assertTrue(cache.get("PIXELRANGER", 200).isPresent());
    }

    @Test
    void pruneRemovesExpiredEntries() {
        ProfileCache cache = new ProfileCache(1_000, 100);
        cache.put("Old", new CacheEntry(SkinData.EMPTY, LookupStatus.ERROR, 0));
        cache.put("New", new CacheEntry(SkinData.EMPTY, LookupStatus.ERROR, 950));
        cache.prune(1_000);
        assertEquals(1, cache.size());
        assertTrue(cache.peek("New") != null);
    }

    @Test
    void putAllLoadsSnapshot() {
        ProfileCache cache = new ProfileCache(1_000, 100);
        cache.putAll(Map.of("One", new CacheEntry(new SkinData("v", ""), LookupStatus.FOUND, 10),
                "Two", new CacheEntry(SkinData.EMPTY, LookupStatus.NOT_FOUND, 10)));
        assertEquals(2, cache.size());
    }
}
