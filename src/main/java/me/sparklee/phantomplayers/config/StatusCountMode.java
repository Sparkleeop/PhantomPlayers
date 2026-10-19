package me.sparklee.phantomplayers.config;

import java.util.Locale;

public enum StatusCountMode {
    ACTIVE,
    CONFIGURED,
    DISABLED;

    public static StatusCountMode parse(String value) {
        if (value == null) {
            return ACTIVE;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return ACTIVE;
        }
    }

    public String configName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
