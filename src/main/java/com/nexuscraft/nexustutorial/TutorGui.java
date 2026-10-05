package com.nexuscraft.nexustutorial;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The tutorial's menus: a hub of categories, and a paged list of lessons in each. Plain chest menus,
 * so they work the same for Java and Bedrock players. Every click is looked up in the menu's own
 * action table, so nothing about a click depends on the item's name.
 */
final class TutorGui implements InventoryHolder {

    static final int SIZE = 54;
    static final int PER_PAGE = 45;
    /** Where the category buttons sit on the hub (two rows of seven). */
    static final int[] HUB_SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};

    final String category;
    final int page;
    final Map<Integer, String> actions = new HashMap<>();
    private Inventory inventory;

    private TutorGui(String category, int page) {
        this.category = category;
        this.page = page;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    // ------------------------------------------------------------------ building

    static void openHub(Tutor tutor, Player p) {
        TutorGui gui = new TutorGui(null, 0);
        gui.inventory = Bukkit.createInventory(gui, SIZE, ColorText.translate("&6&lServer Tutorial"));
        List<Lesson> mine = tutor.visible(p);
        int done = tutor.doneCount(p, mine);
        gui.put(4, button(Material.WRITABLE_BOOK, "&e&lYour progress",
                List.of("&f" + done + " of " + mine.size() + " lessons finished",
                        done == mine.size() && !mine.isEmpty() ? "&aYou know the whole server!" : "&7Pick a topic below to learn it.")), null);
        List<Category> cats = tutor.visibleCategories(p);
        for (int i = 0; i < cats.size() && i < HUB_SLOTS.length; i++) {
            Category c = cats.get(i);
            List<Lesson> in = tutor.visibleIn(p, c.id());
            int d = tutor.doneCount(p, in);
            List<String> lore = new ArrayList<>();
            lore.add("&7" + c.description());
            lore.add("");
            lore.add((d == in.size() ? "&a" : "&f") + d + "/" + in.size() + " lessons finished");
            lore.add("&eClick to open");
            gui.put(HUB_SLOTS[i], button(material(c.icon(), Material.BOOK), "&6&l" + c.title(), lore), "cat:" + c.id());
        }
        Lesson active = tutor.activeLesson(p);
        if (active != null) {
            int step = tutor.activeStep(p);
            gui.put(40, button(Material.LIME_DYE, "&a&lContinue: " + active.title(),
                    List.of("&7You are on step " + (step + 1) + " of " + active.steps().size() + ".", "&eClick to show it again")), "continue");
            gui.put(44, button(Material.BARRIER, "&c&lStop this lesson", List.of("&7Put it down. You can start it again any time.")), "stop");
        }
        gui.put(49, button(Material.OAK_DOOR, "&7Close", List.of()), "close");
        p.openInventory(gui.inventory);
    }

    static void openCategory(Tutor tutor, Player p, String categoryId, int page) {
        List<Lesson> in = tutor.visibleIn(p, categoryId);
        Category c = tutor.book().category(categoryId);
        if (c == null || in.isEmpty()) {
            openHub(tutor, p);
            return;
        }
        int pages = Math.max(1, (in.size() + PER_PAGE - 1) / PER_PAGE);
        int pg = Math.max(0, Math.min(pages - 1, page));
        TutorGui gui = new TutorGui(categoryId, pg);
        gui.inventory = Bukkit.createInventory(gui, SIZE, ColorText.translate("&6&l" + c.title()));
        for (int i = 0; i < PER_PAGE; i++) {
            int idx = pg * PER_PAGE + i;
            if (idx >= in.size()) {
                break;
            }
            Lesson l = in.get(idx);
            Tutor.LessonState state = tutor.stateOf(p, l);
            List<String> lore = new ArrayList<>();
            lore.add("&7" + l.summary());
            lore.add("");
            lore.add("&8" + l.steps().size() + " steps");
            switch (state) {
                case DONE -> {
                    lore.add("&a✔ Finished");
                    lore.add("&eClick to do it again");
                }
                case ACTIVE -> {
                    lore.add("&6In progress");
                    lore.add("&eClick to carry on");
                }
                default -> lore.add("&eClick to start");
            }
            String prefix = state == Tutor.LessonState.DONE ? "&a" : state == Tutor.LessonState.ACTIVE ? "&6" : "&f";
            gui.put(i, button(material(l.icon(), Material.PAPER), prefix + "&l" + l.title(), lore), "lesson:" + l.id());
        }
        gui.put(45, button(Material.ARROW, "&e◀ Back to topics", List.of()), "hub");
        if (pg > 0) {
            gui.put(48, button(Material.SPECTRAL_ARROW, "&ePrevious page", List.of("&7Page " + pg + " of " + pages)), "page:" + (pg - 1));
        }
        gui.put(49, button(Material.PAPER, "&7Page " + (pg + 1) + " of " + pages,
                List.of("&f" + tutor.doneCount(p, in) + "/" + in.size() + " finished")), null);
        if (pg < pages - 1) {
            gui.put(50, button(Material.SPECTRAL_ARROW, "&eNext page", List.of("&7Page " + (pg + 2) + " of " + pages)), "page:" + (pg + 1));
        }
        gui.put(53, button(Material.OAK_DOOR, "&7Close", List.of()), "close");
        p.openInventory(gui.inventory);
    }

    // ------------------------------------------------------------------ clicking

    /** Does what a click on this slot asks for. Returns false if the slot does nothing. */
    boolean click(Tutor tutor, Player p, int slot) {
        String action = actions.get(slot);
        if (action == null) {
            return false;
        }
        if (action.equals("close")) {
            p.closeInventory();
        } else if (action.equals("hub")) {
            openHub(tutor, p);
        } else if (action.equals("continue")) {
            p.closeInventory();
            tutor.resume(p);
        } else if (action.equals("stop")) {
            tutor.stop(p);
            openHub(tutor, p);
        } else if (action.startsWith("cat:")) {
            openCategory(tutor, p, action.substring(4), 0);
        } else if (action.startsWith("page:")) {
            try {
                openCategory(tutor, p, category, Integer.parseInt(action.substring(5)));
            } catch (NumberFormatException bad) {
                return false;
            }
        } else if (action.startsWith("lesson:")) {
            p.closeInventory();
            tutor.start(p, action.substring(7));
        } else {
            return false;
        }
        return true;
    }

    // ------------------------------------------------------------------ small helpers

    private void put(int slot, ItemStack item, String action) {
        inventory.setItem(slot, item);
        if (action != null) {
            actions.put(slot, action);
        }
    }

    private static ItemStack button(Material m, String name, List<String> lore) {
        ItemStack item = new ItemStack(m);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ColorText.translate(name));
        List<String> out = new ArrayList<>();
        for (String s : lore) {
            out.add(ColorText.translate(s));
        }
        meta.setLore(out);
        item.setItemMeta(meta);
        return item;
    }

    static Material material(String name, Material fallback) {
        Material m = name == null ? null : Material.matchMaterial(name);
        return m == null || m == Material.AIR ? fallback : m;
    }
}
