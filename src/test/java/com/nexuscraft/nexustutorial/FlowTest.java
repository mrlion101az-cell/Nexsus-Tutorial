package com.nexuscraft.nexustutorial;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.List;

import static com.nexuscraft.nexustutorial.Kit.expect;

/** A lesson from first step to last, against a stub server. */
public final class FlowTest {

    public static void main(String[] args) {
        visibility();
        lessonFlow();
        fallbacks();
        rewards();
        controls();
        offers();
        admin();
        Kit.finish();
    }

    private static Env env() {
        Env e = new Env(Env.sample());
        Env.command("rime", "rimewalker");
        Env.command("menu", "hub");
        e.plugins.add("NexusRime");
        return e;
    }

    // ------------------------------------------------------------------ who sees what

    private static void visibility() {
        Env e = env();
        Kit.TestPlayer p = e.player("Alex");
        expect(e.tutor.visible(p).size() == 2, "visible: the staff lesson is hidden from a player");
        p.perms.add("nexustutorial.staff");
        expect(e.tutor.visible(p).size() == 3, "visible: staff see it");
        e.plugins.remove("NexusRime");
        expect(e.tutor.visible(p).size() == 2 && e.tutor.visibleIn(p, "start").size() == 1, "visible: a lesson whose plugin is not installed is hidden");
        expect(e.tutor.visibleCategories(p).size() == 2, "visible: staff still see both categories");
        p.perms.clear();
        expect(e.tutor.visibleCategories(p).size() == 1 && e.tutor.visibleCategories(p).get(0).id().equals("start"), "visible: a category with nothing to show is hidden");
        expect(e.tutor.start(p, "rime") == Tutor.StartResult.UNAVAILABLE, "visible: a hidden lesson cannot be started");
        expect(e.tutor.start(p, "secret") == Tutor.StartResult.UNAVAILABLE, "visible: nor one needing a permission");
        expect(e.tutor.start(p, "nonsense") == Tutor.StartResult.UNKNOWN, "visible: nor one that does not exist");
        expect(e.tutor.activeLesson(p) == null && e.tutor.barOf(p) == null, "visible: refused starts leave no trace");
    }

    // ------------------------------------------------------------------ the whole walk

    private static void lessonFlow() {
        Env e = env();
        Kit.TestPlayer p = e.player("Alex");
        Lesson rime = e.book.lesson("rime");
        expect(e.tutor.stateOf(p, rime) == Tutor.LessonState.NEW, "flow: a lesson starts out new");
        expect(e.tutor.start(p, "rime") == Tutor.StartResult.OK, "flow: start");
        expect(e.tutor.stateOf(p, rime) == Tutor.LessonState.ACTIVE && e.tutor.activeStep(p) == 0, "flow: it is now active at step 0");
        expect(p.heard("The Long Winter") && p.heard("A horde walks in winter.") && p.heard("[1/5]"), "flow: the title and first step are shown");
        expect(p.heard("[Next"), "flow: a reading step offers a Next button");
        expect(e.tutor.barOf(p) != null && e.tutor.barOf(p).getTitle().contains("step 1 of 5") && Kit.near(e.tutor.barOf(p).getProgress(), 0.0), "flow: the boss bar starts empty");

        // A command typed early does nothing on a reading step.
        e.tutor.onCommand(p, "/rime recipe blade");
        expect(e.tutor.activeStep(p) == 0, "flow: a command does not complete a reading step");

        expect(e.tutor.next(p) == Tutor.NextResult.ADVANCED && e.tutor.activeStep(p) == 1, "flow: Next moves on");
        expect(p.heard("Type /rime recipe blade.") && p.heard("Try it: "), "flow: the command step says what to type");
        expect(Kit.near(e.tutor.barOf(p).getProgress(), 0.2), "flow: the bar fills as you go");

        expect(e.tutor.next(p) == Tutor.NextResult.WAITING && e.tutor.activeStep(p) == 1, "flow: Next cannot skip an action step");
        e.tutor.onCommand(p, "/rime status");
        expect(e.tutor.activeStep(p) == 1, "flow: the wrong command does not count");
        e.tutor.onOpen(p, "Codex");
        expect(e.tutor.activeStep(p) == 1, "flow: a menu does not complete a command step");
        e.tutor.onCommand(p, "/rimewalker recipe blade");
        expect(e.tutor.activeStep(p) == 2 && p.heard("Done!"), "flow: the right command (by alias) completes it");

        e.tutor.onCommand(p, "/rime recipe blade");
        expect(e.tutor.activeStep(p) == 2, "flow: a command does not complete a menu step");
        e.tutor.onOpen(p, "Chest");
        expect(e.tutor.activeStep(p) == 2, "flow: the wrong menu does not count");
        e.tutor.onOpen(p, "§5The §lCODEX§r of Nexus");
        expect(e.tutor.activeStep(p) == 3, "flow: the right menu completes it, colours and case ignored");

        e.tutor.tick();
        expect(e.tutor.activeStep(p) == 3, "flow: an empty inventory does not complete a have step");
        p.getInventory().addItem(new ItemStack(Material.CRAFTING_TABLE, 1));
        e.tutor.tick();
        expect(e.tutor.activeStep(p) == 3, "flow: one is not two");
        p.getInventory().addItem(new ItemStack(Material.CRAFTING_TABLE, 1));
        e.tutor.tick();
        expect(e.tutor.activeStep(p) == 4, "flow: two completes it, counted across stacks");
        expect(p.heard("Stay warm."), "flow: the last step is shown");

        int xpBefore = p.xpGiven;
        expect(e.tutor.next(p) == Tutor.NextResult.FINISHED, "flow: the last Next finishes the lesson");
        expect(e.tutor.stateOf(p, rime) == Tutor.LessonState.DONE && e.tutor.activeLesson(p) == null, "flow: it is done, nothing active");
        expect(p.xpGiven - xpBefore == 50, "flow: 50 xp for the lesson");
        expect(p.heard("Lesson complete") && p.titles.stream().anyMatch(t -> t.contains("Lesson complete")), "flow: told, with a title");
        expect(e.tutor.barOf(p) == null, "flow: the bar goes away");
        expect(p.heard("lesson to go"), "flow: and how many are left");
        expect(e.tutor.next(p) == Tutor.NextResult.NO_LESSON && e.tutor.skip(p) == Tutor.NextResult.NO_LESSON, "flow: nothing to move on from");

        // Doing it again keeps it done.
        expect(e.tutor.start(p, "rime") == Tutor.StartResult.OK && e.tutor.stateOf(p, rime) == Tutor.LessonState.ACTIVE, "flow: a finished lesson can be replayed");
        e.tutor.stop(p);
        expect(e.tutor.stateOf(p, rime) == Tutor.LessonState.DONE, "flow: and stopping the replay keeps it finished");
    }

