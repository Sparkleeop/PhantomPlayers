package me.sparklee.phantomplayers.platform;

import org.bukkit.Bukkit;

import java.util.Locale;

public record PlatformInfo(String minecraftVersion, String serverName, String bukkitVersion, boolean folia,
                           boolean verified) {

    public static PlatformInfo detect() {
        String minecraftVersion = "unknown";
        try {
            minecraftVersion = Bukkit.getMinecraftVersion();
        } catch (RuntimeException ignored) {
            String bukkit = Bukkit.getBukkitVersion();
            int dash = bukkit.indexOf('-');
            if (dash > 0) {
                minecraftVersion = bukkit.substring(0, dash);
            }
        }
        boolean folia = classPresent("io.papermc.paper.threadedregions.RegionizedServer")
                || Bukkit.getName().toLowerCase(Locale.ROOT).contains("folia");
        String name = Bukkit.getName();
        String bukkitVersion = Bukkit.getBukkitVersion();
        boolean verified = isVerified(minecraftVersion);
        return new PlatformInfo(minecraftVersion, name, bukkitVersion, folia, verified);
    }

    private static boolean isVerified(String minecraftVersion) {
        return "1.21.11".equals(minecraftVersion) || minecraftVersion.startsWith("26.3");
    }

    private static boolean classPresent(String name) {
        try {
            Class.forName(name);
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }

    public String summary() {
        return serverName + " " + bukkitVersion + " (Minecraft " + minecraftVersion
                + (folia ? ", Folia" : ", Paper") + (verified ? ", verified" : ", untested") + ")";
    }
}
