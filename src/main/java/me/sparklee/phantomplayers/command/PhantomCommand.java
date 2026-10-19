package me.sparklee.phantomplayers.command;

import me.sparklee.phantomplayers.PhantomPlayers;
import me.sparklee.phantomplayers.identity.FakeIdentity;
import me.sparklee.phantomplayers.identity.IdentityManager;
import me.sparklee.phantomplayers.metrics.Metrics;
import me.sparklee.phantomplayers.population.ActiveFakePlayer;
import me.sparklee.phantomplayers.population.PopulationRegistry;
import me.sparklee.phantomplayers.population.SpawnService;
import me.sparklee.phantomplayers.util.Text;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class PhantomCommand implements CommandExecutor, TabCompleter {

    private static final int PAGE_SIZE = 10;

    private final PhantomPlayers plugin;
    private final SpawnService spawns;
    private final PopulationRegistry registry;
    private final IdentityManager identities;

    public PhantomCommand(PhantomPlayers plugin, SpawnService spawns, PopulationRegistry registry,
                          IdentityManager identities) {
        this.plugin = plugin;
        this.spawns = spawns;
        this.registry = registry;
        this.identities = identities;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            usage(sender);
            return true;
        }
        String subcommand = args[0].toLowerCase(Locale.ROOT);
        switch (subcommand) {
            case "spawn" -> {
                if (!permission(sender, "phantomplayers.spawn")) {
                    return true;
                }
                if (args.length != 2) {
                    sender.sendMessage(Text.error("Usage: /fp spawn <name>"));
                    return true;
                }
                spawns.spawn(args[1], sender);
            }
            case "spawnall" -> {
                if (permission(sender, "phantomplayers.spawn")) {
                    spawns.spawnAll(sender);
                }
            }
            case "despawn" -> {
                if (!permission(sender, "phantomplayers.despawn")) {
                    return true;
                }
                if (args.length != 2) {
                    sender.sendMessage(Text.error("Usage: /fp despawn <name>"));
                    return true;
                }
                spawns.despawn(args[1], sender);
            }
            case "despawnall" -> {
                if (permission(sender, "phantomplayers.despawnall")) {
                    spawns.despawnAll(sender);
                }
            }
            case "list" -> {
                if (!permission(sender, "phantomplayers.list")) {
                    return true;
                }
                if (args.length > 2) {
                    sender.sendMessage(Text.error("Usage: /fp list [page]"));
                    return true;
                }
                int page = 0;
                if (args.length == 2) {
                    try {
                        page = Integer.parseInt(args[1]);
                    } catch (NumberFormatException exception) {
                        sender.sendMessage(Text.error("Page must be a number."));
                        return true;
                    }
                    if (page < 1) {
                        sender.sendMessage(Text.error("Page must be at least 1."));
                        return true;
                    }
                }
                list(sender, page);
            }
            case "reload" -> {
                if (permission(sender, "phantomplayers.reload")) {
                    plugin.reloadAll(sender);
                }
            }
            default -> usage(sender);
        }
        return true;
    }

    private void list(CommandSender sender, int page) {
        List<ActiveFakePlayer> active = registry.snapshot();
        Metrics.Snapshot metrics = plugin.metrics().snapshot();
        sender.sendMessage(Text.highlight("PhantomPlayers population"));
        sender.sendMessage(Text.info("Active: " + active.size() + " / " + plugin.settings().population().maxActive()
                + "  |  Configured identities: " + identities.enabledCount()
                + "  |  Recent names: " + registry.recentCount()));
        sender.sendMessage(Text.info("Skins: " + plugin.skins().cacheSize() + " cached"
                + "  |  Lookup failures: " + metrics.skinLookupFailures()
                + "  |  Simulated chat lines: " + metrics.chatLines()));
        sender.sendMessage(Text.info("Spawns: " + metrics.spawnSuccesses() + " ok / "
                + metrics.spawnAttempts() + " attempts  |  Despawns: " + metrics.despawned()));
        sender.sendMessage(Text.info("Platform: " + plugin.platformInfo().summary()));
        sender.sendMessage(Text.info("Adapter: " + plugin.packets().description()));
        sender.sendMessage(Text.info("Chat: " + (plugin.chatManager().isRunning() ? "running" : "disabled")
                + "  |  " + plugin.chatManager().config().templates().size() + " templates"));

        if (page == 0) {
            if (!active.isEmpty()) {
                sender.sendMessage(Text.info("Use /fp list <page> to view active names."));
            }
            return;
        }
        int pages = Math.max(1, (active.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        if (page > pages) {
            sender.sendMessage(Text.error("Page " + page + " does not exist (1-" + pages + ")."));
            return;
        }
        int start = (page - 1) * PAGE_SIZE;
        int end = Math.min(active.size(), start + PAGE_SIZE);
        sender.sendMessage(Text.highlight("Active fake players - page " + page + "/" + pages));
        for (int index = start; index < end; index++) {
            ActiveFakePlayer fake = active.get(index);
            sender.sendMessage(Text.info((index + 1) + ". " + fake.identity().name() + "  "
                    + fake.location().world() + " " + Math.round(fake.location().x()) + ", "
                    + Math.round(fake.location().y()) + ", " + Math.round(fake.location().z())));
        }
    }

    private void usage(CommandSender sender) {
        sender.sendMessage(Text.highlight("PhantomPlayers commands"));
        if (sender.hasPermission("phantomplayers.spawn")) {
            sender.sendMessage(Text.info("/fp spawn <name> - spawn one fake player"));
            sender.sendMessage(Text.info("/fp spawnall - spawn the configured population"));
        }
        if (sender.hasPermission("phantomplayers.despawn")) {
            sender.sendMessage(Text.info("/fp despawn <name> - despawn one fake player"));
        }
        if (sender.hasPermission("phantomplayers.despawnall")) {
            sender.sendMessage(Text.info("/fp despawnall - despawn every fake player"));
        }
        if (sender.hasPermission("phantomplayers.list")) {
            sender.sendMessage(Text.info("/fp list [page] - population details"));
        }
        if (sender.hasPermission("phantomplayers.reload")) {
            sender.sendMessage(Text.info("/fp reload - reload all configuration files"));
        }
    }

    private boolean permission(CommandSender sender, String permission) {
        if (sender.hasPermission(permission) || sender.hasPermission("phantomplayers.admin")) {
            return true;
        }
        sender.sendMessage(Text.error("You do not have permission to do that."));
        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> options = new ArrayList<>();
            addIf(options, sender, "spawn", "phantomplayers.spawn");
            addIf(options, sender, "spawnall", "phantomplayers.spawn");
            addIf(options, sender, "despawn", "phantomplayers.despawn");
            addIf(options, sender, "despawnall", "phantomplayers.despawnall");
            addIf(options, sender, "list", "phantomplayers.list");
            addIf(options, sender, "reload", "phantomplayers.reload");
            return filter(options, args[0]);
        }
        if (args.length == 2) {
            String subcommand = args[0].toLowerCase(Locale.ROOT);
            if ("spawn".equals(subcommand) && sender.hasPermission("phantomplayers.spawn")) {
                List<String> options = new ArrayList<>();
                for (FakeIdentity identity : identities.spawnPool()) {
                    if (!registry.isActive(identity.name())) {
                        options.add(identity.name());
                    }
                }
                return filter(options, args[1]);
            }
            if ("despawn".equals(subcommand) && sender.hasPermission("phantomplayers.despawn")) {
                return filter(registry.activeNames(), args[1]);
            }
            if ("list".equals(subcommand) && sender.hasPermission("phantomplayers.list")) {
                int pages = Math.max(1, (registry.activeCount() + PAGE_SIZE - 1) / PAGE_SIZE);
                List<String> options = new ArrayList<>(pages);
                for (int page = 1; page <= pages; page++) {
                    options.add(Integer.toString(page));
                }
                return filter(options, args[1]);
            }
        }
        return List.of();
    }

    private static void addIf(List<String> options, CommandSender sender, String option, String permission) {
        if (sender.hasPermission(permission) || sender.hasPermission("phantomplayers.admin")) {
            options.add(option);
        }
    }

    private static List<String> filter(List<String> options, String prefix) {
        String normalized = prefix.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(normalized)) {
                result.add(option);
            }
        }
        return result;
    }
}
