package com.nexuscraft.nexustutorial;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** What the tutorial remembers about one player. */
final class ProgressData {
    /** Lessons finished. */
    final Set<String> done = new LinkedHashSet<>();
    /** The lesson in progress, or null. */
    String active;
    /** Index of the current step of {@link #active}. */
    int step;
    /** The join offer has been made (once is enough). */
    boolean offered;
    /** The "finished everything" reward has been paid. */
    boolean graduated;

    /** Flat form for the YAML file. */
    void writeTo(Map<String, Object> out, String prefix) {
        out.put(prefix + ".done", new ArrayList<>(done));
        if (active != null) {
            out.put(prefix + ".active", active);
            out.put(prefix + ".step", step);
        }
        out.put(prefix + ".offered", offered);
        out.put(prefix + ".graduated", graduated);
    }

    static ProgressData read(ConfigurationSection c, String prefix) {
        ProgressData d = new ProgressData();
        for (String id : c.getStringList(prefix + ".done")) {
            if (id != null && !id.isBlank()) {
                d.done.add(id);
            }
        }
        String active = c.getString(prefix + ".active", "");
        d.active = active.isBlank() ? null : active;
        d.step = Math.max(0, c.getInt(prefix + ".step", 0));
        d.offered = c.getBoolean(prefix + ".offered", false);
        d.graduated = c.getBoolean(prefix + ".graduated", false);
        return d;
    }

    /** Anything worth remembering? (An untouched record is not saved.) */
    boolean isBlank() {
        return done.isEmpty() && active == null && !offered && !graduated;
    }

    List<String> doneList() {
        return new ArrayList<>(done);
    }
}
