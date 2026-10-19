package me.sparklee.phantomplayers.chat;

import java.util.List;

public record ChatConfig(
        ChatSettings settings,
        boolean randomizeDelays,
        boolean avoidSameSpeakerConsecutively,
        boolean avoidRepeatingTemplate,
        List<ChatTemplate> templates
) {

    public ChatConfig {
        templates = List.copyOf(templates);
    }

    public static ChatConfig defaults() {
        return new ChatConfig(ChatSettings.defaults(), true, true, true, List.of());
    }

    public boolean canRun() {
        return settings.enabled() && !templates.isEmpty();
    }
}
