package com.nexuscraft.nexustutorial;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

/**
 * Runs the lessons: who is on which step, what completes it, what is said, what is paid. Everything
 * the server has to answer (is that plugin installed? is that a real command?) is passed in, so the
 * whole flow can be exercised without a server.
 */
final class Tutor {

    enum StartResult { OK, UNKNOWN, UNAVAILABLE }

    enum NextResult { ADVANCED, FINISHED, WAITING, NO_LESSON }

    enum LessonState { NEW, ACTIVE, DONE }

    private final TutorConfig cfg;
    private final LessonBook book;
    private final ProgressStore store;
    private final Predicate<String> pluginPresent;
    private final UnaryOperator<String> canon;
    private final Consumer<String> console;
    private final Map<UUID, BossBar> bars = new HashMap<>();

    /**
     * @param pluginPresent is a plugin with this name installed and enabled?
     * @param canon         a command label's one true name, or null when the server has no such command
     * @param console       runs a command as the console (reward commands)
     */
    Tutor(TutorConfig cfg, LessonBook book, ProgressStore store, Predicate<String> pluginPresent,
          UnaryOperator<String> canon, Consumer<String> console) {
        this.cfg = cfg;
        this.book = book;
        this.store = store;
        this.pluginPresent = pluginPresent;
        this.canon = canon;
        this.console = console;
    }

    LessonBook book() {
        return book;
    }

    // ------------------------------------------------------------------ what a player can see

    /** Can this player take this lesson right now (plugin installed, permission held)? */
    boolean available(Player p, Lesson l) {
        if (l.plugin() != null && !pluginPresent.test(l.plugin())) {
            return false;
        }
        return l.permission() == null || p.hasPermission(l.permission());
    }

    List<Lesson> visible(Player p) {
        List<Lesson> out = new ArrayList<>();
        for (Lesson l : book.lessons()) {
            if (available(p, l)) {
                out.add(l);
            }
        }
        return out;
    }

    List<Lesson> visibleIn(Player p, String category) {
        List<Lesson> out = new ArrayList<>();
        for (Lesson l : book.lessonsIn(category)) {
            if (available(p, l)) {
                out.add(l);
            }
        }
        return out;
    }

    /** Categories that hold at least one lesson this player can take. */
    List<Category> visibleCategories(Player p) {
        List<Category> out = new ArrayList<>();
        for (Category c : book.categories()) {
            if (!visibleIn(p, c.id()).isEmpty()) {
                out.add(c);
            }
        }
        return out;
    }

    LessonState stateOf(Player p, Lesson l) {
        ProgressData d = store.peek(p.getUniqueId());
        if (d == null) {
            return LessonState.NEW;
        }
        if (l.id().equals(d.active)) {
            return LessonState.ACTIVE;
        }
        return d.done.contains(l.id()) ? LessonState.DONE : LessonState.NEW;
    }

    int doneCount(Player p, List<Lesson> lessons) {
        int n = 0;
        for (Lesson l : lessons) {
            if (stateOf(p, l) == LessonState.DONE) {
                n++;
            }
        }
        return n;
    }

    /** The lesson this player is in the middle of, or null. */
    Lesson activeLesson(Player p) {
        ProgressData d = store.peek(p.getUniqueId());
        return d == null || d.active == null ? null : book.lesson(d.active);
    }

    /** Index of the step they are on (0-based), clamped to the lesson. */
    int activeStep(Player p) {
        ProgressData d = store.peek(p.getUniqueId());
        Lesson l = activeLesson(p);
        if (d == null || l == null) {
            return 0;
        }
        return Math.min(d.step, l.steps().size() - 1);
    }

    /** What a step really waits for: a command the server does not have, or an unknown item, falls back to plain reading. */
    StepKind effective(Step s) {
        if (s.kind() == StepKind.COMMAND && canon.apply(s.words()[0]) == null) {
            return StepKind.INFO;
        }
        if (s.kind() == StepKind.HAVE && Material.matchMaterial(s.material()) == null) {
            return StepKind.INFO;
        }
        return s.kind();
    }

