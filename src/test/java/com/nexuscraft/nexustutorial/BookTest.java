package com.nexuscraft.nexustutorial;

import org.bukkit.Bukkit;

import java.util.List;

import static com.nexuscraft.nexustutorial.Kit.expect;

/** The step grammar, command and title matching, and reading lessons.yml. */
public final class BookTest {

    public static void main(String[] args) {
        grammar();
        matching();
        book();
        progress();
        config();
        Kit.finish();
    }

    private static void grammar() {
        Step info = Step.parse("info :: Read this.");
        expect(info != null && info.kind() == StepKind.INFO && info.text().equals("Read this.") && info.arg().isEmpty(), "grammar: info");
        Step cmd = Step.parse("command rime recipe blade :: Type it.");
        expect(cmd != null && cmd.kind() == StepKind.COMMAND && cmd.arg().equals("rime recipe blade") && cmd.words().length == 3, "grammar: command with sub-words");
        Step slash = Step.parse("command /menu :: Type it.");
        expect(slash != null && slash.arg().equals("menu"), "grammar: a leading slash is tolerated");
        Step open = Step.parse("open Nexus Hub :: Open it.");
        expect(open != null && open.kind() == StepKind.OPEN && open.arg().equals("Nexus Hub"), "grammar: open keeps a multi-word title");
        Step have = Step.parse("have oak_log 8 :: Collect wood.");
        expect(have != null && have.material().equals("OAK_LOG") && have.count() == 8, "grammar: have with a count");
        expect(Step.parse("have STICK :: Get one.").count() == 1, "grammar: have without a count wants one");
        expect(Step.parse("INFO :: Capitals are fine.") != null, "grammar: the kind is case-insensitive");
        expect(Step.parse("info :: text with :: inside").text().equals("text with :: inside"), "grammar: only the first :: splits");
        expect(Step.parse("info:: no space") != null, "grammar: spacing around :: is forgiving");
        for (String bad : new String[] {null, "", "info", "info ::", ":: text", "info extra :: text", "command :: text", "open :: text", "have :: text",
                "have STICK 0 :: x", "have STICK x :: x", "have STICK 2 3 :: x", "dance :: x", "command / :: x"}) {
            expect(Step.parse(bad) == null, "grammar: rejects " + bad);
        }
        expect(Step.parse("have STICK 99999 :: x").count() == Step.MAX_COUNT, "grammar: absurd counts are capped");
    }

    private static void matching() {
        Kit.reset();
        Env.command("rime", "rimewalker", "winterhorde");
        Env.command("menu", "hub");
        java.util.function.UnaryOperator<String> canon = Env::canon;
        String[] want = {"rime", "recipe", "blade"};
        expect(CommandMatch.matches(want, "/rime recipe blade", canon), "match: exactly what was asked");
        expect(CommandMatch.matches(want, "rime recipe blade", canon), "match: no slash is fine");
        expect(CommandMatch.matches(want, "/RIME Recipe BLADE", canon), "match: case does not matter");
        expect(CommandMatch.matches(want, "/rimewalker recipe blade", canon), "match: an alias counts");
        expect(CommandMatch.matches(want, "/nexusrime:rime recipe blade", canon), "match: a namespaced label is resolved by the server's command map");
        expect(CommandMatch.matches(want, "/rime recipe blade extra words", canon), "match: extra words after are fine");
        expect(CommandMatch.matches(new String[] {"rime"}, "/rime status", canon), "match: a bare command matches any use of it");
        expect(!CommandMatch.matches(want, "/rime recipe dagger", canon), "match: a different sub-word does not");
        expect(!CommandMatch.matches(want, "/rime recipe", canon), "match: too few words does not");
        expect(!CommandMatch.matches(want, "/menu recipe blade", canon), "match: a different command does not");
        expect(!CommandMatch.matches(want, "/nothing recipe blade", canon), "match: an unknown command does not");
        expect(!CommandMatch.matches(new String[] {"ghost"}, "/ghost", canon), "match: a step for a command the server lacks never matches");
        expect(!CommandMatch.matches(want, "", canon) && !CommandMatch.matches(want, "/", canon) && !CommandMatch.matches(want, null, canon)
                && !CommandMatch.matches(null, "/rime", canon) && !CommandMatch.matches(new String[0], "/rime", canon), "match: empty input never matches");
        expect(CommandMatch.matches(new String[] {"hub"}, "/menu", canon), "match: both sides are resolved to the real name");

        expect(CommandMatch.titleHas("§6§lNexus Hub", "nexus hub"), "title: colour codes and case are ignored");
        expect(CommandMatch.titleHas("Nexus §6§lHub", "nexus hub"), "title: a colour code in the middle of the words is ignored");
        expect(CommandMatch.titleHas("&bNexus &eHub", "Nexus Hub"), "title: ampersand codes are ignored too");
        expect(CommandMatch.titleHas("Nexus Hub", "&6Nexus &lHub"), "title: colour codes in the fragment are ignored");
        expect(CommandMatch.titleHas("The Crafters Guild Vendor", "Guild Vendor"), "title: a fragment matches");
        expect(!CommandMatch.titleHas("Chest", "Codex"), "title: other menus do not");
        expect(!CommandMatch.titleHas(null, "x") && !CommandMatch.titleHas("x", null) && !CommandMatch.titleHas("x", "  "), "title: null and blank never match");
        expect(CommandMatch.strip("&aGreen §lBold &zNot").equals("Green Bold &zNot"), "strip: only real colour codes go");
        expect(Bukkit.COMMANDS.size() == 5, "match: test setup registered five labels");
        Bukkit.COMMANDS.put("zed", new org.bukkit.command.Command() {
            @Override
            public String getName() {
                return "Zed";
            }
        });
        Bukkit.COMMANDS.put("zedalias", new org.bukkit.command.Command() {
            @Override
            public String getName() {
                return "zed";
            }
        });
        expect(CommandMatch.matches(new String[] {"zedalias"}, "/Zed", canon), "match: canonical names that differ only by case are the same command");
    }

