package me.sparklee.phantomplayers.names;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class NamePool {

    private final List<String> names;
    private final List<String> warnings;

    private NamePool(List<String> names, List<String> warnings) {
        this.names = names;
        this.warnings = warnings;
    }

    public static NamePool empty() {
        return new NamePool(List.of(), List.of());
    }

    public static NamePool parse(String content) {
        List<String> parsed = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        String[] lines = content.split("\\R", -1);
        for (int index = 0; index < lines.length; index++) {
            String candidate = lines[index].trim();
            if (candidate.isEmpty() || candidate.startsWith("#")) {
                continue;
            }
            if (!NameRules.isValid(candidate)) {
                warnings.add("names.txt line " + (index + 1) + ": \"" + candidate
                        + "\" is not a valid 3-16 character Minecraft username");
                continue;
            }
            if (!seen.add(NameRules.key(candidate))) {
                warnings.add("names.txt line " + (index + 1) + ": duplicate username \"" + candidate + "\" ignored");
                continue;
            }
            parsed.add(candidate);
        }
        return new NamePool(List.copyOf(parsed), List.copyOf(warnings));
    }

    public List<String> names() {
        return names;
    }

    public List<String> warnings() {
        return warnings;
    }

    public int size() {
        return names.size();
    }

    public boolean isEmpty() {
        return names.isEmpty();
    }
}
