package me.sparklee.phantomplayers.population;

import me.sparklee.phantomplayers.config.StatusCountMode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class StatusCounter {

    private StatusCounter() {
    }

    public record Plan(int totalCount, int fakeCount, List<StatusSample> samples) {
    }

    public static Plan plan(StatusCountMode mode, int realCount, List<StatusSample> realSamples,
                            List<StatusSample> activeSamples, List<StatusSample> configuredSamples,
                            boolean includeFakeCount, boolean includeFakeSamples, boolean includeRealPlayers,
                            int maxSampleEntries) {
        int fakeCount = includeFakeCount ? fakeSource(mode, activeSamples, configuredSamples).size() : 0;
        int total = Math.max(0, realCount) + fakeCount;
        if (maxSampleEntries <= 0 || !includeRealPlayers && !includeFakeSamples) {
            return new Plan(total, fakeCount, List.of());
        }

        List<StatusSample> fakeSource = includeFakeSamples
                ? fakeSource(mode, activeSamples, configuredSamples) : List.of();
        Map<String, StatusSample> unique = new LinkedHashMap<>();
        for (StatusSample sample : fakeSource) {
            if (unique.size() >= maxSampleEntries) {
                break;
            }
            unique.putIfAbsent(sample.name().toLowerCase(Locale.ROOT), sample);
        }
        if (includeRealPlayers) {
            List<StatusSample> realSource = realSamples == null ? List.of() : realSamples;
            for (StatusSample sample : realSource) {
                if (unique.size() >= maxSampleEntries) {
                    break;
                }
                unique.putIfAbsent(sample.name().toLowerCase(Locale.ROOT), sample);
            }
        }
        return new Plan(total, fakeCount, List.copyOf(unique.values()));
    }

    private static List<StatusSample> fakeSource(StatusCountMode mode, List<StatusSample> activeSamples,
                                                 List<StatusSample> configuredSamples) {
        return switch (mode) {
            case ACTIVE -> activeSamples == null ? List.of() : activeSamples;
            case CONFIGURED -> configuredSamples == null ? List.of() : configuredSamples;
            case DISABLED -> List.of();
        };
    }
}