    private static void book() {
        Kit.reset();
        LessonBook b = new LessonBook();
        b.load(Env.sample(), "t");
        expect(b.warnings().isEmpty(), "book: the sample loads cleanly");
        expect(b.categories().size() == 2 && b.lessons().size() == 3, "book: categories and lessons are read");
        expect(b.lesson("RIME") != null && b.lesson("rime").steps().size() == 5, "book: lookup is case-insensitive");
        expect(b.lesson("rime").plugin().equals("NexusRime") && b.lesson("rime").permission() == null, "book: plugin and permission");
        expect(b.lesson("secret").permission().equals("nexustutorial.staff"), "book: a permission is kept");
        expect(b.lesson("nope") == null && b.lesson(null) == null && b.category(null) == null, "book: unknown ids are null");
        expect(b.lessonsIn("start").size() == 2 && b.lessonsIn("fun").size() == 1, "book: lessons by category");

        Kit.MapSection bad = new Kit.MapSection();
        bad.put("categories.start.title", "S").put("categories.start.icon", "COMPASS").put("categories.start.description", "d");
        bad.put("categories.Bad Id.title", "x");
        bad.put("categories.notitle.icon", "BOOK");
        bad.put("lessons.good.category", "start").put("lessons.good.title", "Good").put("lessons.good.steps", List.of("info :: a", "oops", "info :: b"));
        bad.put("lessons.Bad Id.category", "start").put("lessons.Bad Id.title", "x").put("lessons.Bad Id.steps", List.of("info :: a"));
        bad.put("lessons.notitle.category", "start").put("lessons.notitle.steps", List.of("info :: a"));
        bad.put("lessons.nocat.title", "No category").put("lessons.nocat.steps", List.of("info :: a"));
        bad.put("lessons.nosteps.category", "start").put("lessons.nosteps.title", "t").put("lessons.nosteps.steps", List.of("garbage"));
        bad.put("lessons.lost.category", "ghost").put("lessons.lost.title", "Lost").put("lessons.lost.steps", List.of("info :: a"));
        List<String> nine = new java.util.ArrayList<>();
        for (int i = 0; i < 9; i++) {
            nine.add("info :: step " + i);
        }
        bad.put("lessons.long.category", "start").put("lessons.long.title", "Long").put("lessons.long.steps", nine);
        LessonBook w = new LessonBook();
        w.load(bad, "bad.yml");
        expect(w.lesson("good") != null && w.lesson("good").steps().size() == 2, "book: an unreadable step is dropped, the rest kept");
        expect(w.lesson("long").steps().size() == LessonBook.MAX_STEPS, "book: steps past the limit are ignored");
        expect(w.lesson("lost") == null, "book: a lesson in an unknown category is dropped");
        expect(w.lessons().size() == 2 && w.categories().size() == 1, "book: bad ids, missing titles and empty lessons are skipped");
        expect(w.warnings().size() >= 8 && w.warnings().stream().allMatch(s -> s.startsWith("bad.yml")), "book: every problem is reported with the file name");

        // Layering: replace one, add one, hide one.
        LessonBook l = new LessonBook();
        l.load(Env.sample(), "base");
        Kit.MapSection custom = new Kit.MapSection();
        custom.put("lessons.menu.category", "fun").put("lessons.menu.title", "My Menu").put("lessons.menu.steps", List.of("info :: mine"));
        custom.put("lessons.extra.category", "start").put("lessons.extra.title", "Extra").put("lessons.extra.steps", List.of("info :: new"));
        custom.put("hidden", List.of("rime", "ghost"));
        l.load(custom, "custom");
        expect(l.lesson("menu").title().equals("My Menu") && l.lesson("menu").category().equals("fun"), "book: a custom lesson replaces the built-in one");
        expect(l.lesson("extra") != null, "book: a custom lesson is added");
        expect(l.lesson("rime") == null, "book: hidden lessons are removed");
        expect(l.warnings().size() == 1 && l.warnings().get(0).contains("ghost"), "book: hiding something that does not exist is reported");
        l.clear();
        expect(l.lessons().isEmpty() && l.categories().isEmpty() && l.warnings().isEmpty(), "book: clear forgets everything");
        l.load(null, "x");
        expect(l.lessons().isEmpty(), "book: a missing file is fine");
    }

