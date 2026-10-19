package me.sparklee.phantomplayers.platform;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class PaperSchedulerService implements SchedulerService {

    private final Plugin plugin;

    public PaperSchedulerService(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public Cancellable runGlobal(Runnable task) {
        return wrap(Bukkit.getGlobalRegionScheduler().run(plugin, scheduled -> task.run()));
    }

    @Override
    public Cancellable runGlobalLater(long delayTicks, Runnable task) {
        return wrap(Bukkit.getGlobalRegionScheduler().runDelayed(plugin, scheduled -> task.run(),
                Math.max(0L, delayTicks)));
    }

    @Override
    public Cancellable runGlobalRepeating(long delayTicks, long periodTicks, Runnable task) {
        return wrap(Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, scheduled -> task.run(),
                Math.max(0L, delayTicks), Math.max(1L, periodTicks)));
    }

    @Override
    public Cancellable runAtLocation(World world, Location location, Runnable task) {
        int chunkX = location.getBlockX() >> 4;
        int chunkZ = location.getBlockZ() >> 4;
        return wrap(Bukkit.getRegionScheduler().run(plugin, world, chunkX, chunkZ, scheduled -> task.run()));
    }

    @Override
    public Cancellable runForPlayer(Player player, Runnable task) {
        return wrap(player.getScheduler().run(plugin, scheduled -> task.run(), () -> {
        }));
    }

    @Override
    public Cancellable runAsync(Runnable task) {
        return wrap(Bukkit.getAsyncScheduler().runNow(plugin, scheduled -> task.run()));
    }

    private static Cancellable wrap(ScheduledTask task) {
        if (task == null) {
            return Cancellable.NOOP;
        }
        return new Cancellable() {
            @Override
            public void cancel() {
                task.cancel();
            }

            @Override
            public boolean isCancelled() {
                return task.isCancelled();
            }
        };
    }
}