    // ------------------------------------------------------------------ starting, moving on, stopping

    StartResult start(Player p, String lessonId) {
        Lesson l = book.lesson(lessonId);
        if (l == null) {
            return StartResult.UNKNOWN;
        }
        if (!available(p, l)) {
            return StartResult.UNAVAILABLE;
        }
        ProgressData d = store.of(p.getUniqueId());
        if (!l.id().equals(d.active)) {
            d.active = l.id();
            d.step = 0;
        }
        d.step = Math.min(d.step, l.steps().size() - 1);
        store.markDirty();
        present(p, l, d.step, true);
        return StartResult.OK;
    }

    /** Presses "Next": moves past a reading step. */
    NextResult next(Player p) {
        Lesson l = activeLesson(p);
        if (l == null) {
            return NextResult.NO_LESSON;
        }
        Step s = l.steps().get(activeStep(p));
        if (effective(s) != StepKind.INFO) {
            say(p, "&7That step needs you to do something first. &8(Stuck? &f/tutorial skip&8)");
            describe(p, l, activeStep(p));
            return NextResult.WAITING;
        }
        return advance(p, l);
    }

    /** Skips whatever step they are on. */
    NextResult skip(Player p) {
        Lesson l = activeLesson(p);
        if (l == null) {
            return NextResult.NO_LESSON;
        }
        return advance(p, l);
    }

    /** Puts the current lesson down; progress on finished lessons stays. */
    boolean stop(Player p) {
        ProgressData d = store.peek(p.getUniqueId());
        if (d == null || d.active == null) {
            return false;
        }
        d.active = null;
        d.step = 0;
        store.markDirty();
        dropBar(p);
        return true;
    }

    private NextResult advance(Player p, Lesson l) {
        ProgressData d = store.of(p.getUniqueId());
        d.step = activeStep(p) + 1;
        store.markDirty();
        if (d.step >= l.steps().size()) {
            finish(p, l, d);
            return NextResult.FINISHED;
        }
        present(p, l, d.step, false);
        return NextResult.ADVANCED;
    }

    private void finish(Player p, Lesson l, ProgressData d) {
        d.active = null;
        d.step = 0;
        d.done.add(l.id());
        store.markDirty();
        dropBar(p);
        p.sendMessage(ColorText.translate(cfg.prefix + cfg.msgLessonDone.replace("{lesson}", l.title())));
        if (cfg.titles) {
            p.sendTitle(ColorText.translate("&a&lLesson complete"), ColorText.translate("&f" + l.title()), 5, 40, 10);
        }
        reward(p, l, cfg.xpPerLesson, cfg.lessonCommands);
        if (!d.graduated) {
            List<Lesson> mine = visible(p);
            if (!mine.isEmpty() && doneCount(p, mine) == mine.size()) {
                d.graduated = true;
                p.sendMessage(ColorText.translate(cfg.prefix + cfg.msgAllDone));
                reward(p, l, cfg.finalXp, cfg.finalCommands);
            }
        }
        List<Lesson> left = new ArrayList<>(visible(p));
        left.removeIf(x -> d.done.contains(x.id()));
        if (!left.isEmpty()) {
            say(p, "&7" + left.size() + " lesson" + (left.size() == 1 ? "" : "s") + " to go. Type &f/tutorial &7to pick the next one.");
        }
    }

    private void reward(Player p, Lesson l, int xp, List<String> commands) {
        if (xp > 0) {
            p.giveExp(xp);
        }
        for (String c : commands) {
            console.accept(c.replace("{player}", p.getName()).replace("{lesson}", l.id()));
        }
    }

    // ------------------------------------------------------------------ what the player does

    /** The player ran a command (what they typed, with or without the slash). */
    void onCommand(Player p, String typed) {
        Lesson l = activeLesson(p);
        if (l == null) {
            return;
        }
        Step s = l.steps().get(activeStep(p));
        if (effective(s) == StepKind.COMMAND && CommandMatch.matches(s.words(), typed, canon)) {
            done(p, l);
        }
    }

