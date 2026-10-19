package me.sparklee.phantomplayers.identity;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public final class UuidUtil {

    private static final String NAMESPACE = "PhantomPlayers:";

    private UuidUtil() {
    }

    public static UUID deterministic(String username) {
        return UUID.nameUUIDFromBytes((NAMESPACE + username).getBytes(StandardCharsets.UTF_8));
    }
}
