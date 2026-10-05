package com.nexuscraft.nexustutorial;

import java.util.Locale;

/**
 * One step of a lesson, written in lessons.yml as {@code "<kind> [argument] :: <text>"}:
 *
 * <pre>
 *   info :: Plain explanation. The player presses Next.
 *   command rime recipe blade :: Type /rime recipe blade.
 *   open Codex :: Open the Nexus Codex.
 *   have CRAFTING_TABLE 1 :: Craft a crafting table.
 * </pre>
 */
record Step(StepKind kind, String arg, String text) {

    static final int MAX_COUNT = 2304;

    /** Parses one step, or returns null if it does not follow the grammar. */
    static Step parse(String raw) {
        if (raw == null) {
            return null;
        }
        int cut = raw.indexOf("::");
        if (cut < 0) {
            return null;
        }
        String text = raw.substring(cut + 2).trim();
        String head = raw.substring(0, cut).trim();
        if (text.isEmpty() || head.isEmpty()) {
            return null;
        }
        String[] parts = head.split("\\s+", 2);
        StepKind kind;
        switch (parts[0].toLowerCase(Locale.ROOT)) {
            case "info" -> kind = StepKind.INFO;
            case "command" -> kind = StepKind.COMMAND;
            case "open" -> kind = StepKind.OPEN;
            case "have" -> kind = StepKind.HAVE;
            default -> {
                return null;
            }
        }
        String arg = parts.length > 1 ? parts[1].trim() : "";
        if (kind == StepKind.INFO && !arg.isEmpty()) {
            return null;
        }
        if (kind != StepKind.INFO && arg.isEmpty()) {
            return null;
        }
        if (kind == StepKind.COMMAND && arg.startsWith("/")) {
            arg = arg.substring(1).trim();
            if (arg.isEmpty()) {
                return null;
            }
        }
        if (kind == StepKind.HAVE) {
            String[] w = arg.split("\\s+");
            if (w.length > 2) {
                return null;
            }
            if (w.length == 2) {
                try {
                    if (Integer.parseInt(w[1]) < 1) {
                        return null;
                    }
                } catch (NumberFormatException bad) {
                    return null;
                }
            }
        }
        return new Step(kind, arg, text);
    }

    /** For a HAVE step: the material name, upper-case. */
    String material() {
        return arg.split("\\s+")[0].toUpperCase(Locale.ROOT);
    }

    /** For a HAVE step: how many are needed (at least 1, never absurd). */
    int count() {
        String[] w = arg.split("\\s+");
        if (w.length < 2) {
            return 1;
        }
        try {
            return Math.max(1, Math.min(MAX_COUNT, Integer.parseInt(w[1])));
        } catch (NumberFormatException bad) {
            return 1;
        }
    }

    /** For a COMMAND step: the command words, without the slash. */
    String[] words() {
        return arg.trim().split("\\s+");
    }
}
