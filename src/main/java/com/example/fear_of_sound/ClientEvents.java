package com.example.fear_of_sound;

import com.example.fear_of_sound.config.FearofSoundConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Optional;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = FearofSoundMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class ClientEvents {

    private static final ThreadLocal<Boolean> IS_HANDLING_SOUND = ThreadLocal.withInitial(() -> false);

    private enum PlayerAction {
        IDLE, CRAWLING, SNEAKING, WALKING, SPRINTING, SPRINT_JUMPING
    }


    private static final double IDLE_THRESHOLD_SQ = 0.02 * 0.02;
    private static final double CRAWLING_THRESHOLD_SQ = 0.06 * 0.06;
    private static final double SNEAKING_SPEED_SQ = 0.12 * 0.12;
    private static final double WALKING_SPEED_SQ = 0.28 * 0.28;

    @SubscribeEvent
    public static void onPlaySoundEvent(PlaySoundEvent event) {
        if (IS_HANDLING_SOUND.get()) {
            return;
        }

        try {
            IS_HANDLING_SOUND.set(true);

            if (event.getSound() == null || !FearofSoundConfig.SOUND_BASED_DETECTION_ENABLED_CACHE) {
                return;
            }

            Level clientWorld = Minecraft.getInstance().level;
            Player clientPlayer = Minecraft.getInstance().player;
            if (clientWorld == null || clientPlayer == null) {
                return;
            }

            if (event.getSound() instanceof AbstractSoundInstance soundInstance) {
                ResourceLocation soundRL = soundInstance.getLocation();
                if (soundRL == null) {
                    return;
                }

                SoundEvent se = ForgeRegistries.SOUND_EVENTS.getValue(soundRL);
                if (se == null) {
                    return;
                }

                double x = soundInstance.getX();
                double y = soundInstance.getY();
                double z = soundInstance.getZ();
                ResourceLocation dim = clientWorld.dimension().location();
                Optional<UUID> sourcePlayerUUID = Optional.empty();
                int calculatedRange = -1;
                double calculatedWeight = 1.0;

                if (FearofSoundConfig.PLAYER_STEP_SOUNDS_CACHE.contains(se) &&
                    clientPlayer.position().distanceToSqr(x, y, z) < 1.5 * 1.5) {

                    sourcePlayerUUID = Optional.of(clientPlayer.getUUID());
                    Vec3 motion = clientPlayer.getDeltaMovement();
                    double horizontalSpeedSq = motion.x * motion.x + motion.z * motion.z;
                    boolean isOnGround = clientPlayer.onGround();
                    boolean isSneaking = clientPlayer.isShiftKeyDown();
                    boolean isSprinting = clientPlayer.isSprinting();

                    PlayerAction currentAction = PlayerAction.IDLE;

                    if (isSneaking) {
                        if (horizontalSpeedSq > IDLE_THRESHOLD_SQ && horizontalSpeedSq <= CRAWLING_THRESHOLD_SQ) {
                            currentAction = PlayerAction.CRAWLING;
                        } else if (horizontalSpeedSq > CRAWLING_THRESHOLD_SQ && horizontalSpeedSq <= SNEAKING_SPEED_SQ * 1.1) {
                            currentAction = PlayerAction.SNEAKING;
                        }
                    } else {
                        if (isSprinting && !isOnGround) {
                            currentAction = PlayerAction.SPRINT_JUMPING;
                        } else if (isSprinting && horizontalSpeedSq > WALKING_SPEED_SQ) {
                            currentAction = PlayerAction.SPRINTING;
                        } else if (isOnGround && horizontalSpeedSq > IDLE_THRESHOLD_SQ) {
                            currentAction = PlayerAction.WALKING;
                        }
                    }

                    switch (currentAction) {
                        case CRAWLING:        calculatedRange = 3;  calculatedWeight = 1.0; break;
                        case SNEAKING:        calculatedRange = 5;  calculatedWeight = 1.0; break;
                        case WALKING:         calculatedRange = 10; calculatedWeight = 1.0; break;
                        case SPRINTING:       calculatedRange = 16; calculatedWeight = 1.0; break;
                        case SPRINT_JUMPING:  calculatedRange = 20; calculatedWeight = 1.0; break;
                        case IDLE: default: return;
                    }
                }

                SoundSystemFacade.broadcastClientSound(soundRL, x, y, z, dim, sourcePlayerUUID, calculatedRange, calculatedWeight);
            }
        } finally {
            IS_HANDLING_SOUND.remove();
        }
    }
}