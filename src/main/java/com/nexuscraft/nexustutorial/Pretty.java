package com.nexuscraft.nexustutorial;

/** "CRAFTING_TABLE" -&gt; "Crafting Table". */
final class Pretty {

    private Pretty() {
    }

    static String of(String id) {
        StringBuilder sb = new StringBuilder();
        for (String part : id.toLowerCase(java.util.Locale.ROOT).split("_")) {
            if (part.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }
}
