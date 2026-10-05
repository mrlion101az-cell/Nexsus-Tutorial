package com.nexuscraft.nexustutorial;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** /tutorial -- open the menu, start a lesson, move on, or (for admins) check and reset. */
final class TutorCommand implements TabExecutor {

    private final Tutor tutor;
    private final Runnable reload;

    TutorCommand(Tutor tutor, Runnable reload) {
        this.tutor = tutor;
        this.reload = reload;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "reload", "check", "reset", "complete" -> admin(sender, sub, args);
            case "" -> {
                if (sender instanceof Player p) {
                    TutorGui.openHub(tutor, p);
                } else {
                    sender.sendMessage(ColorText.translate("&7Players open the tutorial with /tutorial. Admins: /tutorial check, reload, reset, complete."));
                }
            }
            default -> player(sender, sub, args);
        }
        return true;
    }

    private void player(CommandSender sender, String sub, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(ColorText.translate("&cOnly players can take the tutorial."));
            return;
        }
        switch (sub) {
            case "start" -> {
                if (args.length < 2) {
                    TutorGui.openHub(tutor, p);
                    return;
                }
                switch (tutor.start(p, args[1])) {
                    case UNKNOWN -> p.sendMessage(ColorText.translate("&cThere is no lesson called " + args[1] + ". Try &f/tutorial list&c."));
                    case UNAVAILABLE -> p.sendMessage(ColorText.translate("&cThat lesson is not available to you on this server."));
                    default -> { }
                }
            }
            case "next" -> {
                if (tutor.next(p) == Tutor.NextResult.NO_LESSON) {
                    p.sendMessage(ColorText.translate("&7You are not in a lesson. Type &f/tutorial &7to pick one."));
                }
            }
            case "skip" -> {
                if (tutor.skip(p) == Tutor.NextResult.NO_LESSON) {
                    p.sendMessage(ColorText.translate("&7You are not in a lesson. Type &f/tutorial &7to pick one."));
                }
            }
            case "stop" -> p.sendMessage(ColorText.translate(tutor.stop(p)
                    ? "&7Lesson put down. Type &f/tutorial &7to start another." : "&7You are not in a lesson."));
            case "list" -> list(p);
            case "progress", "status" -> progress(p);
            default -> p.sendMessage(ColorText.translate("&7/tutorial &8| &7start <lesson>, next, skip, stop, list, progress"
                    + (p.hasPermission("nexustutorial.admin") ? " &8| &7check, reload, reset <player> [lesson], complete <player> <lesson>" : "")));
        }
    }

    private void list(Player p) {
        for (Category c : tutor.visibleCategories(p)) {
            List<Lesson> in = tutor.visibleIn(p, c.id());
            p.sendMessage(ColorText.translate("&6&l" + c.title() + " &7(" + tutor.doneCount(p, in) + "/" + in.size() + ")"));
            StringBuilder line = new StringBuilder("&8  ");
            for (Lesson l : in) {
                line.append(tutor.stateOf(p, l) == Tutor.LessonState.DONE ? "&a" : "&f").append(l.id()).append("&8, ");
            }
            p.sendMessage(ColorText.translate(line.substring(0, line.length() - 2)));
        }
        p.sendMessage(ColorText.translate("&7Start one with &f/tutorial start <lesson>&7, or just open &f/tutorial&7."));
    }

    private void progress(Player p) {
        List<Lesson> mine = tutor.visible(p);
        p.sendMessage(ColorText.translate("&6Tutorial: &f" + tutor.doneCount(p, mine) + " of " + mine.size() + " lessons finished."));
        Lesson active = tutor.activeLesson(p);
        if (active != null) {
            p.sendMessage(ColorText.translate("&7In progress: &f" + active.title() + " &7(step " + (tutor.activeStep(p) + 1) + " of " + active.steps().size() + ")"));
        }
    }

    private void admin(CommandSender sender, String sub, String[] args) {
        if (!sender.hasPermission("nexustutorial.admin")) {
            sender.sendMessage(ColorText.translate("&cYou don't have permission for that."));
            return;
        }
        switch (sub) {
            case "reload" -> {
                reload.run();
                sender.sendMessage(ColorText.translate("&aNexusTutorial reloaded."));
            }
            case "check" -> {
                for (String line : tutor.checkReport()) {
                    sender.sendMessage(ColorText.translate(line));
                }
            }
            case "reset" -> {
                if (args.length < 2) {
                    sender.sendMessage(ColorText.translate("&c/tutorial reset <player> [lesson]"));
                    return;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    sender.sendMessage(ColorText.translate("&cNo player named " + args[1] + " is online."));
                    return;
                }
                boolean changed = tutor.reset(target.getUniqueId(), args.length > 2 ? args[2].toLowerCase(Locale.ROOT) : null);
                sender.sendMessage(ColorText.translate(changed ? "&aReset " + target.getName() + "'s tutorial progress"
                        + (args.length > 2 ? " for " + args[2] : "") + "." : "&7Nothing to reset for " + target.getName() + "."));
            }
            default -> {
                if (args.length < 3) {
                    sender.sendMessage(ColorText.translate("&c/tutorial complete <player> <lesson>"));
                    return;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    sender.sendMessage(ColorText.translate("&cNo player named " + args[1] + " is online."));
                    return;
                }
                boolean ok = tutor.markDone(target.getUniqueId(), args[2].toLowerCase(Locale.ROOT));
                sender.sendMessage(ColorText.translate(ok ? "&aMarked " + args[2] + " as finished for " + target.getName() + " (no reward)."
                        : "&cThere is no lesson called " + args[2] + "."));
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        boolean admin = sender.hasPermission("nexustutorial.admin");
        if (args.length == 1) {
            out.addAll(List.of("start", "next", "skip", "stop", "list", "progress"));
            if (admin) {
                out.addAll(List.of("check", "reload", "reset", "complete"));
            }
            out.removeIf(s -> !s.startsWith(args[0].toLowerCase(Locale.ROOT)));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("start") && sender instanceof Player p) {
            for (Lesson l : tutor.visible(p)) {
                if (l.id().startsWith(args[1].toLowerCase(Locale.ROOT))) {
                    out.add(l.id());
                }
            }
        } else if (admin && args.length == 2 && (args[0].equalsIgnoreCase("reset") || args[0].equalsIgnoreCase("complete"))) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))) {
                    out.add(p.getName());
                }
            }
        } else if (admin && args.length == 3 && (args[0].equalsIgnoreCase("reset") || args[0].equalsIgnoreCase("complete"))) {
            for (Lesson l : tutor.book().lessons()) {
                if (l.id().startsWith(args[2].toLowerCase(Locale.ROOT))) {
                    out.add(l.id());
                }
            }
        }
        return out;
    }
}
