package me.sparklee.phantomplayers.config;

import me.sparklee.phantomplayers.skin.SkinData;

public record Settings(
        int configVersion,
        Population population,
        Presentation presentation,
        Profiles profiles,
        ServerStatus serverStatus,
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
            SkinData fallbackSkin
    ) {
        public long cacheTtlMillis() {
            return cacheTtlHours * 3_600_000L;
        }

        public long negativeCacheMillis() {
            return negativeCacheMinutes * 60_000L;
        }
    }

    public record ServerStatus(
            boolean enabled,
            StatusCountMode countMode,
            boolean sampleEnabled,
            boolean includeRealPlayers,
            int maxSampleEntries
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
                new Profiles(true, 168L, 10L, 5, 2, SkinData.EMPTY),
                new ServerStatus(true, StatusCountMode.ACTIVE, true, true, 12),
                new Chat(true),
                new Compatibility(true)
        );
    }
}
