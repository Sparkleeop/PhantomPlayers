package me.sparklee.phantomplayers.integration;

import java.util.function.Predicate;
import java.util.function.Supplier;

public final class PlaceholderIntegration {

    private final boolean available;
    private PhantomPlaceholders expansion;

    public PlaceholderIntegration(boolean available) {
        this.available = available;
    }

    public boolean available() {
        return available;
    }

    public boolean installed() {
        return expansion != null;
    }

    public boolean install(Supplier<PlaceholderStats> stats, String version,
                           Predicate<PhantomPlaceholders> registrar) {
        if (!available || expansion != null) {
            return expansion != null;
        }
        PhantomPlaceholders created = new PhantomPlaceholders(stats, version);
        if (registrar.test(created)) {
            expansion = created;
            return true;
        }
        return false;
    }

    public void uninstall() {
        PhantomPlaceholders current = expansion;
        expansion = null;
        if (current != null) {
            try {
                current.unregister();
            } catch (RuntimeException | LinkageError ignored) {
            }
        }
    }

    public String describe() {
        if (expansion != null) {
            return "registered";
        }
        return available ? "present but not registered" : "not installed";
    }
}
