package com.nexuscraft.nexustutorial;

import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

import static com.nexuscraft.nexustutorial.Kit.expect;

/** The menus, the listener that feeds the tutorial, and /tutorial. */
public final class GuiTest {

    static final class Console implements CommandSender {
        final List<String> out = new ArrayList<>();
        boolean admin;

        @Override
        public String getName() {
            return "CONSOLE";
        }

        @Override
        public boolean hasPermission(String node) {
            return admin;
        }

        @Override
        public void sendMessage(String message) {
            out.add(message);
        }

        @Override
        public void sendMessage(net.kyori.adventure.text.Component component) {
            out.add(component.toString());
        }

        boolean heard(String f) {
            return out.stream().anyMatch(s -> s.contains(f));
        }
    }

    public static void main(String[] args) {
        hub();
        paging();
        listener();
        command();
        store();
        Kit.finish();
    }

    private static Env env() {
        Env e = new Env(Env.sample());
        Env.command("rime", "rimewalker");
        e.plugins.add("NexusRime");
        return e;
    }

    private static TutorGui gui(Kit.TestPlayer p) {
        return (TutorGui) p.opened.getHolder();
    }

    private static InventoryClickEvent click(Kit.TestPlayer p, Inventory top, int slot) {
        InventoryView view = new InventoryView();
        view.setTopInventory(top);
        view.setPlayer(p);
        return new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER, slot, ClickType.LEFT, InventoryAction.PICKUP_ALL);
    }

    private static int slotWith(TutorGui g, String action) {
        for (java.util.Map.Entry<Integer, String> e : g.actions.entrySet()) {
            if (e.getValue().equals(action)) {
                return e.getKey();
            }
        }
        return -1;
    }

    // ------------------------------------------------------------------ the hub and category pages

    private static void hub() {
        Env e = env();
        Kit.TestPlayer p = e.player("Alex");
        TutorGui.openHub(e.tutor, p);
        expect(p.opened != null && p.opened.getHolder() instanceof TutorGui, "hub: opens a menu owned by the tutorial");
        TutorGui g = gui(p);
        expect(slotWith(g, "cat:start") == TutorGui.HUB_SLOTS[0], "hub: the first category is in the first slot");
        expect(slotWith(g, "cat:fun") == -1, "hub: a category with nothing for this player is not shown");
        expect(p.opened.getItem(4).getItemMeta().getLore().get(0).contains("0 of 2"), "hub: shows overall progress");
        expect(g.actions.get(49).equals("close") && slotWith(g, "continue") == -1 && slotWith(g, "stop") == -1, "hub: no Continue or Stop with nothing running");
        expect(p.opened.getItem(TutorGui.HUB_SLOTS[0]).getType() == Material.COMPASS, "hub: the category icon is used");
        expect(p.opened.getItem(TutorGui.HUB_SLOTS[0]).getItemMeta().getLore().stream().anyMatch(s -> s.contains("0/2")), "hub: and its progress");

        // Open the category through a real click event.
        Inventory top = p.opened;
        InventoryClickEvent ev = click(p, top, TutorGui.HUB_SLOTS[0]);
        new TutorListener(e.plugin, e.cfg, e.tutor).onClick(ev);
        expect(ev.isCancelled(), "hub: clicks never move items");
        TutorGui cat = gui(p);
        expect(cat.category.equals("start") && p.opened != top, "hub: clicking a category opens it");
        expect(cat.actions.size() >= 4 && slotWith(cat, "hub") == 45 && slotWith(cat, "close") == 53, "category: has Back and Close");
        expect(slotWith(cat, "page:0") == -1 && slotWith(cat, "page:1") == -1, "category: one page needs no paging buttons");
        int rime = slotWith(cat, "lesson:rime");
        int menu = slotWith(cat, "lesson:menu");
        expect(rime >= 0 && menu >= 0 && slotWith(cat, "lesson:secret") == -1, "category: lists the lessons the player can take");
        expect(p.opened.getItem(rime).getItemMeta().getDisplayName().contains("The Long Winter") && p.opened.getItem(rime).getType() == Material.BLUE_ICE,
                "category: each lesson shows its title and icon");
        expect(p.opened.getItem(rime).getItemMeta().getLore().stream().anyMatch(s -> s.contains("Click to start") || s.contains("5 steps")), "category: and its length");

        // Back, then in again, then start a lesson.
        expect(cat.click(e.tutor, p, 45) && gui(p).category == null, "category: Back returns to the hub");
        TutorGui.openCategory(e.tutor, p, "start", 0);
        cat = gui(p);
        expect(cat.click(e.tutor, p, slotWith(cat, "lesson:rime")), "category: clicking a lesson works");
        expect(p.opened == null && e.tutor.activeLesson(p).id().equals("rime"), "category: it closes the menu and starts the lesson");
        expect(!cat.click(e.tutor, p, 30) && !cat.click(e.tutor, p, -1), "category: a click on an empty slot does nothing");

        // The hub now offers Continue and Stop.
        TutorGui.openHub(e.tutor, p);
        g = gui(p);
        expect(slotWith(g, "continue") == 40 && slotWith(g, "stop") == 44, "hub: Continue and Stop appear while a lesson runs");
        int msgs = p.messages.size();
        expect(g.click(e.tutor, p, 40) && p.opened == null && p.messages.size() > msgs, "hub: Continue closes the menu and shows the step again");
        TutorGui.openHub(e.tutor, p);
        expect(gui(p).click(e.tutor, p, 44) && e.tutor.activeLesson(p) == null && slotWith(gui(p), "stop") == -1, "hub: Stop ends the lesson and refreshes");
        expect(gui(p).click(e.tutor, p, 49) && p.opened == null, "hub: Close closes");

        // Finished lessons are marked.
        e.tutor.markDone(p.getUniqueId(), "menu");
        TutorGui.openCategory(e.tutor, p, "start", 0);
        ItemStack done = p.opened.getItem(slotWith(gui(p), "lesson:menu"));
        expect(done.getItemMeta().getDisplayName().contains("§a") && done.getItemMeta().getLore().stream().anyMatch(s -> s.contains("Finished")), "category: a finished lesson is marked green");
        e.tutor.start(p, "rime");
        TutorGui.openCategory(e.tutor, p, "start", 0);
        expect(p.opened.getItem(slotWith(gui(p), "lesson:rime")).getItemMeta().getLore().stream().anyMatch(s -> s.contains("In progress")), "category: a running lesson says so");
        TutorGui.openHub(e.tutor, p);
        expect(p.opened.getItem(4).getItemMeta().getLore().get(0).contains("1 of 2"), "hub: progress counts finished lessons");

        // A bad category goes back to the hub.
        TutorGui.openCategory(e.tutor, p, "ghost", 0);
        expect(gui(p).category == null, "category: an unknown category shows the hub");
        TutorGui.openCategory(e.tutor, p, "fun", 0);
        expect(gui(p).category == null, "category: one with nothing for this player shows the hub");
        expect(TutorGui.material("NOT_A_THING", Material.BOOK) == Material.BOOK && TutorGui.material(null, Material.BOOK) == Material.BOOK
                && TutorGui.material("air", Material.BOOK) == Material.BOOK && TutorGui.material("compass", Material.BOOK) == Material.COMPASS, "icons: unknown names fall back");
    }

    private static void paging() {
        Kit.MapSection m = new Kit.MapSection();
        m.put("categories.big.title", "Big").put("categories.big.icon", "BOOK").put("categories.big.description", "Lots");
        for (int i = 0; i < 50; i++) {
            String id = "l" + String.format("%02d", i);
            m.put("lessons." + id + ".category", "big").put("lessons." + id + ".title", "Lesson " + i).put("lessons." + id + ".steps", List.of("info :: a", "info :: b"));
        }
        Env e = new Env(m);
        Kit.TestPlayer p = e.player("Alex");
        TutorGui.openCategory(e.tutor, p, "big", 0);
        TutorGui g = gui(p);
        expect(slotWith(g, "page:1") == 50 && slotWith(g, "page:-1") == -1 && g.page == 0, "paging: page 1 offers Next only");
        int shown = 0;
        for (int i = 0; i < TutorGui.PER_PAGE; i++) {
            shown += p.opened.getItem(i) != null ? 1 : 0;
        }
        expect(shown == 45, "paging: a full page holds 45 lessons");
        expect(g.click(e.tutor, p, 50) && gui(p).page == 1, "paging: Next opens page 2");
        g = gui(p);
        shown = 0;
        for (int i = 0; i < TutorGui.PER_PAGE; i++) {
            shown += p.opened.getItem(i) != null ? 1 : 0;
        }
        expect(shown == 5 && slotWith(g, "page:0") == 48 && slotWith(g, "page:2") == -1, "paging: page 2 holds the other five and offers Previous only");
        expect(g.click(e.tutor, p, 48) && gui(p).page == 0, "paging: Previous goes back");
        TutorGui.openCategory(e.tutor, p, "big", 99);
        expect(gui(p).page == 1, "paging: a page past the end is clamped");
        TutorGui.openCategory(e.tutor, p, "big", -3);
        expect(gui(p).page == 0, "paging: a negative page is clamped");
    }

    // ------------------------------------------------------------------ what the server tells us

    private static void listener() {
        Env e = env();
        e.cfg.offerDelaySeconds = 0;
        TutorListener l = new TutorListener(e.plugin, e.cfg, e.tutor);
        Kit.TestPlayer p = e.player("Alex");
        e.tutor.start(p, "rime");
        e.tutor.next(p);

        PlayerCommandPreprocessEvent cmd = new PlayerCommandPreprocessEvent(p, "/rime recipe blade");
        l.onCommand(cmd);
        expect(e.tutor.activeStep(p) == 2 && !cmd.isCancelled(), "listener: a typed command completes its step and is left alone");

        // Our own menu is never "the menu a step asked for".
        e.cfg.bossBar = true;
        TutorGui.openHub(e.tutor, p);
        InventoryView ours = new InventoryView();
        ours.setTopInventory(p.opened);
        ours.setPlayer(p);
        ours.setTitle("Codex");
        l.onOpen(new InventoryOpenEvent(ours));
        expect(e.tutor.activeStep(p) == 2, "listener: opening our own menu does not complete an open step");
        InventoryView other = new InventoryView();
        other.setTopInventory(new Inventory());
        other.setPlayer(p);
        other.setTitle("§6The Nexus Codex");
        l.onOpen(new InventoryOpenEvent(other));
        expect(e.tutor.activeStep(p) == 3, "listener: another menu with the right title does");
        l.onOpen(new InventoryOpenEvent());
        l.onOpen(new InventoryOpenEvent(new InventoryView()));
        expect(e.tutor.activeStep(p) == 3, "listener: an event with no player or inventory is ignored");

        // Clicks in someone else's menu are not ours to touch.
        Inventory chest = new Inventory();
        InventoryClickEvent foreign = click(p, chest, 3);
        l.onClick(foreign);
        expect(!foreign.isCancelled(), "listener: other plugins' menus are left alone");
        InventoryClickEvent noView = new InventoryClickEvent(null, InventoryType.SlotType.CONTAINER, 3, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        l.onClick(noView);
        expect(!noView.isCancelled(), "listener: a click with no view is ignored");

        // Clicks in our menu: cancelled everywhere, acted on only in the top inventory.
        TutorGui.openHub(e.tutor, p);
        Inventory top = p.opened;
        InventoryClickEvent inOurs = click(p, top, 49);
        l.onClick(inOurs);
        expect(inOurs.isCancelled() && p.opened == null, "listener: a click on Close closes");
        TutorGui.openHub(e.tutor, p);
        top = p.opened;
        InventoryView v = new InventoryView();
        v.setTopInventory(top);
        v.setPlayer(p);
        InventoryClickEvent fromBottom = new InventoryClickEvent(v, InventoryType.SlotType.QUICKBAR, 49, ClickType.SHIFT_LEFT, InventoryAction.MOVE_TO_OTHER_INVENTORY);
        l.onClick(fromBottom);
        expect(fromBottom.isCancelled() && p.opened == top, "listener: shift-clicking from the player's inventory is blocked and does nothing");
        InventoryClickEvent outOfRange = click(p, top, 80);
        l.onClick(outOfRange);
        expect(outOfRange.isCancelled() && p.opened == top, "listener: a slot outside the menu does nothing");

        InventoryDragEvent drag = new InventoryDragEvent(v);
        l.onDrag(drag);
        expect(drag.isCancelled(), "listener: dragging items over our menu is blocked");
        InventoryView plain = new InventoryView();
        plain.setTopInventory(new Inventory());
        InventoryDragEvent okDrag = new InventoryDragEvent(plain);
        l.onDrag(okDrag);
        expect(!okDrag.isCancelled() && !new InventoryDragEvent(null).isCancelled(), "listener: other menus can be dragged in");
        l.onDrag(new InventoryDragEvent(null));

        // Joining and leaving.
        Kit.TestPlayer fresh = e.player("Newbie");
        fresh.played = false;
        l.onJoin(new PlayerJoinEvent(fresh, ""));
        expect(fresh.heard("/tutorial"), "listener: a first-time player is invited on join (no delay)");
        Kit.TestPlayer old = e.player("Regular");
        l.onJoin(new PlayerJoinEvent(old, ""));
        expect(old.messages.isEmpty(), "listener: a regular is not");
        e.cfg.offerDelaySeconds = 20;
        Kit.TestPlayer later = e.player("Later");
        later.played = false;
        l.onJoin(new PlayerJoinEvent(later, ""));
        expect(later.messages.isEmpty() && e.tutor.shouldOffer(later), "listener: with a delay the invitation waits");
        e.tutor.start(p, "rime");
        l.onQuit(new PlayerQuitEvent(p, ""));
        expect(e.tutor.barOf(p) == null && e.tutor.activeLesson(p) != null, "listener: leaving drops the bar but remembers the lesson");
    }

    // ------------------------------------------------------------------ /tutorial

    private static void command() {
        Env e = env();
        int[] reloads = {0};
        TutorCommand cmd = new TutorCommand(e.tutor, () -> reloads[0]++);
        Command c = new Command();
        Kit.TestPlayer p = e.player("Alex");
        Kit.TestPlayer admin = e.player("Admin");
        admin.perms.add("nexustutorial.admin");
        admin.perms.add("nexustutorial.staff");
        Console console = new Console();

        cmd.onCommand(p, c, "tutorial", new String[0]);
        expect(p.opened != null, "command: /tutorial opens the menu");
        p.closeInventory();
        cmd.onCommand(console, c, "tutorial", new String[0]);
        expect(console.heard("Players open the tutorial"), "command: the console is told how it works");
        cmd.onCommand(console, c, "tutorial", new String[] {"next"});
        expect(console.heard("Only players"), "command: the console cannot take lessons");

        cmd.onCommand(p, c, "tutorial", new String[] {"start", "rime"});
        expect(e.tutor.activeLesson(p).id().equals("rime"), "command: start <lesson>");
        cmd.onCommand(p, c, "tutorial", new String[] {"start", "RIME"});
        expect(e.tutor.activeLesson(p).id().equals("rime"), "command: lesson names are case-insensitive");
        cmd.onCommand(p, c, "tutorial", new String[] {"start", "ghost"});
        expect(p.heard("no lesson called ghost"), "command: an unknown lesson is explained");
        cmd.onCommand(p, c, "tutorial", new String[] {"start", "secret"});
        expect(p.heard("not available to you"), "command: a lesson the player cannot take is explained");
        p.closeInventory();
        cmd.onCommand(p, c, "tutorial", new String[] {"start"});
        expect(p.opened != null, "command: start with no name opens the menu");

        cmd.onCommand(p, c, "tutorial", new String[] {"next"});
        expect(e.tutor.activeStep(p) == 1, "command: next");
        cmd.onCommand(p, c, "tutorial", new String[] {"skip"});
        expect(e.tutor.activeStep(p) == 2, "command: skip");
        cmd.onCommand(p, c, "tutorial", new String[] {"progress"});
        expect(p.heard("0 of 2 lessons finished") && p.heard("The Long Winter") && p.heard("step 3 of 5"), "command: progress shows the lesson in progress");
        cmd.onCommand(p, c, "tutorial", new String[] {"list"});
        expect(p.heard("Getting Started") && p.heard("rime") && p.heard("menu") && !p.heard("secret") && !p.heard("Fun"), "command: list shows ids by category, hiding what the player cannot take");
        cmd.onCommand(p, c, "tutorial", new String[] {"stop"});
        expect(e.tutor.activeLesson(p) == null && p.heard("Lesson put down"), "command: stop");
        cmd.onCommand(p, c, "tutorial", new String[] {"stop"});
        expect(p.heard("not in a lesson"), "command: stop with nothing running says so");
        cmd.onCommand(p, c, "tutorial", new String[] {"next"});
        cmd.onCommand(p, c, "tutorial", new String[] {"skip"});
        expect(p.messages.stream().filter(s -> s.contains("not in a lesson")).count() >= 3, "command: next and skip with nothing running say so");
        cmd.onCommand(p, c, "tutorial", new String[] {"nonsense"});
        expect(p.lastMessage().contains("start <lesson>") && !p.lastMessage().contains("reload"), "command: help hides admin commands from players");
        cmd.onCommand(admin, c, "tutorial", new String[] {"nonsense"});
        expect(admin.lastMessage().contains("reload") && admin.lastMessage().contains("reset"), "command: and shows them to admins");

        for (String sub : new String[] {"reload", "check", "reset", "complete"}) {
            cmd.onCommand(p, c, "tutorial", new String[] {sub, "Alex", "menu"});
        }
        expect(reloads[0] == 0 && p.messages.stream().filter(s -> s.contains("permission")).count() == 4, "command: players cannot use any admin command");
        cmd.onCommand(admin, c, "tutorial", new String[] {"reload"});
        expect(reloads[0] == 1 && admin.heard("reloaded"), "command: reload runs the hook");
        cmd.onCommand(admin, c, "tutorial", new String[] {"check"});
        expect(admin.heard("3 lessons loaded"), "command: check prints the report");
        cmd.onCommand(admin, c, "tutorial", new String[] {"complete", "Alex", "menu"});
        expect(e.tutor.stateOf(p, e.book.lesson("menu")) == Tutor.LessonState.DONE && admin.heard("Marked menu as finished for Alex"), "command: complete");
        cmd.onCommand(admin, c, "tutorial", new String[] {"complete", "Alex", "ghost"});
        expect(admin.heard("no lesson called ghost"), "command: complete with an unknown lesson");
        cmd.onCommand(admin, c, "tutorial", new String[] {"complete", "Nobody", "menu"});
        cmd.onCommand(admin, c, "tutorial", new String[] {"complete", "Alex"});
        expect(admin.heard("No player named Nobody") && admin.heard("/tutorial complete <player> <lesson>"), "command: complete needs a real player and a lesson");
        cmd.onCommand(admin, c, "tutorial", new String[] {"reset", "Alex", "menu"});
        expect(e.tutor.stateOf(p, e.book.lesson("menu")) == Tutor.LessonState.NEW && admin.heard("Reset Alex's tutorial progress for menu"), "command: reset one lesson");
        cmd.onCommand(admin, c, "tutorial", new String[] {"reset", "Alex", "menu"});
        expect(admin.heard("Nothing to reset"), "command: resetting nothing says so");
        cmd.onCommand(admin, c, "tutorial", new String[] {"reset", "Alex"});
        cmd.onCommand(admin, c, "tutorial", new String[] {"reset"});
        cmd.onCommand(admin, c, "tutorial", new String[] {"reset", "Nobody"});
        expect(admin.heard("/tutorial reset <player> [lesson]") && admin.heard("No player named Nobody"), "command: reset needs a real player");
        console.admin = true;
        cmd.onCommand(console, c, "tutorial", new String[] {"check"});
        expect(console.heard("lessons loaded"), "command: the console can run admin commands when it has the permission");

        // Tab completion
        expect(cmd.onTabComplete(p, c, "tutorial", new String[] {""}).equals(List.of("start", "next", "skip", "stop", "list", "progress")), "tab: players see their commands");
        expect(cmd.onTabComplete(admin, c, "tutorial", new String[] {"re"}).equals(List.of("reload", "reset")), "tab: admins see theirs, filtered by prefix");
        expect(cmd.onTabComplete(p, c, "tutorial", new String[] {"start", ""}).equals(List.of("rime", "menu")) || cmd.onTabComplete(p, c, "tutorial", new String[] {"start", ""}).size() == 2,
                "tab: start completes the lessons the player can take");
        expect(cmd.onTabComplete(p, c, "tutorial", new String[] {"start", "se"}).isEmpty(), "tab: and never a hidden one");
        expect(cmd.onTabComplete(admin, c, "tutorial", new String[] {"reset", "al"}).equals(List.of("Alex")), "tab: reset completes player names");
        expect(cmd.onTabComplete(admin, c, "tutorial", new String[] {"complete", "Alex", "m"}).equals(List.of("menu")), "tab: and lesson ids");
        expect(cmd.onTabComplete(p, c, "tutorial", new String[] {"reset", "al"}).isEmpty() && cmd.onTabComplete(console, c, "tutorial", new String[] {"start", ""}).isEmpty(),
                "tab: players get no admin completions, the console no lessons");
        expect(cmd.onTabComplete(p, c, "tutorial", new String[] {"next", "x"}).isEmpty(), "tab: next takes no argument");
    }

    // ------------------------------------------------------------------ remembering

    private static void store() {
        java.io.File dir = new java.io.File(System.getProperty("java.io.tmpdir"), "tut-test-" + System.nanoTime());
        List<String> warnings = new ArrayList<>();
        ProgressStore s = new ProgressStore(new java.io.File(dir, "players.yml"), warnings::add);
        s.load();
        expect(!s.dirty(), "store: loading nothing is not a change");
        java.util.UUID id = java.util.UUID.randomUUID();
        expect(s.peek(id) == null, "store: peek never creates");
        s.of(id).done.add("menu");
        expect(s.peek(id) != null, "store: of creates");
        s.flush();
        expect(!s.dirty(), "store: flushing with nothing changed does nothing");
        expect(!dir.exists(), "store: and touches no files");
        s.markDirty();
        expect(s.dirty(), "store: marking dirty");
        s.flush();
        expect(!s.dirty() && dir.isDirectory(), "store: a flush writes and clears the flag");
        s.forget(id);
        expect(s.dirty() && s.peek(id) == null, "store: forgetting is a change");
        s.forget(id);
        s.flush();
        ProgressStore none = new ProgressStore(null, warnings::add);
        none.markDirty();
        none.flush();
        none.load();
        expect(warnings.isEmpty(), "store: no file, no complaints");
        dir.delete();
        new java.io.File(dir, "players.yml").delete();
        dir.delete();
        expect(Material.COMPASS != null, "store: done");
    }
}
