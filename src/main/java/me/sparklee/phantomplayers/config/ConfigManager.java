package me.sparklee.phantomplayers.config;

import java.nio.file.Path;
import java.util.logging.Logger;

public final class ConfigManager {

    private final Path file;
    private final Logger logger;
    private volatile Settings settings = Settings.defaults();

    public ConfigManager(Path dataFolder, Logger logger) {
        this.file = dataFolder.resolve("config.yml");
        this.logger = logger;
    }

    public Settings settings() {
        return settings;
    }

    public Path file() {
        return file;
    }

    public ConfigLoader.LoadResult parse() {
        return ConfigLoader.load(file, logger);
    }

    public ConfigLoader.LoadResult loadInitial() {
        ConfigLoader.LoadResult result = parse();
        this.settings = result.settings();
        return result;
    }

    public void apply(Settings next) {
        this.settings = next;
    }
}
