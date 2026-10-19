package me.sparklee.phantomplayers.chat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.regex.Matcher;

public final class ConversationPlanner {

    private ConversationPlanner() {
    }

    public record PlannedLine(String text, String speaker, long delayTicks) {
    }

    public record Plan(List<PlannedLine> lines, Set<String> requiredNames) {
        public Plan {
            lines = List.copyOf(lines);
            requiredNames = Set.copyOf(requiredNames);
        }
    }

    public static Plan plan(ChatTemplate template, ChatSettings settings, List<String> activeNames,
                            Random random, boolean randomizeDelays, boolean avoidSameSpeaker) {
        if (activeNames.isEmpty() || template == null) {
            return new Plan(List.of(), Set.of());
        }
        int lineCount = Math.min(template.lines().size(), settings.maxLinesPerConversation());
        List<String> sourceLines = template.lines().subList(0, lineCount);

        LinkedHashSet<Integer> indices = new LinkedHashSet<>();
        for (String line : sourceLines) {
            Matcher matcher = ChatTemplate.PLACEHOLDER.matcher(line);
            while (matcher.find()) {
                indices.add(Integer.parseInt(matcher.group(1)));
            }
        }
        if (indices.isEmpty()) {
            return new Plan(List.of(), Set.of());
        }

        List<String> shuffled = new ArrayList<>(activeNames);
        Collections.shuffle(shuffled, random);
        Map<Integer, String> mapping = new HashMap<>();
        int cursor = 0;
        for (int index : indices) {
            mapping.put(index, shuffled.get(cursor % shuffled.size()));
            cursor++;
        }

        List<PlannedLine> lines = buildLines(sourceLines, mapping, settings, random, randomizeDelays);
        if (avoidSameSpeaker && shuffled.size() > 1) {
            for (int attempt = 0; attempt < 12; attempt++) {
                int violation = firstRepeatedSpeaker(lines);
                if (violation < 0) {
                    break;
                }
                if (!trySwapSpeaker(sourceLines, mapping, lines, violation)) {
                    break;
                }
                lines = buildLines(sourceLines, mapping, settings, random, randomizeDelays);
            }
        }

        Set<String> required = new LinkedHashSet<>();
        for (PlannedLine line : lines) {
            required.add(line.speaker());
        }
        return new Plan(lines, required);
    }

    private static List<PlannedLine> buildLines(List<String> sourceLines, Map<Integer, String> mapping,
                                                ChatSettings settings, Random random, boolean randomizeDelays) {
        List<PlannedLine> lines = new ArrayList<>(sourceLines.size());
        for (String source : sourceLines) {
            Matcher matcher = ChatTemplate.PLACEHOLDER.matcher(source);
            StringBuilder text = new StringBuilder();
            String speaker = null;
            while (matcher.find()) {
                String name = mapping.get(Integer.parseInt(matcher.group(1)));
                if (speaker == null) {
                    speaker = name;
                }
                matcher.appendReplacement(text, Matcher.quoteReplacement(name));
            }
            matcher.appendTail(text);
            long delaySeconds = settings.minDelaySeconds();
            if (randomizeDelays) {
                int span = settings.maxDelaySeconds() - settings.minDelaySeconds();
                delaySeconds += span == 0 ? 0 : random.nextInt(span + 1);
            }
            lines.add(new PlannedLine(text.toString(), speaker, delaySeconds * 20L));
        }
        return lines;
    }

    private static int firstRepeatedSpeaker(List<PlannedLine> lines) {
        for (int index = 1; index < lines.size(); index++) {
            if (lines.get(index).speaker().equals(lines.get(index - 1).speaker())) {
                return index;
            }
        }
        return -1;
    }

    private static boolean trySwapSpeaker(List<String> sourceLines, Map<Integer, String> mapping,
                                          List<PlannedLine> lines, int lineIndex) {
        String previous = lines.get(lineIndex - 1).speaker();
        Matcher current = ChatTemplate.PLACEHOLDER.matcher(sourceLines.get(lineIndex));
        Integer firstIndex = null;
        Integer alternative = null;
        while (current.find()) {
            int index = Integer.parseInt(current.group(1));
            if (firstIndex == null) {
                firstIndex = index;
            } else if (!mapping.get(index).equals(previous)) {
                alternative = index;
                break;
            }
        }
        if (firstIndex == null || alternative == null
                || mapping.get(alternative).equals(mapping.get(firstIndex))) {
            return false;
        }
        String temporary = mapping.get(firstIndex);
        mapping.put(firstIndex, mapping.get(alternative));
        mapping.put(alternative, temporary);
        return true;
    }
}
