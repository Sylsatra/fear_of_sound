package com.example.fear_of_sound.integration;

import com.example.fear_of_sound.FearofSoundMod;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

public final class SoundAttractLOSIntegration {
    private static volatile Method canDetectLiving;
    private static volatile Method smartLineOfSight;
    private static volatile boolean resolved;
    private static final AtomicBoolean WARNED = new AtomicBoolean();

    private SoundAttractLOSIntegration() {
    }

    public static boolean canDetect(Mob mob, LivingEntity target) {
        if (mob == null || target == null || !mob.isAlive() || !target.isAlive() || mob.level() != target.level()) return false;
        if (!mob.hasLineOfSight(target)) return false;
        if (!FearofSoundMod.isSoundAttractLoaded || !ModList.get().isLoaded("soundattract")) return true;
        resolve();
        if (canDetectLiving == null) return true;
        try {
            if (!Boolean.TRUE.equals(canDetectLiving.invoke(null, mob, target))) return false;
            return smartLineOfSight == null || Boolean.TRUE.equals(smartLineOfSight.invoke(null, mob, target));
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            warnOnce(e);
            return mob.hasLineOfSight(target);
        }
    }

    private static void resolve() {
        if (resolved) return;
        synchronized (SoundAttractLOSIntegration.class) {
            if (resolved) return;
            try {
                Class<?> stealth = Class.forName("com.example.soundattract.event.StealthDetectionEvents", false,
                        SoundAttractLOSIntegration.class.getClassLoader());
                canDetectLiving = stealth.getMethod("canMobDetectLivingEntity", Mob.class, LivingEntity.class);
                try {
                    Class<?> fov = Class.forName("com.example.soundattract.event.FovEvents", false,
                            SoundAttractLOSIntegration.class.getClassLoader());
                    smartLineOfSight = fov.getMethod("hasSmartLineOfSight", Mob.class, net.minecraft.world.entity.Entity.class);
                } catch (ReflectiveOperationException | LinkageError ignored) {
                    smartLineOfSight = null;
                }
            } catch (ReflectiveOperationException | LinkageError e) {
                warnOnce(e);
            } finally {
                resolved = true;
            }
        }
    }

    private static void warnOnce(Throwable error) {
        if (WARNED.compareAndSet(false, true)) {
            FearofSoundMod.LOGGER.warn("Sound Attract sight adapter unavailable; falling back to vanilla line of sight: {}", error.toString());
        }
    }
}
