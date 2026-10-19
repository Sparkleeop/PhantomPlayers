package me.sparklee.phantomplayers.identity;

import java.util.UUID;

public record FakeIdentity(String name, UUID uuid, boolean enabled) {
}
