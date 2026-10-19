package me.sparklee.phantomplayers.names;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

public final class NamePoolManager {

    private final Path file;
    private final Logger logger;

    public NamePoolManager(Path dataFolder, Logger logger) {
        this.file = dataFolder.resolve("names.txt");
        this.logger = logger;
    }

    public Path file() {
        return file;
    }

    public NamePool load() throws IOException {
        if (!Files.exists(file)) {
            return NamePool.empty();
        }
        NamePool pool = NamePool.parse(Files.readString(file, StandardCharsets.UTF_8));
        for (String warning : pool.warnings()) {
            logger.warning(warning);
        }
        return pool;
    }
}
