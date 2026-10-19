package me.sparklee.phantomplayers.platform;

import java.util.logging.Logger;

public final class NmsLoader {

    private NmsLoader() {
    }

    public static NmsPackets create(PlatformInfo platform, Logger logger, boolean allowUntested)
            throws CompatibilityException {
        if (!platform.verified()) {
            if (!allowUntested) {
                throw new CompatibilityException("Unsupported server version " + platform.minecraftVersion()
                        + "; verified versions are Paper/Folia 1.21.11 and 26.3, and "
                        + "compatibility.allow-untested-adapters is false");
            }
            logger.warning("Minecraft " + platform.minecraftVersion()
                    + " is not a verified target. The mojmap packet adapter will be probed at runtime.");
        }
        try {
            NmsPackets packets = new MojmapPackets(logger);
            logger.info("Packet adapter ready: " + packets.description());
            return packets;
        } catch (ReflectiveOperationException exception) {
            throw new CompatibilityException("This server build does not expose the expected mojmap packet "
                    + "classes. No NMS classes are bundled with PhantomPlayers. Cause: "
                    + exception.getClass().getSimpleName()
                    + (exception.getMessage() == null ? "" : " - " + exception.getMessage()), exception);
        }
    }
}
