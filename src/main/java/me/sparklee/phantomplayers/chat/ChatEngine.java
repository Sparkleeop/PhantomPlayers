package me.sparklee.phantomplayers.chat;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Pure scheduler-driven conversation simulator. All timing is expressed in
 * Minecraft ticks; the Bukkit glue supplies a scheduler that maps these to the
 * global region scheduler.
 */
public final class ChatEngine {

    public interface Cancellable {
        void cancel();
    }

    public interface Scheduler {
        Cancellable later(long delayTicks, Runnable task);

        Cancellable repeating(long periodTicks, Runnable task);
    }

    public interface Delivery {
        boolean hasAudience();

        void broadcast(String line);
    }

    public interface Names {
        List<String> activeNames();
    }

    private static final int TICK_PERIOD = 20;
    private static final long COOLDOWN_TICK_DECREMENT = 20L;

    private final ChatConfig config;
    private final Names names;
    private final Delivery delivery;
    private final Scheduler scheduler;
    private final Random random;
    private final Set<Cancellable> conversationTasks = new HashSet<>();
    private final List<Cancellable> everything = new ArrayList<>();

    private Cancellable ticker;
    private boolean running;
    private int activeConversations;
    private long cooldownTicks;
    private String lastTemplate;

    public ChatEngine(ChatConfig config, Names names, Delivery delivery, Scheduler scheduler, Random random) {
        this.config = config;
        this.names = names;
        this.delivery = delivery;
        this.scheduler = scheduler;
        this.random = random;
    }

    public synchronized void start() {
        if (running) {
            return;
        }
        running = true;
        ticker = scheduler.repeating(TICK_PERIOD, this::tick);
        if (ticker != null) {
            everything.add(ticker);
        }
    }

    public synchronized void stop() {
        running = false;
        for (Cancellable task : everything) {
            task.cancel();
        }
        everything.clear();
        for (Cancellable task : conversationTasks) {
            task.cancel();
        }
        conversationTasks.clear();
        ticker = null;
        activeConversations = 0;
        cooldownTicks = 0;
    }

    public synchronized boolean isRunning() {
        return running;
    }

    private synchronized void tick() {
        if (!running) {
            return;
        }
        if (cooldownTicks > 0) {
            cooldownTicks = Math.max(0L, cooldownTicks - COOLDOWN_TICK_DECREMENT);
            return;
        }
        if (!config.canRun() || !delivery.hasAudience()) {
            return;
        }
        if (activeConversations >= config.settings().maxConcurrentConversations()) {
            return;
        }
        List<String> activeNames = names.activeNames();
        if (activeNames.size() < 2) {
            return;
        }
        ChatTemplate template = TemplateSelector.select(config.templates(), random, lastTemplate,
                config.avoidRepeatingTemplate());
        if (template == null) {
            return;
        }
        ConversationPlanner.Plan plan = ConversationPlanner.plan(template, config.settings(), activeNames, random,
                config.randomizeDelays(), config.avoidSameSpeakerConsecutively());
        if (plan.lines().isEmpty()) {
            return;
        }
        activeConversations++;
        lastTemplate = template.name();
        schedule(plan, 0);
    }

    private void schedule(ConversationPlanner.Plan plan, int lineIndex) {
        if (!running || lineIndex >= plan.lines().size()) {
            finishConversation();
            return;
        }
        ConversationPlanner.PlannedLine line = plan.lines().get(lineIndex);
        Cancellable[] holder = new Cancellable[1];
        Runnable task = () -> {
            synchronized (ChatEngine.this) {
                if (holder[0] != null) {
                    conversationTasks.remove(holder[0]);
                    everything.remove(holder[0]);
                }
                if (!running) {
                    finishConversation();
                    return;
                }
                if (!delivery.hasAudience()) {
                    finishConversation();
                    return;
                }
                if (!names.activeNames().containsAll(plan.requiredNames())) {
                    finishConversation();
                    return;
                }
                delivery.broadcast(line.text());
                if (lineIndex + 1 >= plan.lines().size()) {
                    finishConversation();
                } else {
                    schedule(plan, lineIndex + 1);
                }
            }
        };
        holder[0] = scheduler.later(line.delayTicks(), task);
        if (holder[0] == null) {
            finishConversation();
            return;
        }
        conversationTasks.add(holder[0]);
        everything.add(holder[0]);
    }

    private void finishConversation() {
        if (activeConversations > 0) {
            activeConversations--;
        }
        cooldownTicks = config.settings().cooldownTicks();
    }
}
