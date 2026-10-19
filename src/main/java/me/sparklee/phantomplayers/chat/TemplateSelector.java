package me.sparklee.phantomplayers.chat;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class TemplateSelector {

    private TemplateSelector() {
    }

    public static ChatTemplate select(List<ChatTemplate> templates, Random random, String lastTemplate,
                                      boolean avoidRepeat) {
        if (templates.isEmpty()) {
            return null;
        }
        List<ChatTemplate> pool = templates;
        if (avoidRepeat && templates.size() > 1 && lastTemplate != null) {
            List<ChatTemplate> filtered = new ArrayList<>(templates.size() - 1);
            for (ChatTemplate template : templates) {
                if (!template.name().equals(lastTemplate)) {
                    filtered.add(template);
                }
            }
            if (!filtered.isEmpty()) {
                pool = filtered;
            }
        }
        int totalWeight = 0;
        for (ChatTemplate template : pool) {
            totalWeight += Math.max(1, template.weight());
        }
        int pick = random.nextInt(totalWeight);
        for (ChatTemplate template : pool) {
            pick -= Math.max(1, template.weight());
            if (pick < 0) {
                return template;
            }
        }
        return pool.get(pool.size() - 1);
    }
}
