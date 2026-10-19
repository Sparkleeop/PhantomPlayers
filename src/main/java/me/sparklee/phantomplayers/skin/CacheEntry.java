package me.sparklee.phantomplayers.skin;

public record CacheEntry(SkinData skin, LookupStatus status, long fetchedAt) {

    public CacheEntry {
        skin = skin == null ? SkinData.EMPTY : skin;
        status = status == null ? LookupStatus.ERROR : status;
        fetchedAt = Math.max(0L, fetchedAt);
    }

    public boolean isFresh(long now, long successTtlMillis, long negativeTtlMillis) {
        long ttl = status == LookupStatus.FOUND ? successTtlMillis : negativeTtlMillis;
        return now >= fetchedAt && now - fetchedAt < ttl;
    }
}
