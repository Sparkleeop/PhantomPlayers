package me.sparklee.phantomplayers.config;

import me.sparklee.phantomplayers.skin.SkinData;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public final class ConfigLoader {

    private static final int MAX_SAMPLE_WARNING_THRESHOLD = 12;

    private ConfigLoader() {
    }

    public record LoadResult(Settings settings, List<String> errors, List<String> warnings, boolean missing) {
        public boolean hasErrors() {
            return !errors.isEmpty();
        }
    }

    public static LoadResult load(Path file, Logger logger) {
        if (!Files.exists(file)) {
            return new LoadResult(Settings.defaults(), List.of(), List.of(), true);
        }
        String content;
        try {
            content = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            List<String> errors = List.of("Unable to read " + file.getFileName() + ": " + exception.getMessage());
            return new LoadResult(Settings.defaults(), errors, List.of(), false);
        }
        return parse(content, logger);
    }

    public static LoadResult parse(String content, Logger logger) {
        YamlConfiguration configuration = new YamlConfiguration();
        try {
            configuration.loadFromString(content);
        } catch (InvalidConfigurationException exception) {
            List<String> errors = List.of("config.yml is not valid YAML: " + exception.getMessage());
            return new LoadResult(Settings.defaults(), errors, List.of(), false);
        }

        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        Settings defaults = Settings.defaults();

        int configVersion = integer(configuration, "config-version", defaults.configVersion(), 0, Integer.MAX_VALUE,
                errors);
        if (configVersion > Settings.CURRENT_VERSION) {
            errors.add("config-version " + configVersion + " is newer than supported version "
                    + Settings.CURRENT_VERSION + "; unknown keys are ignored");
        }

        int maxActive = integer(configuration, "population.max-active", 100, 1, 10_000, errors);
        int batchSize = integer(configuration, "population.spawn-batch-size", 10, 1, 1_000, errors);
        long batchDelay = longValue(configuration, "population.spawn-batch-delay-ticks", 20L, 0L, 72_000L, errors);
        int recentMemory = integer(configuration, "population.recent-name-memory", 50, 0, 10_000, errors);

        boolean spreadEnabled = bool(configuration, "population.spawn-spread.enabled", true, errors);
        double spreadRadius = decimal(configuration, "population.spawn-spread.radius", 6.0D, 0.0D, 128.0D, errors);

        String consoleWorld = string(configuration, "population.console-spawn.world", "world", errors);
        if (consoleWorld == null || consoleWorld.isBlank()) {
            errors.add("population.console-spawn.world must not be empty");
            consoleWorld = "world";
        }
        double consoleX = finiteDecimal(configuration, "population.console-spawn.x", 0.0D, errors);
        double consoleY = finiteDecimal(configuration, "population.console-spawn.y", 64.0D, errors);
        double consoleZ = finiteDecimal(configuration, "population.console-spawn.z", 0.0D, errors);
        float consoleYaw = (float) finiteDecimal(configuration, "population.console-spawn.yaw", 0.0D, errors);
        float consolePitch = (float) finiteDecimal(configuration, "population.console-spawn.pitch", 0.0D,
                errors);

        boolean tabList = bool(configuration, "presentation.tab-list", true, errors);
        double viewDistance = decimal(configuration, "presentation.entity-view-distance", 96.0D, 4.0D, 512.0D,
                errors);
        long syncInterval = longValue(configuration, "presentation.sync-interval-ticks", 200L, 20L, 72_000L,
                errors);
        boolean skinLayers = bool(configuration, "presentation.skin-layers", true, errors);
        boolean gravity = bool(configuration, "presentation.gravity", true, errors);

        boolean profilesEnabled = bool(configuration, "profiles.enabled", true, errors);
        long cacheTtlHours = longValue(configuration, "profiles.cache-ttl-hours", 168L, 1L, 8_760L, errors);
        long negativeCacheMinutes = longValue(configuration, "profiles.negative-cache-minutes", 10L, 0L, 10_080L,
                errors);
        int requestTimeout = integer(configuration, "profiles.request-timeout-seconds", 5, 1, 30, errors);
        int maxConcurrentRequests = integer(configuration, "profiles.max-concurrent-requests", 2, 1, 8, errors);
        String fallbackValue = string(configuration, "profiles.fallback-skin.value", "", errors);
        String fallbackSignature = string(configuration, "profiles.fallback-skin.signature", "", errors);
        if ((fallbackValue == null || fallbackValue.isBlank())
                && fallbackSignature != null && !fallbackSignature.isBlank()) {
            errors.add("profiles.fallback-skin.signature requires profiles.fallback-skin.value");
            fallbackSignature = "";
        }
        SkinData fallbackSkin = new SkinData(fallbackValue, fallbackSignature);

        boolean statusEnabled = bool(configuration, "server-status.enabled", true, errors);
        String countModeText = string(configuration, "server-status.count-mode", "active", errors);
        StatusCountMode countMode = StatusCountMode.parse(countModeText);
        if (countModeText != null && !countMode.name().equalsIgnoreCase(countModeText.trim())
                && !"active".equalsIgnoreCase(countModeText.trim())
                && !"configured".equalsIgnoreCase(countModeText.trim())
                && !"disabled".equalsIgnoreCase(countModeText.trim())) {
            errors.add("server-status.count-mode must be one of: active, configured, disabled");
        }
        boolean sampleEnabled = bool(configuration, "server-status.sample.enabled", true, errors);
        boolean includeRealPlayers = bool(configuration, "server-status.sample.include-real-players", true, errors);
        int maxSampleEntries = integer(configuration, "server-status.sample.max-entries", 12, 0, 100, errors);
        if (maxSampleEntries > MAX_SAMPLE_WARNING_THRESHOLD) {
            warnings.add("server-status.sample.max-entries is greater than " + MAX_SAMPLE_WARNING_THRESHOLD
                    + "; the vanilla client only renders a small subset");
        }

        String delivery = string(configuration, "chat.delivery", "broadcast", errors);
        if (delivery != null && !delivery.equalsIgnoreCase("broadcast")) {
            errors.add("chat.delivery must be \"broadcast\"; delivered lines use plain server broadcasts");
        }
        boolean broadcastToConsole = bool(configuration, "chat.broadcast-to-console", true, errors);

        boolean allowUntested = bool(configuration, "compatibility.allow-untested-adapters", true, errors);

        Settings settings = new Settings(
                configVersion,
                new Settings.Population(maxActive, batchSize, batchDelay, recentMemory,
                        new Settings.Spread(spreadEnabled, spreadRadius),
                        new Settings.ConsoleSpawn(consoleWorld, consoleX, consoleY, consoleZ, consoleYaw, consolePitch)),
                new Settings.Presentation(tabList, viewDistance, syncInterval, skinLayers, gravity),
                new Settings.Profiles(profilesEnabled, cacheTtlHours, negativeCacheMinutes, requestTimeout,
                        maxConcurrentRequests, fallbackSkin),
                new Settings.ServerStatus(statusEnabled, countMode, sampleEnabled, includeRealPlayers,
                        maxSampleEntries),
                new Settings.Chat(broadcastToConsole),
                new Settings.Compatibility(allowUntested)
        );
        for (String error : errors) {
            logger.warning("config.yml: " + error);
        }
        for (String warning : warnings) {
            logger.warning("config.yml: " + warning);
        }
        return new LoadResult(settings, List.copyOf(errors), List.copyOf(warnings), false);
    }

    private static int integer(YamlConfiguration configuration, String path, int fallback, int min, int max,
                               List<String> errors) {
        Object raw = configuration.get(path);
        if (raw == null) {
            return fallback;
        }
        if (!(raw instanceof Number number)) {
            errors.add(path + " must be a number; using " + fallback);
            return fallback;
        }
        int value = number.intValue();
        if (value < min || value > max) {
            errors.add(path + " must be between " + min + " and " + max + "; using " + fallback);
            return fallback;
        }
        return value;
    }

    private static long longValue(YamlConfiguration configuration, String path, long fallback, long min, long max,
                                  List<String> errors) {
        Object raw = configuration.get(path);
        if (raw == null) {
            return fallback;
        }
        if (!(raw instanceof Number number)) {
            errors.add(path + " must be a number; using " + fallback);
            return fallback;
        }
        long value = number.longValue();
        if (value < min || value > max) {
            errors.add(path + " must be between " + min + " and " + max + "; using " + fallback);
            return fallback;
        }
        return value;
    }

    private static double decimal(YamlConfiguration configuration, String path, double fallback, double min,
                                  double max, List<String> errors) {
        Object raw = configuration.get(path);
        if (raw == null) {
            return fallback;
        }
        if (!(raw instanceof Number number)) {
            errors.add(path + " must be a number; using " + fallback);
            return fallback;
        }
        double value = number.doubleValue();
        if (!Double.isFinite(value) || value < min || value > max) {
            errors.add(path + " must be between " + min + " and " + max + "; using " + fallback);
            return fallback;
        }
        return value;
    }

    private static double finiteDecimal(YamlConfiguration configuration, String path, double fallback,
                                        List<String> errors) {
        Object raw = configuration.get(path);
        if (raw == null) {
            return fallback;
        }
        if (!(raw instanceof Number number) || !Double.isFinite(number.doubleValue())) {
            errors.add(path + " must be a finite number; using " + fallback);
            return fallback;
        }
        return number.doubleValue();
    }

    private static boolean bool(YamlConfiguration configuration, String path, boolean fallback,
                                List<String> errors) {
        Object raw = configuration.get(path);
        if (raw == null) {
            return fallback;
        }
        if (!(raw instanceof Boolean value)) {
            errors.add(path + " must be true or false; using " + fallback);
            return fallback;
        }
        return value;
    }

    private static String string(YamlConfiguration configuration, String path, String fallback,
                                 List<String> errors) {
        Object raw = configuration.get(path);
        if (raw == null) {
            return fallback;
        }
        if (!(raw instanceof String value)) {
            errors.add(path + " must be a string; using \"" + fallback + "\"");
            return fallback;
        }
        return value;
    }
}
