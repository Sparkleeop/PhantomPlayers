package me.sparklee.phantomplayers.platform;

import me.sparklee.phantomplayers.skin.SkinData;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

/**
 * Reflection-only mojmap packet adapter. No NMS types are referenced at
 * compile time so the plugin runs on both Paper and Folia without a
 * paperweight/userdev build.
 */
public final class MojmapPackets implements NmsPackets {

    private static final String GAME_PACKAGE = "net.minecraft.network.protocol.game.";
    private static final AtomicInteger FALLBACK_ENTITY_IDS = new AtomicInteger(1_500_000_000);

    private final Logger logger;
    private final Class<?> packetClass;
    private final Class<?> actionClass;
    private final Constructor<?> updateConstructor;
    private final Constructor<?> entryConstructor;
    private final Class<?> gameTypeClass;
    private final Object survival;
    private final Class<?> gameProfileClass;
    private final Constructor<?> gameProfileConstructor;
    private final Class<?> propertyClass;
    private final Constructor<?> propertyConstructor;
    private final Constructor<?> gameProfileWithPropertiesConstructor;
    private final Constructor<?> propertyMapConstructor;
    private final Method newMultimap;
    private final Method multimapPut;
    private final AtomicBoolean skinPropertyWarningLogged = new AtomicBoolean();
    private final Constructor<?> removeConstructor;
    private final Constructor<?> addEntityConstructor;
    private final Constructor<?> teleportConstructor;
    private final Constructor<?> positionMoveRotationConstructor;
    private final Constructor<?> vec3Constructor;
    private final Object playerType;
    private final Object zeroVelocity;
    private final Constructor<?> removeEntitiesConstructor;
    private final Constructor<?> setEntityDataConstructor;
    private final Method dataValueCreate;
    private final Object skinPartsAccessor;
    private final Method nextEntityIdStatic;
    private final Field connectionField;
    private final Method sendMethod;
    private volatile boolean fallbackIdWarningLogged;

    public MojmapPackets(Logger logger) throws CompatibilityException, ReflectiveOperationException {
        this.logger = logger;
        this.packetClass = Class.forName(GAME_PACKAGE + "ClientboundPlayerInfoUpdatePacket");
        this.actionClass = Class.forName(GAME_PACKAGE + "ClientboundPlayerInfoUpdatePacket$Action");
        Class<?> entryClass = Class.forName(GAME_PACKAGE + "ClientboundPlayerInfoUpdatePacket$Entry");
        Class<?> componentClass = Class.forName("net.minecraft.network.chat.Component");
        Class<?> chatSessionClass = Class.forName("net.minecraft.network.chat.RemoteChatSession$Data");
        this.gameTypeClass = Class.forName("net.minecraft.world.level.GameType");
        this.survival = staticField(gameTypeClass, "SURVIVAL");

        this.updateConstructor = packetClass.getConstructor(EnumSet.class, List.class);
        this.entryConstructor = entryClass.getConstructor(UUID.class, Class.forName("com.mojang.authlib.GameProfile"),
                boolean.class, int.class, gameTypeClass, componentClass, boolean.class, int.class, chatSessionClass);

        this.gameProfileClass = Class.forName("com.mojang.authlib.GameProfile");
        this.gameProfileConstructor = gameProfileClass.getConstructor(UUID.class, String.class);
        this.propertyClass = Class.forName("com.mojang.authlib.properties.Property");
        this.propertyConstructor = propertyClass.getConstructor(String.class, String.class, String.class);

        Constructor<?> profileWithProperties = null;
        Constructor<?> propertiesConstructor = null;
        Method multimapFactory = null;
        Method multimapPutMethod = null;
        try {
            Class<?> propertyMapClass = Class.forName("com.mojang.authlib.properties.PropertyMap");
            Class<?> multimapClass = Class.forName("com.google.common.collect.Multimap");
            propertiesConstructor = propertyMapClass.getConstructor(multimapClass);
            multimapFactory = Class.forName("com.google.common.collect.HashMultimap").getMethod("create");
            multimapPutMethod = multimapClass.getMethod("put", Object.class, Object.class);
            profileWithProperties = gameProfileClass.getConstructor(UUID.class, String.class, propertyMapClass);
        } catch (ReflectiveOperationException exception) {
            logger.warning("GameProfile texture properties are unavailable; skins will use fallbacks only");
        }
        this.gameProfileWithPropertiesConstructor = profileWithProperties;
        this.propertyMapConstructor = propertiesConstructor;
        this.newMultimap = multimapFactory;
        this.multimapPut = multimapPutMethod;

        Class<?> removePacket = Class.forName(GAME_PACKAGE + "ClientboundPlayerInfoRemovePacket");
        this.removeConstructor = removePacket.getConstructor(List.class);

        Class<?> addPacket = Class.forName(GAME_PACKAGE + "ClientboundAddEntityPacket");
        Class<?> entityTypeClass = Class.forName("net.minecraft.world.entity.EntityType");
        Class<?> vec3Class = Class.forName("net.minecraft.world.phys.Vec3");
        this.playerType = resolvePlayerType(entityTypeClass);
        this.zeroVelocity = staticField(vec3Class, "ZERO");
        this.addEntityConstructor = addPacket.getConstructor(int.class, UUID.class, double.class, double.class,
                double.class, float.class, float.class, entityTypeClass, int.class, vec3Class, double.class);
        this.vec3Constructor = vec3Class.getConstructor(double.class, double.class, double.class);

        Class<?> teleportPacket = Class.forName(GAME_PACKAGE + "ClientboundTeleportEntityPacket");
        Class<?> positionMoveRotationClass = Class.forName("net.minecraft.world.entity.PositionMoveRotation");
        this.positionMoveRotationConstructor = positionMoveRotationClass.getConstructor(vec3Class, vec3Class,
                float.class, float.class);
        this.teleportConstructor = teleportPacket.getConstructor(int.class, positionMoveRotationClass,
                java.util.Set.class, boolean.class);

        Class<?> removeEntitiesPacket = Class.forName(GAME_PACKAGE + "ClientboundRemoveEntitiesPacket");
        this.removeEntitiesConstructor = removeEntitiesPacket.getConstructor(int[].class);

        Class<?> dataPacket = Class.forName(GAME_PACKAGE + "ClientboundSetEntityDataPacket");
        this.setEntityDataConstructor = dataPacket.getConstructor(int.class, List.class);
        Class<?> dataValueClass = Class.forName("net.minecraft.network.syncher.SynchedEntityData$DataValue");
        Method create = null;
        for (Method method : dataValueClass.getMethods()) {
            if (method.getName().equals("create") && method.getParameterCount() == 2) {
                create = method;
                break;
            }
        }
        this.dataValueCreate = create;
        Object accessor = null;
        try {
            Class<?> avatarClass = Class.forName("net.minecraft.world.entity.Avatar");
            accessor = avatarClass.getField("DATA_PLAYER_MODE_CUSTOMISATION").get(null);
        } catch (ReflectiveOperationException exception) {
            logger.warning("Skin-layer metadata accessor is unavailable; skin layers will use client defaults");
        }
        this.skinPartsAccessor = accessor;

        Method nextId = null;
        try {
            nextId = Class.forName("net.minecraft.world.entity.Entity").getMethod("nextEntityId");
        } catch (NoSuchMethodException ignored) {
            // 26.3 moved entity ID allocation to ServerLevel.
        }
        this.nextEntityIdStatic = nextId;

        Class<?> serverPlayerClass = Class.forName("net.minecraft.server.level.ServerPlayer");
        this.connectionField = serverPlayerClass.getField("connection");
        Class<?> listenerClass = Class.forName("net.minecraft.server.network.ServerCommonPacketListenerImpl");
        this.sendMethod = findSend(listenerClass, packetClass);
        if (sendMethod == null) {
            throw new CompatibilityException("Could not locate ServerCommonPacketListenerImpl#send(Packet)");
        }
    }

