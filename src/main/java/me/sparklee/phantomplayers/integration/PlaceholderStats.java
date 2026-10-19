package me.sparklee.phantomplayers.integration;

public record PlaceholderStats(
        int fakeOnline,
        int realOnline,
        int fakeMax,
        int configured,
        int spawned,
        int unspawned
) {

    public static final PlaceholderStats EMPTY = new PlaceholderStats(0, 0, 0, 0, 0, 0);

    public int totalOnline() {
        return fakeOnline + realOnline;
    }
}
