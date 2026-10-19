package me.sparklee.phantomplayers.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

public final class Text {

    private static final Component PREFIX = Component.text("[", NamedTextColor.DARK_AQUA)
            .append(Component.text("PhantomPlayers", NamedTextColor.AQUA))
            .append(Component.text("] ", NamedTextColor.DARK_AQUA))
            .decoration(TextDecoration.ITALIC, false);

    private Text() {
    }

    public static Component info(String message) {
        return PREFIX.append(Component.text(message, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
    }

    public static Component success(String message) {
        return PREFIX.append(Component.text(message, NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
    }

    public static Component warn(String message) {
        return PREFIX.append(Component.text(message, NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
    }

    public static Component error(String message) {
        return PREFIX.append(Component.text(message, NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
    }

    public static Component highlight(String message) {
        return Component.text(message, NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false);
    }
}