    // ------------------------------------------------------------------ steps that cannot work here

    private static void fallbacks() {
        Env e = new Env(Env.sample());
        e.plugins.add("NexusRime");
        // No "rime" command is registered on this server, and the item does not exist.
        Kit.TestPlayer p = e.player("Alex");
        Step cmd = e.book.lesson("rime").steps().get(1);
        expect(e.tutor.effective(cmd) == StepKind.INFO, "fallback: a command the server lacks becomes a reading step");
        e.tutor.start(p, "rime");
        expect(e.tutor.next(p) == Tutor.NextResult.ADVANCED && e.tutor.activeStep(p) == 1, "fallback: Next works on it");
        expect(p.heard("[Next"), "fallback: and it offers Next");
        Env.command("rime");
        expect(e.tutor.effective(cmd) == StepKind.COMMAND, "fallback: once the command exists it is a real step again");

        Kit.MapSection m = new Kit.MapSection();
        m.put("categories.c.title", "C").put("categories.c.icon", "BOOK").put("categories.c.description", "d");
        m.put("lessons.odd.category", "c").put("lessons.odd.title", "Odd").put("lessons.odd.steps", List.of("have BANANA 3 :: Get bananas.", "info :: ok"));
        Env o = new Env(m);
        Kit.TestPlayer q = o.player("Bea");
        expect(o.tutor.effective(o.book.lesson("odd").steps().get(0)) == StepKind.INFO, "fallback: an unknown item becomes a reading step");
        o.tutor.start(q, "odd");
        o.tutor.tick();
        expect(o.tutor.activeStep(q) == 0, "fallback: the tick does not complete it");
        expect(o.tutor.next(q) == Tutor.NextResult.ADVANCED, "fallback: Next does");

        List<String> report = e.tutor.checkReport();
        expect(report.get(0).contains("3 lessons loaded") && report.get(0).contains("3 usable here") && !report.get(0).contains("hidden"), "check: summary line");
        Env missing = new Env(Env.sample());
        List<String> r2 = missing.tutor.checkReport();
        expect(r2.get(0).contains("hidden") && r2.get(0).contains("rime (NexusRime)"), "check: lists lessons hidden for a missing plugin");
        missing.plugins.add("NexusRime");
        List<String> r3 = missing.tutor.checkReport();
        expect(r3.stream().anyMatch(s -> s.contains("rime step 2") && s.contains("/rime")) && r3.stream().noneMatch(s -> s.contains("CRAFTING_TABLE")), "check: names steps that only read here");
        expect(missing.book.warnings().isEmpty(), "check: a clean book adds no warnings");
        Kit.MapSection broken = new Kit.MapSection();
        broken.put("lessons.x.title", "x");
        Env b = new Env(broken);
        expect(b.tutor.checkReport().stream().anyMatch(s -> s.startsWith("&c")), "check: book warnings are repeated");
    }

