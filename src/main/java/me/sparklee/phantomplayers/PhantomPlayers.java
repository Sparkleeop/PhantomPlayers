package me.sparklee.phantomplayers;

import me.sparklee.phantomplayers.chat.ChatConfigLoader;
import me.sparklee.phantomplayers.chat.ChatManager;
import me.sparklee.phantomplayers.command.PhantomCommand;
import me.sparklee.phantomplayers.config.ConfigLoader;
import me.sparklee.phantomplayers.config.ConfigManager;
import me.sparklee.phantomplayers.config.Settings;
import me.sparklee.phantomplayers.identity.IdentityManager;
import me.sparklee.phantomplayers.metrics.Metrics;
import me.sparklee.phantomplayers.names.NamePool;
import me.sparklee.phantomplayers.names.NamePoolManager;
import me.sparklee.phantomplayers.platform.CompatibilityException;
import me.sparklee.phantomplayers.platform.NmsLoader;
import me.sparklee.phantomplayers.platform.NmsPackets;
import me.sparklee.phantomplayers.platform.PaperSchedulerService;
import me.sparklee.phantomplayers.platform.PlatformInfo;
import me.sparklee.phantomplayers.population.PopulationRegistry;
import me.sparklee.phantomplayers.population.SpawnService;
import me.sparklee.phantomplayers.presentation.PlayerConnectionListener;
import me.sparklee.phantomplayers.presentation.PresentationManager;
import me.sparklee.phantomplayers.presentation.StatusListener;
import me.sparklee.phantomplayers.skin.SkinService;
import me.sparklee.phantomplayers.util.Text;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public final class PhantomPlayers extends JavaPlugin {

    private final Metrics metrics = new Metrics();
    private final AtomicBoolean identitySaveScheduled = new AtomicBoolean();

    private PlatformInfo platformInfo;
    private ConfigManager configManager;
    private NamePoolManager namePoolManager;
    private IdentityManager identityManager;
    private PopulationRegistry registry;
    private PaperSchedulerService scheduler;
    private NmsPackets packets;
    private SkinService skins;
    private PresentationManager presentation;
    private ChatManager chatManager;
    private SpawnService spawnService;

    @Override
    public void onEnable() {
        if (!getDataFolder().exists() && !getDataFolder().mkdirs()) {
            getLogger().severe("Could not create plugin data directory.");
        }
        saveDefaultConfig();
        saveResourceIfMissing("names.txt");
        saveResourceIfMissing("chat.yml");

        platformInfo = PlatformInfo.detect();
        getLogger().info("Detected " + platformInfo.summary());

        configManager = new ConfigManager(getDataFolder().toPath(), getLogger());
        ConfigLoader.LoadResult configResult = configManager.loadInitial();
        if (configResult.hasErrors()) {
            getLogger().warning("config.yml contained " + configResult.errors().size()
                    + " problem(s); built-in defaults were used for those settings.");
        }

        namePoolManager = new NamePoolManager(getDataFolder().toPath(), getLogger());
        NamePool pool;
        try {
            pool = namePoolManager.load();
        } catch (IOException exception) {
            getLogger().severe("Unable to read names.txt: " + exception.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        if (pool.isEmpty()) {
            getLogger().warning("names.txt contains no usernames; spawnall will have nothing to spawn.");
        }

        identityManager = new IdentityManager(getDataFolder().toPath(), getLogger());
        identityManager.load(pool);
        if (identityManager.isDirty()) {
            try {
                identityManager.saveNow();
            } catch (IOException exception) {
                getLogger().warning("Unable to save players.yml: " + exception.getMessage());
            }
        }

        scheduler = new PaperSchedulerService(this);
        Settings settings = configManager.settings();
        registry = new PopulationRegistry(settings.population().recentNameMemory());

        skins = new SkinService(this, scheduler, metrics);
        skins.apply(settings.profiles());

        try {
            packets = NmsLoader.create(platformInfo, getLogger(),
                    settings.compatibility().allowUntestedAdapters());
        } catch (CompatibilityException exception) {
            getLogger().severe("PhantomPlayers cannot start on this server:");
            getLogger().severe(exception.getMessage());
            skins.close();
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        presentation = new PresentationManager(this, packets, scheduler, skins, settings);
        presentation.start();
        getServer().getPluginManager().registerEvents(new PlayerConnectionListener(presentation), this);
        getServer().getPluginManager().registerEvents(new StatusListener(this::getSettings, registry,
                identityManager), this);

        chatManager = new ChatManager(this, scheduler, registry, metrics);
        ChatConfigLoader.Result chatResult = chatManager.load();
        if (chatResult.hasErrors()) {
            for (String error : chatResult.errors()) {
                getLogger().warning("chat.yml: " + error);
            }
        }
        chatManager.apply(chatResult.config(), settings.chat().broadcastToConsole());

        spawnService = new SpawnService(configManager, registry, identityManager, presentation, skins, scheduler,
                metrics, this::requestIdentitySave);
        PhantomCommand command = new PhantomCommand(this, spawnService, registry, identityManager);
        PluginCommand pluginCommand = getCommand("fp");
        if (pluginCommand == null) {
            getLogger().severe("Command /fp is missing from plugin.yml; disabling.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        pluginCommand.setExecutor(command);
        pluginCommand.setTabCompleter(command);

        getLogger().info("Enabled with " + pool.size() + " pool names, " + identityManager.size()
                + " stored identities, max population " + settings.population().maxActive()
                + (chatManager.isRunning() ? ", simulated chat on" : ", simulated chat off") + ".");
    }

    @Override
    public void onDisable() {
        if (chatManager != null) {
            chatManager.stop();
        }
        if (presentation != null) {
            presentation.stop();
        }
        if (identityManager != null) {
            try {
                identityManager.saveNow();
            } catch (IOException exception) {
                getLogger().warning("Unable to save players.yml during shutdown: " + exception.getMessage());
            }
        }
        if (skins != null) {
            skins.close();
        }
        getLogger().info("Disabled.");
    }

    public void reloadAll(CommandSender sender) {
        ConfigLoader.LoadResult configResult = configManager.parse();
        ChatConfigLoader.Result chatResult = chatManager.load();
        if (configResult.hasErrors() || chatResult.hasErrors()) {
            reportErrors(sender, configResult.errors());
            reportErrors(sender, chatResult.errors());
            sender.sendMessage(Text.error("Reload aborted; no changes were applied."));
            return;
        }

        NamePool pool;
        try {
            pool = namePoolManager.load();
        } catch (IOException exception) {
            sender.sendMessage(Text.error("Unable to read names.txt: " + exception.getMessage()));
            return;
        }

        configManager.apply(configResult.settings());
        identityManager.load(pool);
        try {
            identityManager.saveNow();
        } catch (IOException exception) {
            getLogger().warning("Unable to save players.yml: " + exception.getMessage());
        }

        Settings next = configResult.settings();
        registry.setRecentMemory(next.population().recentNameMemory());
        presentation.updateSettings(next);
        skins.apply(next.profiles());
        chatManager.apply(chatResult.config(), next.chat().broadcastToConsole());
        presentation.syncAll();

        sender.sendMessage(Text.success("Reloaded configuration, " + pool.size() + " pool names, "
                + identityManager.size() + " identities, and " + chatResult.config().templates().size()
                + " chat templates."));
        if (!chatResult.config().canRun() && chatResult.config().settings().enabled()) {
            sender.sendMessage(Text.warn("Simulated chat is not running because no valid templates are loaded."));
        }
    }

    private void reportErrors(CommandSender sender, List<String> errors) {
        for (String error : errors) {
            sender.sendMessage(Text.error(error));
        }
    }

    private void saveResourceIfMissing(String resource) {
        File target = new File(getDataFolder(), resource);
        if (target.exists()) {
            return;
        }
        try {
            if (getResource(resource) == null) {
                getLogger().warning("Bundled resource " + resource + " is missing; skipping.");
                return;
            }
            saveResource(resource, false);
        } catch (IllegalArgumentException exception) {
            getLogger().warning("Unable to write default " + resource + ": " + exception.getMessage());
        }
    }

    public void requestIdentitySave() {
        if (!identitySaveScheduled.compareAndSet(false, true)) {
            return;
        }
        try {
            scheduler.runAsync(() -> {
                identitySaveScheduled.set(false);
                try {
                    identityManager.saveNow();
                } catch (IOException exception) {
                    getLogger().warning("Unable to save players.yml: " + exception.getMessage());
                }
            });
        } catch (RuntimeException exception) {
            identitySaveScheduled.set(false);
        }
    }

    public Settings settings() {
        return configManager.settings();
    }

    public Settings getSettings() {
        return settings();
    }

    public PlatformInfo platformInfo() {
        return platformInfo;
    }

    public NmsPackets packets() {
        return packets;
    }

    public Metrics metrics() {
        return metrics;
    }

    public SkinService skins() {
        return skins;
    }

    public ChatManager chatManager() {
        return chatManager;
    }

    public PopulationRegistry registry() {
        return registry;
    }

    public IdentityManager identities() {
        return identityManager;
    }

    public NamePoolManager namePoolManager() {
        return namePoolManager;
    }

    public ConfigManager configManager() {
        return configManager;
    }

    public SpawnService spawnService() {
        return spawnService;
    }
}
