package dev.handyslots.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class HandySlotsConfig {
    public static final int MAX_BINDS = 5;
    public static final int MIN_DELAY = 40;
    public static final int MAX_DELAY = 120;

    private static final Logger LOGGER = LoggerFactory.getLogger("handyslots");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static HandySlotsConfig instance;

    public int delayMs = 70;
    public List<BindConfig> binds = new ArrayList<>();

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("handyslots.json");
    }

    public static HandySlotsConfig get() {
        if (instance == null) load();
        return instance;
    }

    public static void load() {
        HandySlotsConfig loaded = null;
        Path p = path();
        if (Files.exists(p)) {
            try (Reader r = Files.newBufferedReader(p)) {
                loaded = GSON.fromJson(r, HandySlotsConfig.class);
            } catch (Exception e) {
                LOGGER.error("Failed to read handyslots.json, using defaults", e);
            }
        }
        instance = loaded != null ? loaded : new HandySlotsConfig();
        instance.normalize();
        save();
    }

    public static void save() {
        if (instance == null) return;
        instance.normalize();
        try {
            Files.createDirectories(path().getParent());
            try (Writer w = Files.newBufferedWriter(path())) {
                GSON.toJson(instance, w);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to save handyslots.json", e);
        }
    }

    private void normalize() {
        if (binds == null) binds = new ArrayList<>();
        for (int i = 0; i < binds.size(); i++) {
            if (binds.get(i) == null) binds.set(i, new BindConfig());
        }
        while (binds.size() < MAX_BINDS) binds.add(new BindConfig());
        while (binds.size() > MAX_BINDS) binds.remove(binds.size() - 1);

        delayMs = Math.max(MIN_DELAY, Math.min(MAX_DELAY, delayMs));

        for (BindConfig b : binds) {
            if (b.key == null) b.key = "key.keyboard.unknown";
            if (b.mode == null) b.mode = BindConfig.Mode.STRICT;
            if (b.sampleItem == null) b.sampleItem = "";
            if (b.sampleName == null) b.sampleName = "";
            if (b.hotbarSlot < 0 || b.hotbarSlot > 8) b.hotbarSlot = 0;
            if (b.sourceSlot < 9 || b.sourceSlot > 35) b.sourceSlot = -1;
        }
    }
}
