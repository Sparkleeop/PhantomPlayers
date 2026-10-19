package me.sparklee.phantomplayers.config;

import me.sparklee.phantomplayers.skin.SkinData;

public record Settings(
        int configVersion,
        Population population,
        Presentation presentation,
        Profiles profiles,
        Ping ping,
        ServerList serverList,
        Chat chat,
        Compatibility compatibility
) {

    public static final int CURRENT_VERSION = 1;

    public record Population(
            int maxActive,
            int spawnBatchSize,
            long spawnBatchDelayTicks,
            int recentNameMemory,
            Spread spread,
            ConsoleSpawn consoleSpawn
    ) {
    }

    public record Spread(boolean enabled, double radius) {
    }

    public record ConsoleSpawn(String world, double x, double y, double z, float yaw, float pitch) {
    }

    public record Presentation(
            boolean tabList,
            double entityViewDistance,
            long syncIntervalTicks,
            boolean skinLayers,
            boolean gravity
    ) {
    }

    public record Profiles(
            boolean enabled,
            long cacheTtlHours,
            long negativeCacheMinutes,
            int requestTimeoutSeconds,
            int maxConcurrentRequests,
            int retryAttempts,
            long retryBackoffMillis,
            SkinData fallbackSkin
    ) {
        public long cacheTtlMillis() {
            return cacheTtlHours * 3_600_000L;
        }

        public long negativeCacheMillis() {
            return negativeCacheMinutes * 60_000L;
        }
    }

    public record Ping(
            boolean enabled,
            int minimumMs,
            int maximumMs,
            int updateIntervalSeconds,
            boolean randomizePerPlayer,
            boolean variationEnabled
    ) {
        public int range() {
            return maximumMs - minimumMs + 1;
        }

        public boolean canRun() {
            return enabled && maximumMs >= minimumMs;
        }
    }

    public record ServerList(
            boolean enabled,
            boolean includeFakeCount,
            boolean includeFakeSampleNames,
            StatusCountMode countMode,
            boolean includeRealPlayers,
            boolean sampleEnabled,
            int sampleSize
    ) {
    }

    public record Chat(boolean broadcastToConsole) {
    }

    public record Compatibility(boolean allowUntestedAdapters) {
    }

    public static Settings defaults() {
        return new Settings(
                CURRENT_VERSION,
                new Population(
                        100,
                        10,
                        20L,
                        50,
                        new Spread(true, 6.0D),
                        new ConsoleSpawn("world", 0.0D, 64.0D, 0.0D, 0.0F, 0.0F)
                ),
                new Presentation(true, 96.0D, 200L, true, true),
                new Profiles(true, 168L, 10L, 5, 2, 2, 1_500L, SkinData.EMPTY),
                new Ping(true, 20, 220, 30, true, true),
                new ServerList(true, true, true, StatusCountMode.ACTIVE, true, true, 12),
                new Chat(true),
                new Compatibility(true)
        );
    }
}
