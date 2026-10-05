package com.nexuscraft.nexustutorial;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Every category and lesson, read from lessons.yml. A second file can be layered on top: lessons with
 * the same id replace the built-in one, new ids are added, and {@code hidden:} removes lessons.
 * Anything unreadable is skipped and reported in {@link #warnings()}, never thrown.
 */
final class LessonBook {

    static final Pattern ID = Pattern.compile("[a-z][a-z0-9_]{0,31}");
    static final int MAX_STEPS = 8;

    private final Map<String, Category> categories = new LinkedHashMap<>();
    private final Map<String, Lesson> lessons = new LinkedHashMap<>();
    private final List<String> warnings = new ArrayList<>();

    /** Forgets everything (before a reload). */
    void clear() {
        categories.clear();
        lessons.clear();
        warnings.clear();
    }

    List<String> warnings() {
        return warnings;
    }

    List<Category> categories() {
        return new ArrayList<>(categories.values());
    }

    Category category(String id) {
        return id == null ? null : categories.get(id);
    }

    List<Lesson> lessons() {
        return new ArrayList<>(lessons.values());
    }

    Lesson lesson(String id) {
        return id == null ? null : lessons.get(id.toLowerCase(Locale.ROOT));
    }

    List<Lesson> lessonsIn(String category) {
        List<Lesson> out = new ArrayList<>();
        for (Lesson l : lessons.values()) {
            if (l.category().equals(category)) {
                out.add(l);
            }
        }
        return out;
    }

    /** Reads (or layers on top) a lessons file. {@code source} names it in warnings. */
    void load(ConfigurationSection root, String source) {
        if (root == null) {
            return;
        }
        ConfigurationSection cats = root.getConfigurationSection("categories");
        if (cats != null) {
            for (String id : cats.getKeys(false)) {
                String title = cats.getString(id + ".title", "");
                String desc = cats.getString(id + ".description", "");
                if (!ID.matcher(id).matches() || title.isBlank()) {
                    warnings.add(source + ": category '" + id + "' needs a title -- skipped.");
                    continue;
                }
                categories.put(id, new Category(id, title, cats.getString(id + ".icon", "BOOK"), desc));
            }
        }
        ConfigurationSection ls = root.getConfigurationSection("lessons");
        if (ls != null) {
            for (String id : ls.getKeys(false)) {
                Lesson lesson = readLesson(ls, id, source);
                if (lesson != null) {
                    lessons.put(id, lesson);
                }
            }
        }
        for (String id : root.getStringList("hidden")) {
            if (lessons.remove(id.toLowerCase(Locale.ROOT).trim()) == null) {
                warnings.add(source + ": hidden lists '" + id + "', which is not a lesson.");
            }
        }
        // A lesson in a category nobody defined cannot be reached from the menu.
        Set<String> orphans = new LinkedHashSet<>();
        for (Lesson l : lessons.values()) {
            if (!categories.containsKey(l.category())) {
                orphans.add(l.id());
            }
        }
        for (String id : orphans) {
            warnings.add(source + ": lesson '" + id + "' is in category '" + lessons.get(id).category() + "', which does not exist -- skipped.");
            lessons.remove(id);
        }
    }

    private Lesson readLesson(ConfigurationSection ls, String id, String source) {
        if (!ID.matcher(id).matches()) {
            warnings.add(source + ": lesson id '" + id + "' must be lowercase letters, digits and underscores -- skipped.");
            return null;
        }
        String title = ls.getString(id + ".title", "");
        String category = ls.getString(id + ".category", "");
        if (title.isBlank() || category.isBlank()) {
            warnings.add(source + ": lesson '" + id + "' needs a title and a category -- skipped.");
            return null;
        }
        List<Step> steps = new ArrayList<>();
        for (String raw : ls.getStringList(id + ".steps")) {
            Step step = Step.parse(raw);
            if (step == null) {
                warnings.add(source + ": lesson '" + id + "' has a step that does not parse (" + raw + ") -- skipped.");
                continue;
            }
            steps.add(step);
        }
        if (steps.isEmpty()) {
            warnings.add(source + ": lesson '" + id + "' has no readable steps -- skipped.");
            return null;
        }
        if (steps.size() > MAX_STEPS) {
            warnings.add(source + ": lesson '" + id + "' has more than " + MAX_STEPS + " steps; the rest are ignored.");
            steps = new ArrayList<>(steps.subList(0, MAX_STEPS));
        }
        String plugin = ls.getString(id + ".plugin", "").trim();
        String permission = ls.getString(id + ".permission", "").trim();
        return new Lesson(id, category, title, ls.getString(id + ".icon", "PAPER"), plugin.isEmpty() ? null : plugin,
                permission.isEmpty() ? null : permission, ls.getString(id + ".summary", ""), steps);
    }
}
