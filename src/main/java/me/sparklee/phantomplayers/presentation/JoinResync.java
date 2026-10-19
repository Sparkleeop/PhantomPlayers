package me.sparklee.phantomplayers.presentation;

import me.sparklee.phantomplayers.platform.SchedulerService;
import org.bukkit.entity.Player;

import java.util.function.Consumer;

/**
 * Schedules the delayed presentation retries used after a player joins.
 *
 * <p>A joining client can ignore player-info or entity packets that arrive before it has finished
 * loading into the level, and the player's entity scheduler may not be running yet when
 * {@code PlayerJoinEvent} fires. Re-asserting the presentation on the global scheduler a few
 * ticks later covers both cases without touching entity state from a foreign thread.</p>
 */
final class JoinResync {

    static final long[] RETRY_DELAYS = {1L, 20L, 60L};

    private JoinResync() {
    }

    static void schedule(SchedulerService scheduler, Player viewer, Runnable retry,
                         Consumer<RuntimeException> failure) {
        for (long delay : RETRY_DELAYS) {
            try {
                scheduler.runGlobalLater(delay, () -> {
                    if (viewer.isOnline()) {
                        retry.run();
                    }
                });
            } catch (RuntimeException exception) {
                failure.accept(exception);
                return;
            }
        }
    }
}
