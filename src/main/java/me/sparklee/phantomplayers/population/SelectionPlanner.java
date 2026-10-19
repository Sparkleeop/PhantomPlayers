package me.sparklee.phantomplayers.population;

import me.sparklee.phantomplayers.identity.FakeIdentity;
import me.sparklee.phantomplayers.names.NameRules;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

public final class SelectionPlanner {

    private SelectionPlanner() {
    }

    public static List<FakeIdentity> selectSpawnAll(List<FakeIdentity> candidates, Set<String> activeKeys,
                                                    Set<String> recentKeys, int capacity, Random random) {
        if (capacity <= 0) {
            return List.of();
        }
        List<FakeIdentity> fresh = new ArrayList<>();
        List<FakeIdentity> recent = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (FakeIdentity identity : candidates) {
            if (!identity.enabled()) {
                continue;
            }
            String key = NameRules.key(identity.name());
            if (activeKeys.contains(key) || !seen.add(key)) {
                continue;
            }
            if (recentKeys.contains(key)) {
                recent.add(identity);
            } else {
                fresh.add(identity);
            }
        }
        Collections.shuffle(fresh, random);
        Collections.shuffle(recent, random);

        List<FakeIdentity> selected = new ArrayList<>(Math.min(capacity, fresh.size() + recent.size()));
        for (FakeIdentity identity : fresh) {
            if (selected.size() >= capacity) {
                break;
            }
            selected.add(identity);
        }
        for (FakeIdentity identity : recent) {
            if (selected.size() >= capacity) {
                break;
            }
            selected.add(identity);
        }
        return List.copyOf(selected);
    }
}
