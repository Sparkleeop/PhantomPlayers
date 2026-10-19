package me.sparklee.phantomplayers.skin;

import me.sparklee.phantomplayers.config.Settings;
import me.sparklee.phantomplayers.metrics.Metrics;
import me.sparklee.phantomplayers.platform.Cancellable;
import me.sparklee.phantomplayers.platform.SchedulerService;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkinServiceReloadTest {

    private static final class FakeScheduler implements SchedulerService {
        @Override
        public Cancellable runGlobal(Runnable task) {
            return Cancellable.NOOP;
        }

        @Override
        public Cancellable runGlobalLater(long delayTicks, Runnable task) {
            return Cancellable.NOOP;
        }

        @Override
        public Cancellable runGlobalRepeating(long delayTicks, long periodTicks, Runnable task) {
            return Cancellable.NOOP;
        }

        @Override
        public Cancellable runAtLocation(World world, Location location, Runnable task) {
            return Cancellable.NOOP;
        }

        @Override
        public Cancellable runForPlayer(Player player, Runnable task) {
            return Cancellable.NOOP;
        }

        @Override
        public Cancellable runAsync(Runnable task) {
            task.run();
            return Cancellable.NOOP;
        }
    }

    private static Plugin plugin(Path folder) {
        Logger logger = Logger.getLogger("phantom-test");
        return (Plugin) Proxy.newProxyInstance(
                SkinServiceReloadTest.class.getClassLoader(),
                new Class<?>[]{Plugin.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getDataFolder" -> folder.toFile();
                    case "getLogger" -> logger;
                    case "toString" -> "TestPlugin";
                    default -> null;
                });
    }

    private static Settings.Profiles profiles(long ttlHours, long negativeMinutes) {
        return new Settings.Profiles(false, ttlHours, negativeMinutes, 5, 1, 1, 10L, SkinData.EMPTY);
    }

    @Test
    void reloadKeepsResolvedCacheEntries(@TempDir Path folder) throws Exception {
        SkinService service = new SkinService(plugin(folder), new FakeScheduler(), new Metrics());
        service.apply(profiles(1L, 10L));
        service.resolve("Ghost").get(2, TimeUnit.SECONDS);
        assertEquals(1, service.cacheSize());
        assertTrue(new File(folder.toFile(), "cache/profiles.yml").exists());

        service.apply(profiles(2L, 20L));
        assertEquals(1, service.cacheSize());
        assertEquals(SkinData.EMPTY, service.cached("Ghost"));
        service.close();
    }

    @Test
    void reapplyingIdenticalProfilesIsANoOp(@TempDir Path folder) throws Exception {
        SkinService service = new SkinService(plugin(folder), new FakeScheduler(), new Metrics());
        Settings.Profiles settings = profiles(1L, 10L);
        service.apply(settings);
        service.resolve("Ghost").get(2, TimeUnit.SECONDS);
        assertEquals(1, service.cacheSize());

        service.apply(settings);
        assertEquals(1, service.cacheSize());
        service.close();
    }
}
