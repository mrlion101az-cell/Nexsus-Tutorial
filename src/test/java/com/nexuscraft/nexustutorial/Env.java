package com.nexuscraft.nexustutorial;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** A tutorial wired to a stub server: installed plugins, registered commands, a console log. */
final class Env {

    final Kit.TestPlugin plugin = new Kit.TestPlugin();
    final TutorConfig cfg = new TutorConfig();
    final LessonBook book = new LessonBook();
    final ProgressStore store = new ProgressStore(null, w -> { });
    final Set<String> plugins = new HashSet<>();
    final List<String> console = new ArrayList<>();
    final Tutor tutor;

    Env(Kit.MapSection lessons) {
        Kit.reset();
        book.load(lessons, "test");
        tutor = new Tutor(cfg, book, store, plugins::contains, Env::canon, console::add);
    }

    static String canon(String label) {
        Command c = Bukkit.getCommandMap().getCommand(label);
        return c == null ? null : c.getName();
    }

    /** Registers a command with aliases, as a plugin.yml would. */
    static void command(String name, String... aliases) {
        Command c = new Command() {
            @Override
            public String getName() {
                return name;
            }
        };
        Bukkit.COMMANDS.put(name, c);
        for (String a : aliases) {
            Bukkit.COMMANDS.put(a, c);
        }
    }

    Kit.TestPlayer player(String name) {
        return Kit.online(new Kit.TestPlayer(name, 0, 0));
    }

    /** The small lesson book most tests use. */
    static Kit.MapSection sample() {
        Kit.MapSection m = new Kit.MapSection();
        m.put("categories.start.title", "Getting Started").put("categories.start.icon", "COMPASS").put("categories.start.description", "Basics");
        m.put("categories.fun.title", "Fun").put("categories.fun.icon", "CAKE").put("categories.fun.description", "Games");
        m.put("lessons.rime.category", "start").put("lessons.rime.title", "The Long Winter").put("lessons.rime.icon", "BLUE_ICE")
                .put("lessons.rime.plugin", "NexusRime").put("lessons.rime.summary", "Hordes");
        m.put("lessons.rime.steps", List.of(
                "info :: A horde walks in winter.",
                "command rime recipe blade :: Type /rime recipe blade.",
                "open Codex :: Open the Codex.",
                "have CRAFTING_TABLE 2 :: Get two crafting tables.",
                "info :: Stay warm."));
        m.put("lessons.menu.category", "start").put("lessons.menu.title", "The Menu").put("lessons.menu.icon", "ENCHANTED_BOOK")
                .put("lessons.menu.summary", "One menu");
        m.put("lessons.menu.steps", List.of("info :: Open it with /menu.", "info :: Done."));
        m.put("lessons.secret.category", "fun").put("lessons.secret.title", "Staff Only").put("lessons.secret.icon", "BARRIER")
                .put("lessons.secret.permission", "nexustutorial.staff").put("lessons.secret.summary", "Hidden");
        m.put("lessons.secret.steps", List.of("info :: Shh.", "info :: Bye."));
        return m;
    }
}
