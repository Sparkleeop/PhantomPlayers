package me.sparklee.phantomplayers.integration;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;

import java.util.Locale;
import java.util.function.Supplier;

public final class PhantomPlaceholders extends PlaceholderExpansion {

    private final Supplier<PlaceholderStats> stats;
    private final String version;

    public PhantomPlaceholders(Supplier<PlaceholderStats> stats, String version) {
        this.stats = stats;
        this.version = version;
    }

    @Override
    public String getIdentifier() {
        return "phantomplayers";
    }

    @Override
    public String getAuthor() {
        return "sparklee";
    }

    @Override
    public String getVersion() {
        return version;
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        PlaceholderStats snapshot = stats.get();
        if (snapshot == null) {
            snapshot = PlaceholderStats.EMPTY;
        }
        return switch (params.toLowerCase(Locale.ROOT)) {
            case "fake_online" -> Integer.toString(snapshot.fakeOnline());
            case "real_online" -> Integer.toString(snapshot.realOnline());
            case "total_online" -> Integer.toString(snapshot.totalOnline());
            case "fake_max" -> Integer.toString(snapshot.fakeMax());
            case "configured" -> Integer.toString(snapshot.configured());
            case "spawned" -> Integer.toString(snapshot.spawned());
            case "unspawned" -> Integer.toString(snapshot.unspawned());
            default -> null;
        };
    }
}
