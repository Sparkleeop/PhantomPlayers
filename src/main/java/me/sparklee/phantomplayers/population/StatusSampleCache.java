package me.sparklee.phantomplayers.population;

import me.sparklee.phantomplayers.identity.FakeIdentity;
import me.sparklee.phantomplayers.identity.IdentityManager;

import java.util.ArrayList;
import java.util.List;

public final class StatusSampleCache {

    private long registryVersion = Long.MIN_VALUE;
    private long identityVersion = Long.MIN_VALUE;
    private List<StatusSample> active = List.of();
    private List<StatusSample> configured = List.of();

    public synchronized List<StatusSample> active(PopulationRegistry registry) {
        long version = registry.version();
        if (version != registryVersion) {
            List<StatusSample> samples = new ArrayList<>(registry.activeCount());
            for (ActiveFakePlayer fake : registry.snapshot()) {
                samples.add(new StatusSample(fake.identity().name(), fake.identity().uuid()));
            }
            active = List.copyOf(samples);
            registryVersion = version;
        }
        return active;
    }

    public synchronized List<StatusSample> configured(IdentityManager identities) {
        long version = identities.version();
        if (version != identityVersion) {
            List<StatusSample> samples = new ArrayList<>();
            for (FakeIdentity identity : identities.enabled()) {
                samples.add(new StatusSample(identity.name(), identity.uuid()));
            }
            configured = List.copyOf(samples);
            identityVersion = version;
        }
        return configured;
    }
}
