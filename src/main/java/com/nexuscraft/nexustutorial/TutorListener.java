package com.nexuscraft.nexustutorial;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.Plugin;

/** Listens for the things a player does that can complete a step, and for clicks in our menus. */
final class TutorListener implements Listener {

    private final Plugin plugin;
    private final TutorConfig cfg;
    private final Tutor tutor;

    TutorListener(Plugin plugin, TutorConfig cfg, Tutor tutor) {
        this.plugin = plugin;
        this.cfg = cfg;
        this.tutor = tutor;
    }

    /** Only looks, never changes: a command that fails still counts as having been tried. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        tutor.onCommand(event.getPlayer(), event.getMessage());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player p) || event.getInventory() == null) {
            return;
        }
        if (event.getInventory().getHolder() instanceof TutorGui) {
            return; // our own menu is never "the menu a step asked for"
        }
        tutor.onOpen(p, PlainTextComponentSerializer.plainText().serialize(event.getView().title()));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView() == null ? null : event.getView().getTopInventory();
        if (top == null || !(top.getHolder() instanceof TutorGui gui)) {
            return;
        }
        event.setCancelled(true); // nothing is ever taken from, or put into, our menus
        if (!(event.getWhoClicked() instanceof Player p)) {
            return;
        }
        if (event.getClickedInventory() == top && event.getRawSlot() >= 0 && event.getRawSlot() < TutorGui.SIZE) {
            gui.click(tutor, p, event.getRawSlot());
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent event) {
        Inventory top = event.getView() == null ? null : event.getView().getTopInventory();
        if (top != null && top.getHolder() instanceof TutorGui) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        if (!tutor.shouldOffer(p)) {
            return;
        }
        long ticks = cfg.offerDelaySeconds * 20L;
        if (ticks <= 0) {
            tutor.offer(p);
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline()) {
                tutor.offer(p);
            }
        }, ticks);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        tutor.quit(event.getPlayer());
    }
}
