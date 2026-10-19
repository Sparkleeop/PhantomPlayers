package me.sparklee.phantomplayers.chat;

import java.util.List;
import java.util.regex.Pattern;

public record ChatTemplate(String name, int weight, List<String> lines) {

    public static final Pattern PLACEHOLDER = Pattern.compile("\\{player(\\d{1,2})}");

    public ChatTemplate {
        lines = List.copyOf(lines);
    }
}
