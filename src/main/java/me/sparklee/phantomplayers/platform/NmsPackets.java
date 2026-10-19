package me.sparklee.phantomplayers.platform;

import me.sparklee.phantomplayers.skin.SkinData;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.UUID;

public interface NmsPackets {

    Object tabUpsert(Collection<TabEntry> entries);

    Object tabRemove(Collection<UUID> ids);

    Object addEntity(int entityId, UUID uuid, Location location);

    Object removeEntities(int[] ids);

    Object teleportEntity(int entityId, Location location);

    Object metadata(int entityId, byte skinParts);

    void send(Player viewer, Object packet);

    int allocateEntityId(World world, Location location);

    String description();

    record TabEntry(UUID uuid, String name, SkinData skin, boolean listed, int latency) {
    }
}
