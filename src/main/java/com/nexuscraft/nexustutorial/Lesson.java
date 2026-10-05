package com.nexuscraft.nexustutorial;

import java.util.List;

/** One lesson: a short guided walk-through of one plugin. */
record Lesson(String id, String category, String title, String icon, String plugin, String permission,
              String summary, List<Step> steps) {

    Lesson {
        steps = List.copyOf(steps);
    }
}