    // ------------------------------------------------------------------ rewards

    private static void rewards() {
        Env e = env();
        e.cfg.lessonCommands = List.of("eco give {player} 10 for {lesson}");
        e.cfg.finalCommands = List.of("give {player} diamond 1");
        e.cfg.finalXp = 700;
        Kit.TestPlayer p = e.player("Alex");
        e.tutor.start(p, "menu");
        e.tutor.next(p);
        expect(e.console.isEmpty(), "rewards: nothing before the lesson is done");
        e.tutor.next(p);
        expect(e.console.equals(List.of("eco give Alex 10 for menu")) && p.xpGiven == 50, "rewards: the lesson command runs once, with names filled in");
        expect(!p.heard("every lesson"), "rewards: no graduation yet");

        // Finish the only other lesson this player can see: graduation.
        e.tutor.start(p, "rime");
        e.tutor.skip(p);
        e.tutor.skip(p);
        e.tutor.skip(p);
        e.tutor.skip(p);
        e.tutor.skip(p);
        expect(e.tutor.stateOf(p, e.book.lesson("rime")) == Tutor.LessonState.DONE, "rewards: skipping through finishes a lesson");
        expect(p.heard("finished every lesson") && e.console.contains("give Alex diamond 1") && p.xpGiven == 50 + 50 + 700, "rewards: finishing everything pays the final reward");
        int n = e.console.size();
        e.tutor.start(p, "menu");
        e.tutor.skip(p);
        e.tutor.skip(p);
        expect(e.console.size() == n + 1 && !e.console.contains("give Alex diamond 1 "), "rewards: replaying pays the lesson reward, never the final one again");
        expect(e.console.stream().filter(s -> s.startsWith("give Alex diamond")).count() == 1, "rewards: the final reward is paid exactly once");

        // Hidden lessons do not stand in the way.
        Env h = env();
        Kit.TestPlayer q = h.player("Bea");
        h.plugins.remove("NexusRime");
        h.tutor.start(q, "menu");
        h.tutor.skip(q);
        h.tutor.skip(q);
        expect(q.heard("finished every lesson"), "rewards: only lessons the player can see count toward finishing");

        Env zero = env();
        zero.cfg.xpPerLesson = 0;
        Kit.TestPlayer z = zero.player("Zed");
        zero.tutor.start(z, "menu");
        zero.tutor.skip(z);
        zero.tutor.skip(z);
        expect(z.xpGiven == 0 || z.xpGiven == zero.cfg.finalXp, "rewards: zero xp pays no lesson xp");
        Env quiet = env();
        quiet.cfg.titles = false;
        quiet.cfg.bossBar = false;
        Kit.TestPlayer w = quiet.player("Wes");
        quiet.tutor.start(w, "menu");
        expect(quiet.tutor.barOf(w) == null, "rewards: the boss bar can be switched off");
        quiet.tutor.skip(w);
        quiet.tutor.skip(w);
        expect(w.titles.isEmpty(), "rewards: and so can the title");
    }

    // ------------------------------------------------------------------ skip, stop, resume

    private static void controls() {
        Env e = env();
        Kit.TestPlayer p = e.player("Alex");
        expect(!e.tutor.stop(p), "controls: stopping with nothing active says so");
        e.tutor.resume(p);
        expect(p.heard("not in a lesson"), "controls: resuming with nothing active says so");
        e.tutor.start(p, "rime");
        e.tutor.next(p);
        expect(e.tutor.skip(p) == Tutor.NextResult.ADVANCED && e.tutor.activeStep(p) == 2, "controls: skip passes an action step");
        int before = p.messages.size();
        e.tutor.resume(p);
        expect(p.messages.size() > before && p.heard("Open the Codex."), "controls: resume shows the current step again");
        expect(e.tutor.stop(p) && e.tutor.activeLesson(p) == null && e.tutor.barOf(p) == null, "controls: stop puts it down and clears the bar");
        e.tutor.start(p, "rime");
        expect(e.tutor.activeStep(p) == 0, "controls: a stopped lesson starts again from the top");

        // Starting the lesson you are already in carries on instead of restarting.
        e.tutor.next(p);
        e.tutor.next(p);
        expect(e.tutor.activeStep(p) == 1, "controls: (step 2 is an action step, so Next does not pass it)");
        e.tutor.start(p, "rime");
        expect(e.tutor.activeStep(p) == 1, "controls: starting the active lesson keeps your place");
        e.tutor.start(p, "menu");
        expect(e.tutor.activeLesson(p).id().equals("menu") && e.tutor.activeStep(p) == 0, "controls: starting another lesson swaps to it");

        // A lesson shortened by an edit cannot leave someone past its end.
        ProgressData d = e.store.of(p.getUniqueId());
        d.active = "menu";
        d.step = 9;
        expect(e.tutor.activeStep(p) == 1, "controls: a saved step past the end is clamped");
        d.active = "deleted_lesson";
        expect(e.tutor.activeLesson(p) == null, "controls: an active lesson that no longer exists is ignored");
        e.tutor.onCommand(p, "/rime recipe blade");
        e.tutor.onOpen(p, "Codex");
        e.tutor.tick();
        expect(e.tutor.activeStep(p) == 0, "controls: and nothing happens to it");

        e.tutor.quit(p);
        expect(e.tutor.barOf(p) == null, "controls: leaving removes the bar");
    }

