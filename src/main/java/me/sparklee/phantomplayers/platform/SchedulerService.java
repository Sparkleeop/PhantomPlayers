package me.sparklee.phantomplayers.platform;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

public interface SchedulerService {

    Cancellable runGlobal(Runnable task);

    Cancellable runGlobalLater(long delayTicks, Runnable task);

    Cancellable runGlobalRepeating(long delayTicks, long periodTicks, Runnable task);

    Cancellable runAtLocation(World world, Location location, Runnable task);

    Cancellable runForPlayer(Player player, Runnable task);

    Cancellable runAsync(Runnable task);
}