    private static void progress() {
        ProgressData d = new ProgressData();
        expect(d.isBlank(), "progress: a new record is blank");
        d.done.add("rime");
        d.done.add("menu");
        d.active = "economy";
        d.step = 3;
        d.offered = true;
        d.graduated = true;
        java.util.Map<String, Object> flat = new java.util.LinkedHashMap<>();
        d.writeTo(flat, "p");
        Kit.MapSection m = new Kit.MapSection();
        flat.forEach(m::put);
        ProgressData r = ProgressData.read(m, "p");
        expect(r.done.equals(d.done) && "economy".equals(r.active) && r.step == 3 && r.offered && r.graduated, "progress: survives a round trip");
        expect(!r.isBlank() && r.doneList().size() == 2, "progress: a used record is not blank");
        ProgressData e = ProgressData.read(new Kit.MapSection(), "nobody");
        expect(e.isBlank() && e.active == null && e.step == 0, "progress: reading nothing gives a blank record");
        Kit.MapSection odd = new Kit.MapSection();
        odd.put("p.done", List.of("a", "", "b")).put("p.step", -4).put("p.active", "  ");
        ProgressData o = ProgressData.read(odd, "p");
        expect(o.done.size() == 2 && o.step == 0 && o.active == null, "progress: blanks and negative steps are cleaned");
        java.util.Map<String, Object> flat2 = new java.util.LinkedHashMap<>();
        new ProgressData().writeTo(flat2, "q");
        expect(!flat2.containsKey("q.active"), "progress: no active lesson, nothing written for it");
    }

    private static void config() {
        TutorConfig c = new TutorConfig();
        Kit.MapSection m = new Kit.MapSection();
        m.put("offer.mode", " ALL ").put("offer.delay-seconds", 9999).put("rewards.xp-per-lesson", -5).put("rewards.final-xp", 99999999)
                .put("rewards.lesson-commands", List.of("/eco give {player} 5", "  ", "say hi")).put("ui.boss-bar", false).put("messages.prefix", "&a>");
        c.load(m);
        expect(c.offerMode.equals("all") && c.offerDelaySeconds == 300, "config: mode is normalised and the delay clamped");
        expect(c.xpPerLesson == 0 && c.finalXp == 100000, "config: xp is clamped");
        expect(c.lessonCommands.equals(List.of("eco give {player} 5", "say hi")), "config: reward commands lose the slash and blanks");
        expect(!c.bossBar && c.prefix.equals("&a>"), "config: switches and text load");
        Kit.MapSection junk = new Kit.MapSection();
        junk.put("offer.mode", "sometimes");
        TutorConfig j = new TutorConfig();
        j.load(junk);
        expect(j.offerMode.equals("new"), "config: an unknown mode falls back to new");
        c.reset();
        expect(c.offerMode.equals("new") && c.bossBar && c.xpPerLesson == 50 && c.lessonCommands.isEmpty() && c.offerDelaySeconds == 15, "config: reset restores the defaults");
        c.load(null);
        expect(c.offerMode.equals("new"), "config: a missing file is fine");
        expect(Pretty.of("CRAFTING_TABLE").equals("Crafting Table") && Pretty.of("A__B").equals("A B"), "pretty: item names");
    }
}
