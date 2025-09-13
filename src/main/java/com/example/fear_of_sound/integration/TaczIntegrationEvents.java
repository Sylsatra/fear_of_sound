package com.example.fear_of_sound.integration;

import com.example.fear_of_sound.SoundTracker;
import com.example.fear_of_sound.config.FearofSoundConfig;
import com.tacz.guns.api.event.common.GunReloadEvent;
import com.tacz.guns.api.event.common.GunShootEvent;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

public class TaczIntegrationEvents {

    private static final boolean IS_TACZ_LOADED = ModList.get().isLoaded("tacz");
    private static final ResourceLocation TACZ_GUN_SOUND_ID = ResourceLocation.fromNamespaceAndPath("tacz", "gun");

    private static final SoundEvent TACZ_GUN_SOUND = ForgeRegistries.SOUND_EVENTS.getValue(TACZ_GUN_SOUND_ID);

    @SubscribeEvent
    public static void onGunReload(GunReloadEvent event) {
        if (!IS_TACZ_LOADED || !FearofSoundConfig.TACZ_ENABLED_CACHE) return;

        LivingEntity entity = event.getEntity();
        if (entity instanceof Player player && !player.level().isClientSide()) {
            if (TACZ_GUN_SOUND == null) return;

            Level level = player.level();
            BlockPos pos = player.blockPosition();
            String dimensionKey = level.dimension().location().toString();
            int lifetime = FearofSoundConfig.COMMON.soundLifetimeTicks.get();

            double[] rw = calculateTaczReloadRangeWeight(player);
            double range = rw[0];
            double weight = rw[1];

            SoundTracker.addSound(
                    TACZ_GUN_SOUND,
                    pos,
                    dimensionKey,
                    range,
                    weight,
                    lifetime
            );
        }
    }

    @SubscribeEvent
    public static void onGunShoot(GunShootEvent event) {
        if (!IS_TACZ_LOADED || !FearofSoundConfig.TACZ_ENABLED_CACHE) return;

        LivingEntity entity = event.getShooter();
        if (entity instanceof Player player && !player.level().isClientSide()) {
            SoundEvent taczGunSound = ForgeRegistries.SOUND_EVENTS.getValue(TACZ_GUN_SOUND_ID);

            if (taczGunSound == null) return;

            Level level = player.level();
            BlockPos pos = player.blockPosition();
            String dimensionKey = level.dimension().location().toString();
            int lifetime = FearofSoundConfig.COMMON.soundLifetimeTicks.get();

            double[] rw = calculateTaczShootRangeWeight(player);
            double range = rw[0];
            double weight = rw[1];

            SoundTracker.addSound(
                    taczGunSound,
                    pos,
                    dimensionKey,
                    range,
                    weight,
                    lifetime
            );
        }
    }

    private static double[] calculateTaczReloadRangeWeight(Player player) {
        ItemStack gunStack = player.getMainHandItem();
        IGun iGun = IGun.getIGunOrNull(gunStack);
        if (iGun != null) {
            ResourceLocation gunId = iGun.getGunId(gunStack);
            Double shootDb = FearofSoundConfig.TACZ_GUN_SHOOT_DB_CACHE.get(gunId);
            if (shootDb != null) {
                double reloadRange = shootDb / 20.0;
                double reloadWeight = (shootDb / 10.0) / 2.0;
                return new double[]{reloadRange, reloadWeight};
            }
        }
        return new double[]{FearofSoundConfig.TACZ_RELOAD_RANGE_CACHE, FearofSoundConfig.TACZ_RELOAD_WEIGHT_CACHE};
    }

    private static double[] calculateTaczShootRangeWeight(Player player) {
        ItemStack gunStack = player.getMainHandItem();
        IGun iGun = IGun.getIGunOrNull(gunStack);
        if (iGun != null) {
            ResourceLocation gunId = iGun.getGunId(gunStack);
            Double db = FearofSoundConfig.TACZ_GUN_SHOOT_DB_CACHE.get(gunId);

            if (db != null) {
                ResourceLocation attId = iGun.getAttachmentId(gunStack, AttachmentType.MUZZLE);
                Double reduction = FearofSoundConfig.TACZ_ATTACHMENT_REDUCTION_DB_CACHE.get(attId);

                if (reduction != null) {
                    db = Math.max(0, db - reduction);
                }
                return new double[]{db, db / 10.0};
            }
        }
        return new double[]{FearofSoundConfig.TACZ_SHOOT_RANGE_CACHE, FearofSoundConfig.TACZ_SHOOT_WEIGHT_CACHE};
    }
}