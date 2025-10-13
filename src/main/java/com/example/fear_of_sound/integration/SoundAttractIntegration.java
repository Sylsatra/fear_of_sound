package com.example.fear_of_sound.integration;

import com.example.fear_of_sound.SoundTracker.SoundRecord;
import com.example.fear_of_sound.config.AdvancedFearOverrideRegistry;
import com.example.fear_of_sound.config.FearofSoundConfig;
import com.example.fear_of_sound.config.FearofSoundConfig.SoundConfig;
import com.example.soundattract.SoundAttractNetwork;
import com.example.soundattract.SoundMessage;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Optional;
import java.util.UUID;

public class SoundAttractIntegration {
    public static void sendSound(ResourceLocation soundId, double x, double y, double z, ResourceLocation dimension, Optional<UUID> sourcePlayerUUID, int range, double weight) {
        if (SoundAttractNetwork.INSTANCE == null) {
            return;
        }
        SoundMessage msg = new SoundMessage(
                soundId, x, y, z, dimension, sourcePlayerUUID, range, weight
        );
        SoundAttractNetwork.INSTANCE.sendToServer(msg);
    }

    public static SoundRecord findNearestSound(Mob mob) {
        if (com.example.soundattract.SoundTracker.class == null) {
            return null;
        }

        Level level = mob.level();
        BlockPos mobPos = mob.blockPosition();
        Vec3 mobEyePos = mob.getEyePosition();

        com.example.soundattract.SoundTracker.SoundRecord attractSound =
                com.example.soundattract.SoundTracker.findNearestSound(mob, level, mobPos, mobEyePos);

        if (attractSound == null) {
            return null;
        }

        double finalRange = attractSound.range;
        double finalWeight = attractSound.weight;

        if (finalRange <= 0 || finalWeight <= 0) {
            SoundEvent soundEventKey = attractSound.sound;
            if (soundEventKey != null && FearofSoundConfig.SOUND_CONFIGS_CACHE.containsKey(soundEventKey)) {
                SoundConfig fearConfig = FearofSoundConfig.SOUND_CONFIGS_CACHE.get(soundEventKey);
                if (fearConfig != null) {
                    finalRange = fearConfig.range;
                    finalWeight = fearConfig.weight;
                }
            }
        }

        if (finalWeight <= 0) {
            return null;
        }

        ResourceLocation sid = attractSound.sound != null ? ForgeRegistries.SOUND_EVENTS.getKey(attractSound.sound) : null;
        if (!AdvancedFearOverrideRegistry.shouldFear(mob, sid)) {
            return null;
        }
        return new SoundRecord(
                attractSound.sound,
                attractSound.pos,
                attractSound.ticksRemaining,
                attractSound.dimensionKey,
                finalRange,
                finalWeight
        );
    }
}