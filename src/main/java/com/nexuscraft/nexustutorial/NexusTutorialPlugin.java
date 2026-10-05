package com.nexuscraft.nexustutorial;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public final class NexusTutorialPlugin extends JavaPlugin {

    private final TutorConfig cfg = new TutorConfig();
    private final LessonBook book = new LessonBook();
    private ProgressStore store;
    private Tutor tutor;
    private BukkitTask tickTask;
    private BukkitTask saveTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResource("custom-lessons.yml", false);
        cfg.load(getConfig());
        loadLessons();
        store = new ProgressStore(new File(getDataFolder(), "players.yml"), getLogger()::warning);
        store.load();
        tutor = new Tutor(cfg, book, store,
                name -> Bukkit.getPluginManager().isPluginEnabled(name),
                NexusTutorialPlugin::canonicalCommand,
                command -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command));
        getServer().getPluginManager().registerEvents(new TutorListener(this, cfg, tutor), this);
        TutorCommand command = new TutorCommand(tutor, this::reload);
        getCommand("tutorial").setExecutor(command);
        getCommand("tutorial").setTabCompleter(command);
        tickTask = getServer().getScheduler().runTaskTimer(this, tutor::tick, 20L, 20L);
        saveTask = getServer().getScheduler().runTaskTimer(this, store::flush, 600L, 600L);
        getLogger().info("NexusTutorial enabled -- " + book.lessons().size() + " lessons. Run /tutorial check to see what is usable on this server.");
        for (String w : book.warnings()) {
            getLogger().warning(w);
        }
    }

    @Override
    public void onDisable() {
        if (tickTask != null) {
            tickTask.cancel();
        }
        if (saveTask != null) {
            saveTask.cancel();
        }
        if (tutor != null) {
            tutor.shutdown();
        }
    }

    private void reload() {
        reloadConfig();
        cfg.reset();
        cfg.load(getConfig());
        loadLessons();
        for (String w : book.warnings()) {
            getLogger().warning(w);
        }
    }

    /** The lessons that ship in the jar, then the server's own custom-lessons.yml on top. */
    private void loadLessons() {
        book.clear();
        try (InputStream in = getResource("lessons.yml")) {
            if (in != null) {
                book.load(YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8)), "lessons.yml");
            }
        } catch (java.io.IOException ex) {
            getLogger().warning("Could not read the built-in lessons: " + ex.getMessage());
        }
        File custom = new File(getDataFolder(), "custom-lessons.yml");
        if (custom.exists()) {
            book.load(YamlConfiguration.loadConfiguration(custom), "custom-lessons.yml");
        }
    }

    /** A command label's one true name, or null if the server has no such command. */
    static String canonicalCommand(String label) {
        Command c = Bukkit.getCommandMap().getCommand(label);
        return c == null ? null : c.getName().toLowerCase(Locale.ROOT);
    }
}
