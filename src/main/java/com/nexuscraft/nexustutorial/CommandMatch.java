package com.nexuscraft.nexustutorial;

import java.util.Locale;
import java.util.function.UnaryOperator;

/** Does what a player typed count as the command a step asked for? */
final class CommandMatch {

    private CommandMatch() {
    }

    /**
     * @param expected the step's command words, e.g. {@code ["rime", "recipe", "blade"]}
     * @param typed    what the player typed, e.g. {@code "/RIMEWALKER recipe blade extra"}
     * @param canon    maps a command label (name or alias, maybe "plugin:label") to the command's one
     *                 true name, or null if the server has no such command
     */
    static boolean matches(String[] expected, String typed, UnaryOperator<String> canon) {
        if (expected == null || expected.length == 0 || typed == null) {
            return false;
        }
        String line = typed.trim();
        if (line.startsWith("/")) {
            line = line.substring(1);
        }
        if (line.isEmpty()) {
            return false;
        }
        String[] words = line.split("\\s+");
        if (words.length < expected.length) {
            return false;
        }
        String want = canon.apply(expected[0]);
        String got = canon.apply(words[0]);
        if (want == null || got == null || !want.equalsIgnoreCase(got)) {
            return false;
        }
        for (int i = 1; i < expected.length; i++) {
            if (!expected[i].toLowerCase(Locale.ROOT).equals(words[i].toLowerCase(Locale.ROOT))) {
                return false;
            }
        }
        return true;
    }

    /** True if the title of an opened menu contains the fragment (case-insensitive, colour codes ignored). */
    static boolean titleHas(String title, String fragment) {
        if (title == null || fragment == null || fragment.isBlank()) {
            return false;
        }
        return strip(title).toLowerCase(Locale.ROOT).contains(strip(fragment).toLowerCase(Locale.ROOT));
    }

    /** Removes section-sign and ampersand colour codes. */
    static String strip(String s) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if ((c == '§' || c == '&') && i + 1 < s.length() && "0123456789abcdefklmnorABCDEFKLMNOR".indexOf(s.charAt(i + 1)) >= 0) {
                i++;
                continue;
            }
            out.append(c);
        }
        return out.toString();
    }
}
