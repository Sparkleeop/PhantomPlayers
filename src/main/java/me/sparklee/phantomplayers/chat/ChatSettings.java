package me.sparklee.phantomplayers.chat;

public record ChatSettings(
        boolean enabled,
        int minDelaySeconds,
        int maxDelaySeconds,
        int cooldownSeconds,
        int maxConcurrentConversations,
        int maxLinesPerConversation
) {

    public static ChatSettings defaults() {
        return new ChatSettings(true, 8, 35, 60, 3, 8);
    }

    public long minDelayTicks() {
        return minDelaySeconds * 20L;
    }

    public long cooldownTicks() {
        return cooldownSeconds * 20L;
    }
}