    @Override
    public Object tabUpsert(Collection<TabEntry> entries) {
        EnumSet<?> actions = actions();
        List<Object> values = new ArrayList<>(entries.size());
        for (TabEntry entry : entries) {
            values.add(createEntry(entry));
        }
        try {
            return updateConstructor.newInstance(actions, values);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to construct player-info packet", exception);
        }
    }

    @Override
    public Object tabRemove(Collection<UUID> ids) {
        try {
            return removeConstructor.newInstance(new ArrayList<>(ids));
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to construct player-info remove packet", exception);
        }
    }

    @Override
    public Object addEntity(int entityId, UUID uuid, Location location) {
        try {
            return addEntityConstructor.newInstance(entityId, uuid,
                    location.getX(), location.getY(), location.getZ(),
                    location.getPitch(), location.getYaw(),
                    playerType, 0, zeroVelocity, (double) location.getYaw());
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to construct add-entity packet", exception);
        }
    }

    @Override
    public Object teleportEntity(int entityId, Location location) {
        try {
            Object position = vec3Constructor.newInstance(location.getX(), location.getY(), location.getZ());
            Object rotation = positionMoveRotationConstructor.newInstance(position, zeroVelocity,
                    location.getYaw(), location.getPitch());
            return teleportConstructor.newInstance(entityId, rotation, java.util.Set.of(), false);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to construct teleport-entity packet", exception);
        }
    }

    @Override
    public Object removeEntities(int[] ids) {
        try {
            return removeEntitiesConstructor.newInstance((Object) ids);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to construct remove-entities packet", exception);
        }
    }

    @Override
    public Object metadata(int entityId, byte skinParts) {
        if (skinPartsAccessor == null || dataValueCreate == null) {
            return null;
        }
        try {
            Object value = dataValueCreate.invoke(null, skinPartsAccessor, skinParts);
            List<Object> values = new ArrayList<>(1);
            values.add(value);
            return setEntityDataConstructor.newInstance(entityId, values);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to construct entity metadata packet", exception);
        }
    }