    // ------------------------------------------------------------------ the join invitation

    private static void offers() {
        Env e = env();
        Kit.TestPlayer fresh = e.player("Newbie");
        fresh.played = false;
        Kit.TestPlayer old = e.player("Regular");
        expect(e.tutor.shouldOffer(fresh) && !e.tutor.shouldOffer(old), "offer: mode new invites first-time players only");
        e.tutor.offer(old);
        expect(old.messages.isEmpty(), "offer: a regular is not invited");
        e.tutor.offer(fresh);
        expect(fresh.heard("/tutorial") && fresh.heard("Start the tutorial") && fresh.titles.size() == 1, "offer: the invitation has text, a button and a title");
        expect(!e.tutor.shouldOffer(fresh), "offer: only once");
        int n = fresh.messages.size();
        e.tutor.offer(fresh);
        expect(fresh.messages.size() == n, "offer: never a second time");

        e.cfg.offerMode = "all";
        expect(e.tutor.shouldOffer(old), "offer: mode all invites regulars too");
        e.tutor.start(old, "menu");
        expect(!e.tutor.shouldOffer(old), "offer: but not someone who already started");
        Kit.TestPlayer done = e.player("Done");
        e.tutor.markDone(done.getUniqueId(), "menu");
        expect(!e.tutor.shouldOffer(done), "offer: nor someone who finished something");
        e.cfg.offerMode = "off";
        Kit.TestPlayer other = e.player("Other");
        other.played = false;
        expect(!e.tutor.shouldOffer(other), "offer: mode off invites nobody");
        e.cfg.offerMode = "new";
        e.cfg.titles = false;
        Kit.TestPlayer quiet = e.player("Quiet");
        quiet.played = false;
        e.tutor.offer(quiet);
        expect(quiet.titles.isEmpty() && quiet.heard("/tutorial"), "offer: the title can be switched off");
    }

    // ------------------------------------------------------------------ admin helpers

    private static void admin() {
        Env e = env();
        Kit.TestPlayer p = e.player("Alex");
        expect(!e.tutor.reset(p.getUniqueId(), null) && !e.tutor.reset(p.getUniqueId(), "menu"), "admin: resetting a player with no record changes nothing");
        expect(e.tutor.markDone(p.getUniqueId(), "menu") && e.tutor.stateOf(p, e.book.lesson("menu")) == Tutor.LessonState.DONE, "admin: complete marks a lesson finished");
        expect(p.xpGiven == 0 && e.console.isEmpty(), "admin: with no reward");
        expect(!e.tutor.markDone(p.getUniqueId(), "nonsense"), "admin: an unknown lesson is refused");
        e.tutor.start(p, "rime");
        expect(e.tutor.markDone(p.getUniqueId(), "rime") && e.tutor.activeLesson(p) == null && e.tutor.stateOf(p, e.book.lesson("rime")) == Tutor.LessonState.DONE,
                "admin: completing the active lesson ends it");
        expect(e.tutor.reset(p.getUniqueId(), "menu") && e.tutor.stateOf(p, e.book.lesson("menu")) == Tutor.LessonState.NEW, "admin: reset one lesson");
        expect(e.tutor.stateOf(p, e.book.lesson("rime")) == Tutor.LessonState.DONE, "admin: the others are kept");
        e.tutor.start(p, "menu");
        expect(e.tutor.reset(p.getUniqueId(), "menu") && e.tutor.activeLesson(p) == null, "admin: resetting the active lesson stops it");
        e.tutor.start(p, "menu");
        expect(e.tutor.barOf(p) != null && e.tutor.reset(p.getUniqueId(), null) && e.tutor.barOf(p) == null, "admin: reset everything clears the bar too");
        expect(e.store.peek(p.getUniqueId()) == null && e.tutor.stateOf(p, e.book.lesson("rime")) == Tutor.LessonState.NEW, "admin: and the record");
        ProgressData d = e.store.of(p.getUniqueId());
        d.graduated = true;
        d.done.add("menu");
        expect(e.tutor.reset(p.getUniqueId(), "menu") && !d.graduated, "admin: resetting a lesson takes the graduation back");
    }
}
