package me.sparklee.phantomplayers.chat;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;

public final class ChatConfigLoader {

    private static final int MAX_PLACEHOLDER_INDEX = 32;
    private static final int MAX_LINE_LENGTH = 256;

    private ChatConfigLoader() {
    }

    public record Result(ChatConfig config, List<String> errors) {
        public boolean hasErrors() {
            return !errors.isEmpty();
        }
    }

    public static Result load(Path file) {
        if (!Files.exists(file)) {
            return new Result(ChatConfig.defaults(), List.of("chat.yml is missing"));
        }
        String content;
        try {
            content = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            return new Result(ChatConfig.defaults(),
                    List.of("Unable to read chat.yml: " + exception.getMessage()));
        }
        return parse(content);
    }

    public static Result parse(String content) {
        YamlConfiguration configuration = new YamlConfiguration();
        try {
            configuration.loadFromString(content);
        } catch (InvalidConfigurationException exception) {
            return new Result(ChatConfig.defaults(),
                    List.of("chat.yml is not valid YAML: " + exception.getMessage()));
        }

        List<String> errors = new ArrayList<>();
        ChatSettings defaults = ChatSettings.defaults();

        boolean enabled = bool(configuration, "settings.enabled", defaults.enabled(), errors);
        int minDelay = integer(configuration, "settings.min-delay-seconds", defaults.minDelaySeconds(), 1, 3_600,
                errors);
        int maxDelay = integer(configuration, "settings.max-delay-seconds", defaults.maxDelaySeconds(), 1, 3_600,
                errors);
        if (minDelay > maxDelay) {
            errors.add("settings.min-delay-seconds must not exceed settings.max-delay-seconds");
            minDelay = defaults.minDelaySeconds();
            maxDelay = defaults.maxDelaySeconds();
        }
        int cooldown = integer(configuration, "settings.conversation-cooldown-seconds",
                defaults.cooldownSeconds(), 0, 86_400, errors);
        int maxConcurrent = integer(configuration, "settings.max-concurrent-conversations",
                defaults.maxConcurrentConversations(), 1, 32, errors);
        int maxLines = integer(configuration, "settings.max-lines-per-conversation",
                defaults.maxLinesPerConversation(), 1, 32, errors);

        boolean randomize = bool(configuration, "selection.randomize-delays", true, errors);
        boolean avoidSameSpeaker = bool(configuration, "selection.avoid-same-speaker-consecutively", true, errors);
        boolean avoidRepeat = bool(configuration, "selection.avoid-repeating-template", true, errors);

        ChatSettings settings = new ChatSettings(enabled, minDelay, maxDelay, cooldown, maxConcurrent, maxLines);

        List<ChatTemplate> templates = new ArrayList<>();
        ConfigurationSection templatesSection = configuration.getConfigurationSection("templates");
        if (templatesSection == null) {
            errors.add("templates section is missing");
        } else {
            for (String name : templatesSection.getKeys(false)) {
                ConfigurationSection section = templatesSection.getConfigurationSection(name);
                if (section == null) {
                    errors.add("templates." + name + " must be a section");
                    continue;
                }
                int weight = integer(section, "weight", 1, 1, 1_000, errors, "templates." + name + ".");
                Object rawLines = section.get("lines");
                if (!(rawLines instanceof List<?> lineList) || lineList.isEmpty()) {
                    errors.add("templates." + name + ".lines must be a non-empty list");
                    continue;
                }
                List<String> lines = new ArrayList<>();
                boolean templateValid = true;
                for (int index = 0; index < lineList.size(); index++) {
                    Object rawLine = lineList.get(index);
                    if (!(rawLine instanceof String line)) {
                        errors.add("templates." + name + ".lines[" + index + "] must be a string");
                        templateValid = false;
                        continue;
                    }
                    String stripped = line.strip();
                    if (stripped.isEmpty()) {
                        errors.add("templates." + name + ".lines[" + index + "] must not be empty");
                        templateValid = false;
                        continue;
                    }
                    if (stripped.length() > MAX_LINE_LENGTH) {
                        errors.add("templates." + name + ".lines[" + index + "] exceeds "
                                + MAX_LINE_LENGTH + " characters");
                        templateValid = false;
                        continue;
                    }
                    Matcher matcher = ChatTemplate.PLACEHOLDER.matcher(stripped);
                    int placeholders = 0;
                    while (matcher.find()) {
                        placeholders++;
                        int placeholderIndex = Integer.parseInt(matcher.group(1));
                        if (placeholderIndex < 1 || placeholderIndex > MAX_PLACEHOLDER_INDEX) {
                            errors.add("templates." + name + ".lines[" + index + "] uses {player"
                                    + placeholderIndex + "}; the index must be between 1 and "
                                    + MAX_PLACEHOLDER_INDEX);
                            templateValid = false;
                        }
                    }
                    if (placeholders == 0) {
                        errors.add("templates." + name + ".lines[" + index
                                + "] must contain at least one {playerN} placeholder");
                        templateValid = false;
                    }
                    lines.add(stripped);
                }
                if (templateValid && !lines.isEmpty()) {
                    templates.add(new ChatTemplate(name, weight, lines));
                }
            }
        }

        ChatConfig config = new ChatConfig(settings, randomize, avoidSameSpeaker, avoidRepeat, templates);
        if (config.templates().isEmpty()) {
            errors.add("chat.yml contains no usable conversation templates; simulated chat will stay disabled");
        }
        return new Result(config, List.copyOf(errors));
    }

    private static int integer(ConfigurationSection section, String path, int fallback, int min, int max,
                               List<String> errors) {
        return integer(section, path, fallback, min, max, errors, "");
    }

    private static int integer(ConfigurationSection section, String path, int fallback, int min, int max,
                               List<String> errors, String prefix) {
        Object raw = section.get(path);
        if (raw == null) {
            return fallback;
        }
        if (!(raw instanceof Number number)) {
            errors.add(prefix + path + " must be a number; using " + fallback);
            return fallback;
        }
        int value = number.intValue();
        if (value < min || value > max) {
            errors.add(prefix + path + " must be between " + min + " and " + max + "; using " + fallback);
            return fallback;
        }
        return value;
    }

    private static boolean bool(ConfigurationSection section, String path, boolean fallback,
                                List<String> errors) {
        Object raw = section.get(path);
        if (raw == null) {
            return fallback;
        }
        if (!(raw instanceof Boolean value)) {
            errors.add(path + " must be true or false; using " + fallback);
            return fallback;
        }
        return value;
    }
}
