package me.sparklee.phantomplayers.chat;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConversationPlannerTest {

    private static final ChatSettings SETTINGS = new ChatSettings(true, 1, 3, 0, 1, 10);
    private static final List<String> NAMES = List.of("Alpha", "Bravo", "Charlie");

    @Test
    void substitutesAllPlaceholdersAndKeepsSpeakersConsistent() {
        ChatTemplate template = new ChatTemplate("test", 1, List.of(
                "{player1}: hey",
                "{player2}: hello",
                "{player1}: how are you, {player2}?"
        ));
        ConversationPlanner.Plan plan = ConversationPlanner.plan(template, SETTINGS, NAMES, new Random(1),
                false, true);
        assertEquals(3, plan.lines().size());
        for (ConversationPlanner.PlannedLine line : plan.lines()) {
            assertFalse(line.text().contains("{player"), line.text());
        }
        String first = plan.lines().get(0).speaker();
        String second = plan.lines().get(1).speaker();
        assertNotEquals(first, second);
        assertEquals(first, plan.lines().get(2).speaker());
        assertTrue(plan.lines().get(2).text().startsWith(first + ":"));
        assertEquals(2, plan.requiredNames().size());
    }

    @Test
    void delayIsClampedToConfiguredRange() {
        ChatTemplate template = new ChatTemplate("test", 1, List.of(
                "{player1}: one",
                "{player2}: two"
        ));
        for (int seed = 0; seed < 20; seed++) {
            ConversationPlanner.Plan plan = ConversationPlanner.plan(template, SETTINGS, NAMES,
                    new Random(seed), true, true);
            for (ConversationPlanner.PlannedLine line : plan.lines()) {
                assertTrue(line.delayTicks() >= 20 && line.delayTicks() <= 60,
                        "delay was " + line.delayTicks());
            }
        }
    }

    @Test
    void lineCountIsCapped() {
        ChatTemplate template = new ChatTemplate("test", 1, List.of(
                "{player1}: one",
                "{player2}: two",
                "{player1}: three",
                "{player2}: four"
        ));
        ChatSettings capped = new ChatSettings(true, 1, 1, 0, 1, 2);
        ConversationPlanner.Plan plan = ConversationPlanner.plan(template, capped, NAMES, new Random(2),
                false, true);
        assertEquals(2, plan.lines().size());
    }

    @Test
    void cyclesNamesWhenTemplateNeedsMoreSpeakers() {
        ChatTemplate template = new ChatTemplate("test", 1, List.of(
                "{player1}: a",
                "{player2}: b",
                "{player3}: c",
                "{player4}: d"
        ));
        ConversationPlanner.Plan plan = ConversationPlanner.plan(template, SETTINGS, List.of("Alpha", "Bravo"),
                new Random(4), false, false);
        assertEquals(4, plan.lines().size());
        for (ConversationPlanner.PlannedLine line : plan.lines()) {
            assertFalse(line.text().contains("{player"));
        }
    }

    @Test
    void emptyNamesProducesEmptyPlan() {
        ChatTemplate template = new ChatTemplate("test", 1, List.of("{player1}: hi"));
        assertTrue(ConversationPlanner.plan(template, SETTINGS, List.of(), new Random(1), true, true)
                .lines().isEmpty());
    }

    @Test
    void fixedDelayWhenRandomizationDisabled() {
        ChatTemplate template = new ChatTemplate("test", 1, List.of(
                "{player1}: one",
                "{player2}: two"
        ));
        ConversationPlanner.Plan plan = ConversationPlanner.plan(template, SETTINGS, NAMES, new Random(1),
                false, false);
        assertEquals(20L, plan.lines().get(0).delayTicks());
        assertEquals(20L, plan.lines().get(1).delayTicks());
    }
}