    @Override
    public void send(Player viewer, Object packet) {
        try {
            Object handle = viewer.getClass().getMethod("getHandle").invoke(viewer);
            Object connection = connectionField.get(handle);
            sendMethod.invoke(connection, packet);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            throw new IllegalStateException("Unable to send packet to " + viewer.getName(), exception);
        }
    }

    @Override
    public int allocateEntityId(World world, Location location) {
        if (nextEntityIdStatic != null) {
            try {
                return (int) nextEntityIdStatic.invoke(null);
            } catch (ReflectiveOperationException exception) {
                logger.warning("Entity.nextEntityId() failed; falling back to synthetic IDs: "
                        + exception.getMessage());
            }
        } else {
            try {
                Object handle = world.getClass().getMethod("getHandle").invoke(world);
                Method nextId = handle.getClass().getMethod("getNextEntityId");
                return (int) nextId.invoke(handle);
            } catch (ReflectiveOperationException exception) {
                if (!fallbackIdWarningLogged) {
                    fallbackIdWarningLogged = true;
                    logger.warning("ServerLevel#getNextEntityId() is unavailable; using synthetic entity IDs ("
                            + exception.getMessage() + ")");
                }
            }
        }
        return FALLBACK_ENTITY_IDS.incrementAndGet();
    }

    @Override
    public String description() {
        String entityIds = nextEntityIdStatic != null ? "Entity.nextEntityId"
                : "ServerLevel.getNextEntityId";
        return "mojmap packets (entityIds=" + entityIds
                + ", skins=" + (canAttachSkin() ? "on" : "fallback-only") + ")";
    }

    private Object createProfile(TabEntry entry) throws ReflectiveOperationException {
        SkinData skin = entry.skin();
        if (skin == null || !skin.hasTexture() || !canAttachSkin()) {
            return gameProfileConstructor.newInstance(entry.uuid(), entry.name());
        }
        try {
            Object property = propertyConstructor.newInstance("textures", skin.value(),
                    skin.signature().isEmpty() ? null : skin.signature());
            Object multimap = newMultimap.invoke(null);
            multimapPut.invoke(multimap, "textures", property);
            Object properties = propertyMapConstructor.newInstance(multimap);
            return gameProfileWithPropertiesConstructor.newInstance(entry.uuid(), entry.name(), properties);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            if (skinPropertyWarningLogged.compareAndSet(false, true)) {
                logger.warning("Unable to attach skin texture properties; using profiles without textures: "
                        + describe(exception));
            }
            return gameProfileConstructor.newInstance(entry.uuid(), entry.name());
        }
    }

    private boolean canAttachSkin() {
        return gameProfileWithPropertiesConstructor != null && propertyMapConstructor != null
                && newMultimap != null && multimapPut != null;
    }

    private static String describe(Exception exception) {
        Throwable cause = exception;
        while (cause instanceof InvocationTargetException invocation && invocation.getCause() != null) {
            cause = invocation.getCause();
        }
        String message = cause.getMessage();
        return cause.getClass().getSimpleName() + (message == null ? "" : ": " + message);
    }

    private Object createEntry(TabEntry entry) {
        try {
            Object profile = createProfile(entry);
            return entryConstructor.newInstance(entry.uuid(), profile, entry.listed(),
                    Math.max(0, entry.latency()), survival, null, false, 0, null);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to construct player-info entry for " + entry.name(), exception);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private EnumSet<?> actions() {
        EnumSet set = EnumSet.noneOf((Class<? extends Enum>) actionClass);
        for (String action : List.of("ADD_PLAYER", "UPDATE_LISTED", "UPDATE_GAME_MODE", "UPDATE_LATENCY",
                "UPDATE_DISPLAY_NAME", "UPDATE_LIST_ORDER", "UPDATE_HAT")) {
            set.add(Enum.valueOf((Class) actionClass, action));
        }
        return set;
    }

    private static Object resolvePlayerType(Class<?> entityTypeClass) throws ReflectiveOperationException {
        NoSuchFieldException missing = null;
        for (String holder : List.of("net.minecraft.world.entity.EntityType",
                "net.minecraft.world.entity.EntityTypes")) {
            try {
                return staticField(Class.forName(holder), "PLAYER");
            } catch (NoSuchFieldException exception) {
                missing = exception;
            } catch (ClassNotFoundException ignored) {
            }
        }
        throw missing != null ? missing : new NoSuchFieldException("PLAYER");
    }

    private static Object staticField(Class<?> type, String name) throws ReflectiveOperationException {
        try {
            return type.getField(name).get(null);
        } catch (NoSuchFieldException exception) {
            Field field = type.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(null);
        }
    }

    private static Method findSend(Class<?> listenerClass, Class<?> packetClass) {
        for (Method method : listenerClass.getMethods()) {
            if (method.getName().equals("send") && method.getParameterCount() == 1
                    && method.getParameterTypes()[0].isAssignableFrom(packetClass)) {
                return method;
            }
        }
        return null;
    }
}
