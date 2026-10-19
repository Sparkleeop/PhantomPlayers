package me.sparklee.phantomplayers.presentation;

import com.destroystokyo.paper.event.server.PaperServerListPingEvent;
import me.sparklee.phantomplayers.config.Settings;
import me.sparklee.phantomplayers.identity.FakeIdentity;
import me.sparklee.phantomplayers.identity.IdentityManager;
import me.sparklee.phantomplayers.population.PopulationRegistry;
import me.sparklee.phantomplayers.population.StatusCounter;
import me.sparklee.phantomplayers.population.StatusSample;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public final class StatusListener implements Listener {

    private final Supplier<Settings> settings;
    private final PopulationRegistry registry;
    private final IdentityManager identities;

    public StatusListener(Supplier<Settings> settings, PopulationRegistry registry, IdentityManager identities) {
        this.settings = settings;
        this.registry = registry;
        this.identities = identities;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onServerListPing(PaperServerListPingEvent event) {
        Settings.ServerStatus status = settings.get().serverStatus();
        if (!status.enabled()) {
            return;
        }
        List<StatusSample> realSamples = new ArrayList<>();
        for (PaperServerListPingEvent.ListedPlayerInfo listed : event.getListedPlayers()) {
            if (listed.name() != null && listed.id() != null) {
                realSamples.add(new StatusSample(listed.name(), listed.id()));
            }
        }
        List<StatusSample> activeSamples = new ArrayList<>();
        for (var fake : registry.snapshot()) {
            activeSamples.add(new StatusSample(fake.identity().name(), fake.identity().uuid()));
        }
        List<StatusSample> configuredSamples = new ArrayList<>();
        for (FakeIdentity identity : identities.enabled()) {
            configuredSamples.add(new StatusSample(identity.name(), identity.uuid()));
        }

        StatusCounter.Plan plan = StatusCounter.plan(status.countMode(), event.getNumPlayers(), realSamples,
                activeSamples, configuredSamples, status.sampleEnabled(), status.includeRealPlayers(),
                status.maxSampleEntries());
        event.setNumPlayers(plan.totalCount());
        if (status.sampleEnabled()) {
            List<PaperServerListPingEvent.ListedPlayerInfo> listed = event.getListedPlayers();
            listed.clear();
            for (StatusSample sample : plan.samples()) {
                listed.add(new PaperServerListPingEvent.ListedPlayerInfo(sample.name(), sample.id()));
            }
        }
    }
}