    /** The player opened a menu with this (plain-text) title. */
    void onOpen(Player p, String title) {
        Lesson l = activeLesson(p);
        if (l == null) {
            return;
        }
        Step s = l.steps().get(activeStep(p));
        if (effective(s) == StepKind.OPEN && CommandMatch.titleHas(title, s.arg())) {
            done(p, l);
        }
    }

    /** Once a second: checks "have" steps. */
    void tick() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            Lesson l = activeLesson(p);
            if (l == null) {
                continue;
            }
            Step s = l.steps().get(activeStep(p));
            if (effective(s) == StepKind.HAVE && count(p, Material.matchMaterial(s.material())) >= s.count()) {
                done(p, l);
            }
        }
    }

    private static int count(Player p, Material m) {
        int n = 0;
        for (ItemStack stack : p.getInventory().getContents()) {
            if (stack != null && stack.getType() == m) {
                n += stack.getAmount();
            }
        }
        return n;
    }

    private void done(Player p, Lesson l) {
        say(p, "&a&l✔ &aDone!");
        advance(p, l);
    }

    // ------------------------------------------------------------------ the admin check

    /**
     * What the lessons look like on this server: which are hidden because their plugin is missing, and
     * which steps fall back to plain reading because their command or item does not exist here.
     */
    List<String> checkReport() {
        List<String> out = new ArrayList<>();
        List<String> hidden = new ArrayList<>();
        int live = 0;
        for (Lesson l : book.lessons()) {
            if (l.plugin() != null && !pluginPresent.test(l.plugin())) {
                hidden.add(l.id() + " (" + l.plugin() + ")");
                continue;
            }
            live++;
            for (int i = 0; i < l.steps().size(); i++) {
                Step s = l.steps().get(i);
                if (s.kind() != StepKind.INFO && effective(s) == StepKind.INFO) {
                    out.add("&e" + l.id() + " step " + (i + 1) + " &7reads only: no " + (s.kind() == StepKind.COMMAND ? "/" + s.words()[0] + " command" : s.material() + " item") + " on this server.");
                }
            }
        }
        out.add(0, "&f" + book.lessons().size() + " lessons loaded, " + live + " usable here"
                + (hidden.isEmpty() ? "." : ", " + hidden.size() + " hidden (plugin not installed): &7" + String.join(", ", hidden)));
        for (String w : book.warnings()) {
            out.add("&c" + w);
        }
        return out;
    }

    // ------------------------------------------------------------------ the join offer

    /** Should this player be offered the tutorial now? */
    boolean shouldOffer(Player p) {
        if (cfg.offerMode.equals("off")) {
            return false;
        }
        ProgressData d = store.peek(p.getUniqueId());
        if (d != null && (d.offered || !d.done.isEmpty() || d.active != null)) {
            return false;
        }
        return cfg.offerMode.equals("all") || !p.hasPlayedBefore();
    }

    /** Sends the invitation (once per player, ever). */
    void offer(Player p) {
        if (!shouldOffer(p)) {
            return;
        }
        store.of(p.getUniqueId()).offered = true;
        store.markDirty();
        p.sendMessage(ColorText.translate(cfg.prefix + cfg.msgOffer));
        p.sendMessage(Component.text("   [Start the tutorial]", NamedTextColor.GREEN)
                .clickEvent(ClickEvent.runCommand("/tutorial")));
        if (cfg.titles) {
            p.sendTitle(ColorText.translate("&6Welcome!"), ColorText.translate("&eType /tutorial for a quick tour"), 10, 60, 20);
        }
    }

    void quit(Player p) {
        dropBar(p);
        store.flush();
    }

    /** An admin wipes someone's progress (all of it, or one lesson). */
    boolean reset(UUID id, String lessonId) {
        ProgressData d = store.peek(id);
        if (d == null) {
            return false;
        }
        if (lessonId == null) {
            store.forget(id);
            Player online = Bukkit.getPlayer(id);
            if (online != null) {
                dropBar(online);
            }
            return true;
        }
        boolean had = d.done.remove(lessonId);
        if (lessonId.equals(d.active)) {
            d.active = null;
            d.step = 0;
            had = true;
        }
        d.graduated = d.graduated && !had;
        store.markDirty();
        return had;
    }

    /** An admin marks a lesson as finished for someone, without rewards. */
    boolean markDone(UUID id, String lessonId) {
        if (book.lesson(lessonId) == null) {
            return false;
        }
        ProgressData d = store.of(id);
        d.done.add(lessonId);
        if (lessonId.equals(d.active)) {
            d.active = null;
            d.step = 0;
        }
        store.markDirty();
        return true;
    }

    // ------------------------------------------------------------------ saying things

    /** Shows the current step: chat and boss bar. */
    private void present(Player p, Lesson l, int index, boolean first) {
        if (first) {
            say(p, "&e&l" + l.title() + " &7(" + l.steps().size() + " step" + (l.steps().size() == 1 ? "" : "s")
                    + ", &f/tutorial stop &7to leave)");
        }
        describe(p, l, index);
        updateBar(p, l, index);
    }

    private void describe(Player p, Lesson l, int index) {
        Step s = l.steps().get(index);
        String count = "&8[" + (index + 1) + "/" + l.steps().size() + "] ";
        p.sendMessage(ColorText.translate(cfg.prefix + count + "&f" + s.text().replace("{player}", p.getName())));
        switch (effective(s)) {
            case INFO -> p.sendMessage(Component.text("   [Next ▶]", NamedTextColor.GREEN)
                    .clickEvent(ClickEvent.runCommand("/tutorial next")));
            case COMMAND -> p.sendMessage(ColorText.translate("&7   Try it: &f/" + s.arg() + " &8(or &f/tutorial skip&8)"));
            case OPEN -> p.sendMessage(ColorText.translate("&7   Open it to continue. &8(or &f/tutorial skip&8)"));
            case HAVE -> p.sendMessage(ColorText.translate("&7   Get " + (s.count() > 1 ? s.count() + " " : "")
                    + "&f" + Pretty.of(s.material()) + "&7 to continue. &8(or &f/tutorial skip&8)"));
        }
    }

    private void updateBar(Player p, Lesson l, int index) {
        if (!cfg.bossBar) {
            return;
        }
        BossBar bar = bars.computeIfAbsent(p.getUniqueId(), k -> Bukkit.createBossBar("", BarColor.YELLOW, BarStyle.SEGMENTED_10));
        bar.setTitle(ColorText.translate("&6Tutorial: &f" + l.title() + " &7- step " + (index + 1) + " of " + l.steps().size()));
        bar.setProgress(Math.max(0.0, Math.min(1.0, (double) index / l.steps().size())));
        bar.addPlayer(p);
    }

    private void dropBar(Player p) {
        BossBar bar = bars.remove(p.getUniqueId());
        if (bar != null) {
            bar.removePlayer(p);
        }
    }

    /** Removes every boss bar (plugin shutdown). */
    void shutdown() {
        for (Map.Entry<UUID, BossBar> e : bars.entrySet()) {
            Player p = Bukkit.getPlayer(e.getKey());
            if (p != null) {
                e.getValue().removePlayer(p);
            }
        }
        bars.clear();
        store.flush();
    }

    /** Re-shows where the player is (after a rejoin, or from the menu's Continue button). */
    void resume(Player p) {
        Lesson l = activeLesson(p);
        if (l == null) {
            say(p, "&7You are not in a lesson. Type &f/tutorial &7to pick one.");
            return;
        }
        present(p, l, activeStep(p), false);
    }

    private void say(Player p, String text) {
        p.sendMessage(ColorText.translate(cfg.prefix + text));
    }

    /** For tests and the menu: the boss bar a player has, or null. */
    BossBar barOf(Player p) {
        return bars.get(p.getUniqueId());
    }
}
