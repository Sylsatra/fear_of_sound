package com.example.fear_of_sound.config;

import com.example.fear_of_sound.FearofSoundMod;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class ConfigAutoUpdater {
    private static final String FILE_NAME = "fear_of_sound-common.toml";

    public static void migrateIfNeeded() {
        try {
            Path configPath = FMLPaths.CONFIGDIR.get().resolve(FILE_NAME);
            if (!Files.exists(configPath)) return;


            Path backupPath = configPath.resolveSibling(FILE_NAME + ".bak");
            try {
                Files.copy(configPath, backupPath, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignored) {
            }

            CommentedFileConfig cfg = CommentedFileConfig.builder(configPath).sync().autosave().build();
            cfg.load();

            boolean changed = false;


            if (!cfg.contains("Line-of-sight fear parameters.losRange")) {
                cfg.set("Line-of-sight fear parameters.losRange", 32);
                cfg.setComment("Line-of-sight fear parameters.losRange", "Fallback LOS radius (in blocks) used when Sound Attract does not provide a detection range.");
                changed = true;
            }


            String perf = "Performance";
            if (!cfg.contains(perf + ".maxSoundsTracked")) {
                cfg.set(perf + ".maxSoundsTracked", 512);
                cfg.setComment(perf + ".maxSoundsTracked", "Maximum number of sounds to keep in memory when Sound Attract is absent. Older/weaker sounds are evicted when exceeded.");
                changed = true;
            }
            if (!cfg.contains(perf + ".raycastCacheTtlTicks")) {
                cfg.set(perf + ".raycastCacheTtlTicks", 40);
                cfg.setComment(perf + ".raycastCacheTtlTicks", "How long (ticks) to keep block muffling (raycast) results cached.");
                changed = true;
            }
            if (!cfg.contains(perf + ".raycastCacheMaxEntries")) {
                cfg.set(perf + ".raycastCacheMaxEntries", 4096);
                cfg.setComment(perf + ".raycastCacheMaxEntries", "Maximum LRU size for block muffling cache entries across all dimensions.");
                changed = true;
            }
            if (!cfg.contains(perf + ".spatialGridSize")) {
                cfg.set(perf + ".spatialGridSize", 32);
                cfg.setComment(perf + ".spatialGridSize", "Cell size in blocks for the simple spatial index used when Sound Attract is absent.");
                changed = true;
            }
            if (!cfg.contains(perf + ".mergeRadius")) {
                cfg.set(perf + ".mergeRadius", 3);
                cfg.setComment(perf + ".mergeRadius", "Radius in blocks within which identical sounds are de-duplicated by refreshing lifetime instead of adding a new record.");
                changed = true;
            }


            String behavior = "Sound Behavior";
            if (!cfg.contains(behavior + ".panicMinTicks")) {
                cfg.set(behavior + ".panicMinTicks", 0);
                cfg.setComment(behavior + ".panicMinTicks", "Initial panic delay minimum (ticks) before a mob starts fleeing. Set 0 for immediate flee.");
                changed = true;
            }
            if (!cfg.contains(behavior + ".panicMaxTicks")) {
                cfg.set(behavior + ".panicMaxTicks", 0);
                cfg.setComment(behavior + ".panicMaxTicks", "Initial panic delay maximum (ticks). If max > min, a random value in [min, max) is used.");
                changed = true;
            }

            if (changed) {
                cfg.save();
                FearofSoundMod.LOGGER.info("[FearOfSound] Auto-updated config with new keys. A backup was saved as {}", backupPath.getFileName());
            }

            cfg.close();
        } catch (Throwable t) {
            FearofSoundMod.LOGGER.error("[FearOfSound] Failed to auto-update config.", t);
        }
    }
}
