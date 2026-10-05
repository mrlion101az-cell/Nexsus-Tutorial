package com.nexuscraft.nexustutorial;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/** Progress for every player, kept in memory and written to players.yml when something changed. */
final class ProgressStore {

    private final File file;
    private final Consumer<String> warn;
    private final Map<UUID, ProgressData> all = new HashMap<>();
    private boolean dirty;

    ProgressStore(File file, Consumer<String> warn) {
        this.file = file;
        this.warn = warn;
    }

    void load() {
        all.clear();
        dirty = false;
        if (file == null || !file.exists()) {
            return;
        }
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection players = yml.getConfigurationSection("players");
        if (players == null) {
            return;
        }
        for (String key : players.getKeys(false)) {
            try {
                all.put(UUID.fromString(key), ProgressData.read(players, key));
            } catch (IllegalArgumentException bad) {
                warn.accept("players.yml: '" + key + "' is not a player id -- skipped.");
            }
        }
    }

    ProgressData of(UUID id) {
        return all.computeIfAbsent(id, k -> new ProgressData());
    }

    /** The record if one exists, without creating it. */
    ProgressData peek(UUID id) {
        return all.get(id);
    }

    void markDirty() {
        dirty = true;
    }

    boolean dirty() {
        return dirty;
    }

    void forget(UUID id) {
        if (all.remove(id) != null) {
            dirty = true;
        }
    }

    /** Writes players.yml if anything changed since the last write. */
    void flush() {
        if (!dirty || file == null) {
            return;
        }
        Map<String, Object> flat = new LinkedHashMap<>();
        for (Map.Entry<UUID, ProgressData> e : all.entrySet()) {
            if (!e.getValue().isBlank()) {
                e.getValue().writeTo(flat, "players." + e.getKey());
            }
        }
        YamlConfiguration yml = new YamlConfiguration();
        for (Map.Entry<String, Object> e : flat.entrySet()) {
            yml.set(e.getKey(), e.getValue());
        }
        try {
            File dir = file.getParentFile();
            if (dir != null) {
                dir.mkdirs();
            }
            yml.save(file);
            dirty = false;
        } catch (IOException ex) {
            warn.accept("Could not save players.yml: " + ex.getMessage());
        }
    }
}
