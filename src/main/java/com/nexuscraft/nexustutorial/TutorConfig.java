package com.nexuscraft.nexustutorial;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Everything in config.yml, with every number clamped on load so a typo can never do harm. */
final class TutorConfig {

    /** Who is offered the tutorial when they join: "new" (first ever join), "all" (anyone who has never
     *  started it) or "off". */
    String offerMode = "new";
    int offerDelaySeconds = 15;
    boolean bossBar = true;
    boolean titles = true;
    int xpPerLesson = 50;
    int finalXp = 500;
    List<String> lessonCommands = new ArrayList<>();
    List<String> finalCommands = new ArrayList<>();
    String prefix = "&6&l[Tutorial] &r";
    String msgOffer = "&eNew here? Take a quick, friendly tour of the server's plugins: type &f/tutorial&e.";
    String msgLessonDone = "&a&lLesson complete: &f{lesson}&a!";
    String msgAllDone = "&6&lYou finished every lesson! &eYou know the whole server now.";

    /** Back to the built-in defaults (before a reload). */
    void reset() {
        TutorConfig d = new TutorConfig();
        offerMode = d.offerMode;
        offerDelaySeconds = d.offerDelaySeconds;
        bossBar = d.bossBar;
        titles = d.titles;
        xpPerLesson = d.xpPerLesson;
        finalXp = d.finalXp;
        lessonCommands = d.lessonCommands;
        finalCommands = d.finalCommands;
        prefix = d.prefix;
        msgOffer = d.msgOffer;
        msgLessonDone = d.msgLessonDone;
        msgAllDone = d.msgAllDone;
    }

    void load(ConfigurationSection c) {
        if (c == null) {
            return;
        }
        String mode = c.getString("offer.mode", offerMode).toLowerCase(Locale.ROOT).trim();
        offerMode = mode.equals("all") || mode.equals("off") ? mode : "new";
        offerDelaySeconds = clamp(c.getInt("offer.delay-seconds", offerDelaySeconds), 0, 300);
        bossBar = c.getBoolean("ui.boss-bar", bossBar);
        titles = c.getBoolean("ui.titles", titles);
        xpPerLesson = clamp(c.getInt("rewards.xp-per-lesson", xpPerLesson), 0, 5000);
        finalXp = clamp(c.getInt("rewards.final-xp", finalXp), 0, 100000);
        lessonCommands = cleaned(c.getStringList("rewards.lesson-commands"));
        finalCommands = cleaned(c.getStringList("rewards.final-commands"));
        prefix = c.getString("messages.prefix", prefix);
        msgOffer = c.getString("messages.offer", msgOffer);
        msgLessonDone = c.getString("messages.lesson-done", msgLessonDone);
        msgAllDone = c.getString("messages.all-done", msgAllDone);
    }

    private static List<String> cleaned(List<String> raw) {
        List<String> out = new ArrayList<>();
        for (String s : raw) {
            if (s != null && !s.isBlank()) {
                String t = s.trim();
                out.add(t.startsWith("/") ? t.substring(1) : t);
            }
        }
        return out;
    }

    static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
