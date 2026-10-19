package me.sparklee.phantomplayers.ping;

import me.sparklee.phantomplayers.config.Settings;

import java.util.Collection;
import java.util.Random;

public final class PingGenerator {

    private static final int DISTINCT_ATTEMPTS = 16;

    private PingGenerator() {
    }

    public static int pick(Settings.Ping ping, Random random, Collection<Integer> taken) {
        int minimum = ping.minimumMs();
        int maximum = ping.maximumMs();
        int range = maximum - minimum + 1;
        if (range <= 0) {
            return Math.max(0, minimum);
        }
        if (ping.randomizePerPlayer() && range > taken.size()) {
            for (int attempt = 0; attempt < DISTINCT_ATTEMPTS; attempt++) {
                int candidate = minimum + random.nextInt(range);
                if (!taken.contains(candidate)) {
                    return candidate;
                }
            }
        }
        return minimum + random.nextInt(range);
    }

    public static int vary(int current, Settings.Ping ping, Random random) {
        int minimum = ping.minimumMs();
        int maximum = ping.maximumMs();
        if (maximum <= minimum) {
            return Math.max(0, minimum);
        }
        int spread = Math.max(1, Math.min(25, (maximum - minimum) / 4 + 1));
        int delta = random.nextInt(spread * 2 + 1) - spread;
        int next = Math.min(maximum, Math.max(minimum, current + delta));
        if (next == current) {
            next = current < maximum ? current + 1 : current - 1;
        }
        return Math.min(maximum, Math.max(minimum, next));
    }
}
