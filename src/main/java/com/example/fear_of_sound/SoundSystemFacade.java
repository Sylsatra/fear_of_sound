package com.example.fear_of_sound;

import com.example.fear_of_sound.config.AdvancedFearOverrideRegistry;
import com.example.fear_of_sound.integration.SoundAttractIntegration;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Optional;
import java.util.UUID;

public class SoundSystemFacade {
    public static void broadcastClientSound(ResourceLocation soundId, double x, double y, double z,
                                            ResourceLocation dimension, Optional<UUID> sourcePlayerUUID,
                                            int range, double weight, String playerAction) {
        if (FearofSoundMod.isSoundAttractLoaded) {
            if (SoundAttractIntegration.sendSound(
                    soundId, x, y, z, dimension, sourcePlayerUUID, range, weight, playerAction)) return;
        }
        SoundMessage msg = new SoundMessage(soundId, x, y, z, dimension, sourcePlayerUUID, range, weight);
        SoundNetwork.INSTANCE.sendToServer(msg);
    }


    public static void broadcastServerSound(SoundEvent se, double x, double y, double z, Level level) {
        if (FearofSoundMod.isSoundAttractLoaded) {
            String dimKey = level.dimension().location().toString();
            BlockPos pos = BlockPos.containing(x, y, z);
            int lifetime = com.example.fear_of_sound.config.FearofSoundConfig.COMMON.soundLifetimeTicks.get();
            com.example.fear_of_sound.config.FearofSoundConfig.SoundConfig cfg = com.example.fear_of_sound.config.FearofSoundConfig.SOUND_CONFIGS_CACHE.get(se);
            boolean added = cfg != null
                    ? SoundAttractIntegration.addSoundDirect(se, pos, dimKey, cfg.range, cfg.weight, lifetime)
                    : SoundAttractIntegration.addSoundDirect(se, pos, dimKey, 16.0, 1.0, lifetime);
            if (!added) {
                SoundTracker.addSound(se, pos, dimKey);
            }
        } else {
            SoundTracker.addSound(se, BlockPos.containing(x, y, z), level.dimension().location().toString());
        }
    }

    public static SoundTracker.SoundRecord findNearestSoundFor(Mob mob) {
        if (FearofSoundMod.isSoundAttractLoaded) {
            SoundTracker.SoundRecord fromAttract = SoundAttractIntegration.findNearestSound(mob);
            if (fromAttract != null) {
                return fromAttract;
            }
        }

        SoundTracker.SoundRecord rec = SoundTracker.findNearestSound(mob.level(), mob.blockPosition());
        if (rec == null) return null;
        ResourceLocation sid = rec.sound != null ? ForgeRegistries.SOUND_EVENTS.getKey(rec.sound) : null;
        if (!AdvancedFearOverrideRegistry.shouldFear(mob, sid)) return null;
        return rec;
    }
}
