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
                            boolean sampleEnabled, boolean includeRealPlayers, int maxSampleEntries) {
        int fakeCount = switch (mode) {
            case ACTIVE -> activeSamples.size();
            case CONFIGURED -> configuredSamples.size();
            case DISABLED -> 0;
        };
        int total = Math.max(0, realCount) + fakeCount;
        if (!sampleEnabled || maxSampleEntries <= 0) {
            return new Plan(total, fakeCount, List.of());
        }

        List<StatusSample> fakeSource = switch (mode) {
            case ACTIVE -> activeSamples;
            case CONFIGURED -> configuredSamples;
            case DISABLED -> List.of();
        };
        Map<String, StatusSample> unique = new LinkedHashMap<>();
        for (StatusSample sample : fakeSource) {
            if (unique.size() >= maxSampleEntries) {
                break;
            }
            unique.putIfAbsent(sample.name().toLowerCase(Locale.ROOT), sample);
        }
        if (includeRealPlayers) {
            for (StatusSample sample : realSamples) {
                if (unique.size() >= maxSampleEntries) {
                    break;
                }
                unique.putIfAbsent(sample.name().toLowerCase(Locale.ROOT), sample);
            }
        }
        return new Plan(total, fakeCount, List.copyOf(unique.values()));
    }
}
